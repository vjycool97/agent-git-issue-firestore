package com.company.connector.controller;

import com.company.connector.monitoring.SyncHealthIndicator;
import com.company.connector.service.IssueService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for monitoring and manual health check operations.
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    
    private static final Logger logger = LoggerFactory.getLogger(MonitoringController.class);
    
    private final SyncHealthIndicator syncHealthIndicator;
    private final IssueService issueService;
    private final MeterRegistry meterRegistry;
    
    public MonitoringController(SyncHealthIndicator syncHealthIndicator, 
                              IssueService issueService,
                              MeterRegistry meterRegistry) {
        this.syncHealthIndicator = syncHealthIndicator;
        this.issueService = issueService;
        this.meterRegistry = meterRegistry;
    }
    
    /**
     * Get sync health status.
     */
    @GetMapping("/health/sync")
    public ResponseEntity<Health> getSyncHealth() {
        logger.debug("Checking sync health status");
        Health health = syncHealthIndicator.health();
        
        if (health.getStatus().getCode().equals("UP")) {
            return ResponseEntity.ok(health);
        } else {
            return ResponseEntity.status(503).body(health);
        }
    }
    
    /**
     * Get basic metrics summary.
     */
    @GetMapping("/metrics/summary")
    public ResponseEntity<Map<String, Object>> getMetricsSummary() {
        logger.debug("Retrieving metrics summary");
        
        try {
            Map<String, Object> metrics = Map.of(
                "sync_operations_success_total", getCounterValue("sync.operations.success"),
                "sync_operations_failure_total", getCounterValue("sync.operations.failure"),
                "sync_issues_processed_total", getCounterValue("sync.issues.processed"),
                "sync_duration_seconds_max", getTimerMaxValue("sync.duration"),
                "sync_duration_seconds_mean", getTimerMeanValue("sync.duration"),
                "jvm_memory_used_bytes", getGaugeValue("jvm.memory.used"),
                "jvm_memory_max_bytes", getGaugeValue("jvm.memory.max")
            );
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            logger.error("Error retrieving metrics summary", e);
            return ResponseEntity.status(500).body(Map.of("error", "Failed to retrieve metrics"));
        }
    }
    
    /**
     * Trigger a manual sync operation for testing.
     */
    @PostMapping("/sync/test")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> triggerTestSync(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(defaultValue = "5") int limit) {
        
        logger.info("Manual sync test triggered for {}/{} with limit {}", owner, repo, limit);
        
        return issueService.syncIssues(owner, repo, limit)
            .thenApply(result -> {
                Map<String, Object> response = switch (result) {
                    case com.company.connector.model.SyncResult.Success success -> Map.of(
                        "status", "success",
                        "processedCount", success.processedCount(),
                        "duration", success.duration().toString()
                    );
                    case com.company.connector.model.SyncResult.PartialFailure partialFailure -> Map.of(
                        "status", "partial_failure",
                        "processedCount", partialFailure.processedCount(),
                        "failedCount", partialFailure.failedCount(),
                        "errors", partialFailure.errors(),
                        "duration", partialFailure.duration().toString()
                    );
                    case com.company.connector.model.SyncResult.Failure failure -> Map.of(
                        "status", "failure",
                        "error", failure.error(),
                        "duration", failure.duration().toString()
                    );
                };
                
                return ResponseEntity.ok(response);
            })
            .exceptionally(throwable -> {
                logger.error("Manual sync test failed", throwable);
                return ResponseEntity.status(500).body(Map.of(
                    "status", "error",
                    "message", throwable.getMessage()
                ));
            });
    }
    
    private double getCounterValue(String name) {
        try {
            return meterRegistry.counter(name).count();
        } catch (Exception e) {
            logger.warn("Failed to get counter value for {}: {}", name, e.getMessage());
            return 0.0;
        }
    }
    
    private double getTimerMaxValue(String name) {
        try {
            return meterRegistry.timer(name).max(java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Failed to get timer max value for {}: {}", name, e.getMessage());
            return 0.0;
        }
    }
    
    private double getTimerMeanValue(String name) {
        try {
            return meterRegistry.timer(name).mean(java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Failed to get timer mean value for {}: {}", name, e.getMessage());
            return 0.0;
        }
    }
    
    private double getGaugeValue(String name) {
        try {
            return meterRegistry.find(name).gauge() != null ? 
                   meterRegistry.find(name).gauge().value() : 0.0;
        } catch (Exception e) {
            logger.warn("Failed to get gauge value for {}: {}", name, e.getMessage());
            return 0.0;
        }
    }
}