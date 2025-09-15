package com.company.connector.controller;

import com.company.connector.model.SyncResult;
import com.company.connector.monitoring.SyncHealthIndicator;
import com.company.connector.service.IssueService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonitoringControllerTest {
    
    @Mock
    private SyncHealthIndicator syncHealthIndicator;
    
    @Mock
    private IssueService issueService;
    
    private MeterRegistry meterRegistry;
    private MonitoringController controller;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        controller = new MonitoringController(syncHealthIndicator, issueService, meterRegistry);
    }
    
    @Test
    void getSyncHealth_WhenHealthy_ShouldReturn200() {
        // Given
        Health healthyStatus = Health.up()
            .withDetail("status", "Healthy")
            .withDetail("lastSuccessfulSync", "2024-01-15T10:30:00Z")
            .build();
        when(syncHealthIndicator.health()).thenReturn(healthyStatus);
        
        // When
        ResponseEntity<Health> response = controller.getSyncHealth();
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(healthyStatus);
    }
    
    @Test
    void getSyncHealth_WhenUnhealthy_ShouldReturn503() {
        // Given
        Health unhealthyStatus = Health.down()
            .withDetail("status", "Too many consecutive failures")
            .withDetail("consecutiveFailures", 5)
            .build();
        when(syncHealthIndicator.health()).thenReturn(unhealthyStatus);
        
        // When
        ResponseEntity<Health> response = controller.getSyncHealth();
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(unhealthyStatus);
    }
    
    @Test
    void getMetricsSummary_ShouldReturnMetricsMap() {
        // Given
        // Add some metrics to the registry
        meterRegistry.counter("sync.operations.success").increment(5);
        meterRegistry.counter("sync.operations.failure").increment(2);
        meterRegistry.counter("sync.issues.processed").increment(25);
        
        // When
        ResponseEntity<Map<String, Object>> response = controller.getMetricsSummary();
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> metrics = response.getBody();
        assertThat(metrics).isNotNull();
        assertThat(metrics).containsEntry("sync_operations_success_total", 5.0);
        assertThat(metrics).containsEntry("sync_operations_failure_total", 2.0);
        assertThat(metrics).containsEntry("sync_issues_processed_total", 25.0);
    }
    
    @Test
    void triggerTestSync_WithSuccessfulSync_ShouldReturnSuccessResponse() {
        // Given
        SyncResult.Success successResult = new SyncResult.Success(5, Duration.ofSeconds(10));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(successResult));
        
        // When
        CompletableFuture<ResponseEntity<Map<String, Object>>> futureResponse = 
            controller.triggerTestSync("owner", "repo", 5);
        ResponseEntity<Map<String, Object>> response = futureResponse.join();
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("status", "success");
        assertThat(body).containsEntry("processedCount", 5);
        assertThat(body).containsEntry("duration", "PT10S");
    }
    
    @Test
    void triggerTestSync_WithPartialFailure_ShouldReturnPartialFailureResponse() {
        // Given
        SyncResult.PartialFailure partialFailure = new SyncResult.PartialFailure(
            3, 2, java.util.List.of("Error 1", "Error 2"), Duration.ofSeconds(8)
        );
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(partialFailure));
        
        // When
        CompletableFuture<ResponseEntity<Map<String, Object>>> futureResponse = 
            controller.triggerTestSync("owner", "repo", 5);
        ResponseEntity<Map<String, Object>> response = futureResponse.join();
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("status", "partial_failure");
        assertThat(body).containsEntry("processedCount", 3);
        assertThat(body).containsEntry("failedCount", 2);
        assertThat(body).containsKey("errors");
        assertThat(body).containsEntry("duration", "PT8S");
    }
    
    @Test
    void triggerTestSync_WithFailure_ShouldReturnFailureResponse() {
        // Given
        SyncResult.Failure failure = new SyncResult.Failure("Sync failed", Duration.ofSeconds(5));
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(failure));
        
        // When
        CompletableFuture<ResponseEntity<Map<String, Object>>> futureResponse = 
            controller.triggerTestSync("owner", "repo", 5);
        ResponseEntity<Map<String, Object>> response = futureResponse.join();
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("status", "failure");
        assertThat(body).containsEntry("error", "Sync failed");
        assertThat(body).containsEntry("duration", "PT5S");
    }
    
    @Test
    void triggerTestSync_WithException_ShouldReturnErrorResponse() {
        // Given
        when(issueService.syncIssues(anyString(), anyString(), anyInt()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Test exception")));
        
        // When
        CompletableFuture<ResponseEntity<Map<String, Object>>> futureResponse = 
            controller.triggerTestSync("owner", "repo", 5);
        ResponseEntity<Map<String, Object>> response = futureResponse.join();
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("status", "error");
        assertThat(body).containsKey("message");
    }
}