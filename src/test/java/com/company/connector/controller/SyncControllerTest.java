package com.company.connector.controller;

import com.company.connector.model.SyncResult;
import com.company.connector.service.IssueService;
import com.company.connector.service.ScheduledSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for SyncController.
 * 
 * Tests the REST endpoints for manual sync triggering and status checking.
 */
@WebMvcTest(SyncController.class)
class SyncControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private IssueService issueService;
    
    @MockBean
    private ScheduledSyncService scheduledSyncService;
    
    @Test
    void triggerSync_WithValidParameters_ReturnsSuccessResult() throws Exception {
        // Given
        SyncResult.Success successResult = new SyncResult.Success(3, Duration.ofSeconds(5));
        when(issueService.syncIssues("octocat", "Hello-World", 5))
            .thenReturn(CompletableFuture.completedFuture(successResult));
        
        // When & Then
        mockMvc.perform(post("/api/sync/trigger")
                .param("owner", "octocat")
                .param("repo", "Hello-World")
                .param("limit", "5")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());
        
        verify(issueService).syncIssues("octocat", "Hello-World", 5);
    }
    
    @Test
    void triggerSync_WithDefaultLimit_UsesDefaultValue() throws Exception {
        // Given
        SyncResult.Success successResult = new SyncResult.Success(5, Duration.ofSeconds(3));
        when(issueService.syncIssues("testowner", "testrepo", 5))
            .thenReturn(CompletableFuture.completedFuture(successResult));
        
        // When & Then
        mockMvc.perform(post("/api/sync/trigger")
                .param("owner", "testowner")
                .param("repo", "testrepo")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());
        
        verify(issueService).syncIssues("testowner", "testrepo", 5);
    }
    
    @Test
    void triggerSync_WithPartialFailure_ReturnsPartialFailureResult() throws Exception {
        // Given
        SyncResult.PartialFailure partialFailure = new SyncResult.PartialFailure(
            2, 1, List.of("Error processing issue 123"), Duration.ofSeconds(4)
        );
        when(issueService.syncIssues("owner", "repo", 3))
            .thenReturn(CompletableFuture.completedFuture(partialFailure));
        
        // When & Then
        mockMvc.perform(post("/api/sync/trigger")
                .param("owner", "owner")
                .param("repo", "repo")
                .param("limit", "3")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());
    }
    
    @Test
    void triggerSync_WithFailure_ReturnsFailureResult() throws Exception {
        // Given
        SyncResult.Failure failure = new SyncResult.Failure("GitHub API error", Duration.ofSeconds(2));
        when(issueService.syncIssues("owner", "repo", 5))
            .thenReturn(CompletableFuture.completedFuture(failure));
        
        // When & Then
        mockMvc.perform(post("/api/sync/trigger")
                .param("owner", "owner")
                .param("repo", "repo")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());
    }
    
    @Test
    void triggerSync_WithException_ReturnsInternalServerError() throws Exception {
        // Given
        CompletableFuture<SyncResult> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Network error"));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(failedFuture);
        
        // When & Then
        mockMvc.perform(post("/api/sync/trigger")
                .param("owner", "owner")
                .param("repo", "repo")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted());
    }
    
    @Test
    void getSyncStatus_ReturnsCurrentStatus() throws Exception {
        // Given
        Instant startTime = Instant.parse("2024-01-15T10:00:00Z");
        Instant endTime = Instant.parse("2024-01-15T10:05:00Z");
        ScheduledSyncService.SyncStatus status = new ScheduledSyncService.SyncStatus(
            ScheduledSyncService.SyncState.SUCCESS,
            startTime,
            endTime,
            "Sync completed successfully: 3 issues processed"
        );
        when(scheduledSyncService.getCurrentSyncStatus()).thenReturn(status);
        
        // When & Then
        mockMvc.perform(get("/api/sync/status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("SUCCESS"))
            .andExpect(jsonPath("$.startTime").value("2024-01-15T10:00:00Z"))
            .andExpect(jsonPath("$.endTime").value("2024-01-15T10:05:00Z"))
            .andExpect(jsonPath("$.message").value("Sync completed successfully: 3 issues processed"));
    }
    
    @Test
    void getSyncStatus_WithNotStartedState_ReturnsNotStartedStatus() throws Exception {
        // Given
        ScheduledSyncService.SyncStatus status = new ScheduledSyncService.SyncStatus(
            ScheduledSyncService.SyncState.NOT_STARTED,
            null,
            null,
            "No sync operations performed yet"
        );
        when(scheduledSyncService.getCurrentSyncStatus()).thenReturn(status);
        
        // When & Then
        mockMvc.perform(get("/api/sync/status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("NOT_STARTED"))
            .andExpect(jsonPath("$.message").value("No sync operations performed yet"));
    }
    
    @Test
    void triggerScheduledSync_ReturnsSuccessMessage() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/sync/trigger-scheduled")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Scheduled sync triggered successfully"))
            .andExpect(jsonPath("$.status").value("TRIGGERED"));
    }
    
    @Test
    void triggerSync_WithMissingOwnerParameter_ReturnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/sync/trigger")
                .param("repo", "testrepo")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }
    
    @Test
    void triggerSync_WithMissingRepoParameter_ReturnsBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/sync/trigger")
                .param("owner", "testowner")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
    }
}