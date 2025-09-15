package com.company.connector.service;

import com.company.connector.config.SyncConfig;
import com.company.connector.model.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ScheduledSyncService.
 * 
 * Tests the scheduled synchronization functionality, health checks,
 * and sync status tracking.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledSyncServiceTest {
    
    @Mock
    private IssueService issueService;
    
    private SyncConfig syncConfig;
    private ScheduledSyncService scheduledSyncService;
    
    @BeforeEach
    void setUp() {
        syncConfig = new SyncConfig(5, 10, "0 */15 * * * *");
        scheduledSyncService = new ScheduledSyncService(issueService, syncConfig);
    }
    
    @Test
    void performScheduledSync_WithSuccessfulSync_UpdatesStatusCorrectly() {
        // Given
        SyncResult.Success successResult = new SyncResult.Success(3, Duration.ofSeconds(5));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(successResult));
        
        // When
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Then
        verify(issueService).syncIssues("octocat", "Hello-World", 5);
        
        ScheduledSyncService.SyncStatus status = scheduledSyncService.getCurrentSyncStatus();
        assertThat(status.state()).isEqualTo(ScheduledSyncService.SyncState.SUCCESS);
        assertThat(status.message()).contains("3 issues processed");
        assertThat(status.startTime()).isNotNull();
        assertThat(status.endTime()).isNotNull();
    }
    
    @Test
    void performScheduledSync_WithPartialFailure_UpdatesStatusCorrectly() {
        // Given
        SyncResult.PartialFailure partialFailure = new SyncResult.PartialFailure(
            2, 1, List.of("Error processing issue 123"), Duration.ofSeconds(3)
        );
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(partialFailure));
        
        // When
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Then
        ScheduledSyncService.SyncStatus status = scheduledSyncService.getCurrentSyncStatus();
        assertThat(status.state()).isEqualTo(ScheduledSyncService.SyncState.PARTIAL_FAILURE);
        assertThat(status.message()).contains("2 successful, 1 failed");
    }
    
    @Test
    void performScheduledSync_WithFailure_UpdatesStatusCorrectly() {
        // Given
        SyncResult.Failure failure = new SyncResult.Failure("GitHub API error", Duration.ofSeconds(2));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(failure));
        
        // When
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Then
        ScheduledSyncService.SyncStatus status = scheduledSyncService.getCurrentSyncStatus();
        assertThat(status.state()).isEqualTo(ScheduledSyncService.SyncState.FAILED);
        assertThat(status.message()).contains("GitHub API error");
    }
    
    @Test
    void performScheduledSync_WithException_UpdatesStatusCorrectly() {
        // Given
        CompletableFuture<SyncResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Network error"));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(failedFuture);
        
        // When
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Then
        ScheduledSyncService.SyncStatus status = scheduledSyncService.getCurrentSyncStatus();
        assertThat(status.state()).isEqualTo(ScheduledSyncService.SyncState.FAILED);
        assertThat(status.message()).contains("Network error");
    }
    
    @Test
    void health_WithSuccessfulSync_ReturnsUpStatus() {
        // Given - simulate a successful sync
        SyncResult.Success successResult = new SyncResult.Success(3, Duration.ofSeconds(5));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(successResult));
        
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        Health health = scheduledSyncService.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("syncState");
        assertThat(health.getDetails()).containsKey("schedule");
        assertThat(health.getDetails().get("syncState")).isEqualTo(ScheduledSyncService.SyncState.SUCCESS);
        assertThat(health.getDetails().get("schedule")).isEqualTo("0 */15 * * * *");
    }
    
    @Test
    void health_WithFailedSync_ReturnsDownStatus() {
        // Given - simulate a failed sync
        SyncResult.Failure failure = new SyncResult.Failure("GitHub API error", Duration.ofSeconds(2));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(failure));
        
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        Health health = scheduledSyncService.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("syncState")).isEqualTo(ScheduledSyncService.SyncState.FAILED);
    }
    
    @Test
    void health_WithPartialFailure_ReturnsUpStatus() {
        // Given - simulate a partial failure
        SyncResult.PartialFailure partialFailure = new SyncResult.PartialFailure(
            2, 1, List.of("Error processing issue 123"), Duration.ofSeconds(3)
        );
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(partialFailure));
        
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // When
        Health health = scheduledSyncService.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("syncState")).isEqualTo(ScheduledSyncService.SyncState.PARTIAL_FAILURE);
    }
    
    @Test
    void health_WithNoSyncPerformed_ReturnsUnknownStatus() {
        // When
        Health health = scheduledSyncService.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails().get("syncState")).isEqualTo(ScheduledSyncService.SyncState.NOT_STARTED);
        assertThat(health.getDetails().get("message")).isEqualTo("No sync operations performed yet");
    }
    
    @Test
    void getCurrentSyncStatus_InitialState_ReturnsNotStarted() {
        // When
        ScheduledSyncService.SyncStatus status = scheduledSyncService.getCurrentSyncStatus();
        
        // Then
        assertThat(status.state()).isEqualTo(ScheduledSyncService.SyncState.NOT_STARTED);
        assertThat(status.startTime()).isNull();
        assertThat(status.endTime()).isNull();
        assertThat(status.message()).isEqualTo("No sync operations performed yet");
    }
}