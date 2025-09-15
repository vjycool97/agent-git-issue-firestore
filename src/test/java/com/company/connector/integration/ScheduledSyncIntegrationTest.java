package com.company.connector.integration;

import com.company.connector.config.SyncConfig;
import com.company.connector.model.SyncResult;
import com.company.connector.service.IssueService;
import com.company.connector.service.ScheduledSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for scheduled synchronization functionality.
 * 
 * These tests verify the complete integration of scheduled sync operations,
 * REST endpoints, health checks, and status monitoring in a real Spring Boot
 * application context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ScheduledSyncIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private ScheduledSyncService scheduledSyncService;
    
    @Autowired
    private SyncConfig syncConfig;
    
    @MockBean
    private IssueService issueService;
    
    @Test
    void scheduledSyncService_Configuration_IsCorrectlyLoaded() {
        // Verify that the sync configuration is loaded correctly
        assertThat(syncConfig.defaultIssueLimit()).isEqualTo(5);
        assertThat(syncConfig.batchSize()).isEqualTo(10);
        assertThat(syncConfig.schedule()).isEqualTo("0 */15 * * * *");
    }
    
    @Test
    void manualSyncTrigger_WithValidParameters_ReturnsSuccessResult() {
        // Given
        SyncResult.Success successResult = new SyncResult.Success(3, Duration.ofSeconds(5));
        when(issueService.syncIssues("octocat", "Hello-World", 5))
            .thenReturn(CompletableFuture.completedFuture(successResult));
        
        // When
        String url = "http://localhost:" + port + "/api/sync/trigger?owner=octocat&repo=Hello-World&limit=5";
        ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("processedCount")).isEqualTo(3);
        
        verify(issueService).syncIssues("octocat", "Hello-World", 5);
    }
    
    @Test
    void manualSyncTrigger_WithPartialFailure_ReturnsPartialFailureResult() {
        // Given
        SyncResult.PartialFailure partialFailure = new SyncResult.PartialFailure(
            2, 1, List.of("Error processing issue 123"), Duration.ofSeconds(4)
        );
        when(issueService.syncIssues("owner", "repo", 3))
            .thenReturn(CompletableFuture.completedFuture(partialFailure));
        
        // When
        String url = "http://localhost:" + port + "/api/sync/trigger?owner=owner&repo=repo&limit=3";
        ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("processedCount")).isEqualTo(2);
        assertThat(response.getBody().get("failedCount")).isEqualTo(1);
        assertThat(((List<?>) response.getBody().get("errors"))).hasSize(1);
    }
    
    @Test
    void syncStatus_AfterSuccessfulSync_ReturnsCorrectStatus() throws InterruptedException {
        // Given
        SyncResult.Success successResult = new SyncResult.Success(3, Duration.ofSeconds(5));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(successResult));
        
        // Trigger a sync operation
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        Thread.sleep(200);
        
        // When
        String url = "http://localhost:" + port + "/api/sync/status";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("state")).isEqualTo("SUCCESS");
        assertThat(response.getBody().get("message")).asString().contains("3 issues processed");
        assertThat(response.getBody().get("startTime")).isNotNull();
        assertThat(response.getBody().get("endTime")).isNotNull();
    }
    
    @Test
    void triggerScheduledSync_ReturnsSuccessMessage() {
        // When
        String url = "http://localhost:" + port + "/api/sync/trigger-scheduled";
        ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Scheduled sync triggered successfully");
        assertThat(response.getBody().get("status")).isEqualTo("TRIGGERED");
    }
    
    @Test
    void healthCheck_WithSuccessfulSync_ReturnsUpStatus() throws InterruptedException {
        // Given
        SyncResult.Success successResult = new SyncResult.Success(3, Duration.ofSeconds(5));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(successResult));
        
        // Trigger a sync operation
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        Thread.sleep(200);
        
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
    void healthCheck_WithFailedSync_ReturnsDownStatus() throws InterruptedException {
        // Given
        SyncResult.Failure failure = new SyncResult.Failure("GitHub API error", Duration.ofSeconds(2));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(failure));
        
        // Trigger a sync operation
        scheduledSyncService.performScheduledSync();
        
        // Wait for async completion
        Thread.sleep(200);
        
        // When
        Health health = scheduledSyncService.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("syncState")).isEqualTo(ScheduledSyncService.SyncState.FAILED);
        assertThat(health.getDetails().get("message")).asString().contains("GitHub API error");
    }
    
    @Test
    void healthCheck_InitialState_ReturnsUnknownStatus() {
        // When
        Health health = scheduledSyncService.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails().get("syncState")).isEqualTo(ScheduledSyncService.SyncState.NOT_STARTED);
        assertThat(health.getDetails().get("message")).isEqualTo("No sync operations performed yet");
    }
    
    @Test
    void actuatorHealthEndpoint_IncludesScheduledSyncHealth() {
        // When
        String url = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        
        // The health endpoint should include our ScheduledSyncService health indicator
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        if (components != null) {
            assertThat(components).containsKey("scheduledSyncService");
        }
    }
    
    @Test
    void syncConfiguration_IsValidCronExpression() {
        // Verify that the cron expression in configuration is valid
        // Spring will validate this at startup, but we can also test it explicitly
        assertThat(syncConfig.schedule()).matches("^[0-9*,/-]+ [0-9*,/-]+ [0-9*,/-]+ [0-9*,/-]+ [0-9*,/-]+ [0-9*,/-]+$");
    }
    
    @Test
    void manualSyncTrigger_WithInvalidParameters_ReturnsBadRequest() {
        // When - missing required parameters
        String url = "http://localhost:" + port + "/api/sync/trigger";
        ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
    
    @Test
    void syncEndpoints_AreAccessibleWithoutAuthentication() {
        // Test that sync endpoints are accessible (in a real production environment,
        // these would likely be secured)
        
        // Status endpoint
        String statusUrl = "http://localhost:" + port + "/api/sync/status";
        ResponseEntity<Map> statusResponse = restTemplate.getForEntity(statusUrl, Map.class);
        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // Trigger scheduled sync endpoint
        String triggerUrl = "http://localhost:" + port + "/api/sync/trigger-scheduled";
        ResponseEntity<Map> triggerResponse = restTemplate.postForEntity(triggerUrl, null, Map.class);
        assertThat(triggerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}