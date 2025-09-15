package com.company.connector.service;

import com.company.connector.config.SyncConfig;
import com.company.connector.model.SyncResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service responsible for scheduled synchronization operations.
 * 
 * This service handles periodic sync execution using Spring's @Scheduled annotation
 * with configurable cron expressions. It also provides health check functionality
 * to monitor sync status and implements sync status tracking.
 */
@Service
public class ScheduledSyncService implements HealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(ScheduledSyncService.class);
    
    private final IssueService issueService;
    private final SyncConfig syncConfig;
    
    // Sync status tracking
    private final AtomicReference<SyncStatus> lastSyncStatus = new AtomicReference<>(
        new SyncStatus(SyncState.NOT_STARTED, null, null, "No sync operations performed yet")
    );
    
    public ScheduledSyncService(IssueService issueService, SyncConfig syncConfig) {
        this.issueService = issueService;
        this.syncConfig = syncConfig;
    }
    
    /**
     * Performs scheduled synchronization using the configured cron expression.
     * 
     * The schedule is configured via the sync.schedule property in application.yml.
     * Default schedule is "0 star/15 star star star star" (every 15 minutes).
     */
    @Scheduled(cron = "${sync.schedule}")
    public void performScheduledSync() {
        logger.info("Starting scheduled sync operation");
        
        Instant startTime = Instant.now();
        updateSyncStatus(SyncState.IN_PROGRESS, startTime, null, "Sync operation in progress");
        
        try {
            // For demo purposes, sync a default repository
            // In production, this would be configurable or iterate through multiple repositories
            String owner = "octocat";  // Default GitHub demo repository
            String repo = "Hello-World";
            
            CompletableFuture<SyncResult> syncFuture = issueService.syncIssues(owner, repo, syncConfig.defaultIssueLimit());
            
            syncFuture.whenComplete((result, throwable) -> {
                Instant endTime = Instant.now();
                
                if (throwable != null) {
                    String errorMessage = "Scheduled sync failed: " + throwable.getMessage();
                    logger.error(errorMessage, throwable);
                    updateSyncStatus(SyncState.FAILED, startTime, endTime, errorMessage);
                } else {
                    handleSyncResult(result, startTime, endTime);
                }
            });
            
        } catch (Exception e) {
            Instant endTime = Instant.now();
            String errorMessage = "Scheduled sync failed with exception: " + e.getMessage();
            logger.error(errorMessage, e);
            updateSyncStatus(SyncState.FAILED, startTime, endTime, errorMessage);
        }
    }
    
    /**
     * Handles the sync result and updates status accordingly.
     */
    private void handleSyncResult(SyncResult result, Instant startTime, Instant endTime) {
        switch (result) {
            case SyncResult.Success success -> {
                String message = String.format("Sync completed successfully: %d issues processed in %s", 
                    success.processedCount(), success.duration());
                logger.info(message);
                updateSyncStatus(SyncState.SUCCESS, startTime, endTime, message);
            }
            case SyncResult.PartialFailure partialFailure -> {
                String message = String.format("Sync completed with partial failures: %d successful, %d failed in %s", 
                    partialFailure.processedCount(), partialFailure.failedCount(), partialFailure.duration());
                logger.warn(message);
                updateSyncStatus(SyncState.PARTIAL_FAILURE, startTime, endTime, message);
            }
            case SyncResult.Failure failure -> {
                String message = String.format("Sync failed: %s (duration: %s)", 
                    failure.error(), failure.duration());
                logger.error(message);
                updateSyncStatus(SyncState.FAILED, startTime, endTime, message);
            }
        }
    }
    
    /**
     * Updates the sync status for health monitoring.
     */
    private void updateSyncStatus(SyncState state, Instant startTime, Instant endTime, String message) {
        SyncStatus status = new SyncStatus(state, startTime, endTime, message);
        lastSyncStatus.set(status);
        logger.debug("Updated sync status: {}", status);
    }
    
    /**
     * Gets the current sync status for monitoring purposes.
     */
    public SyncStatus getCurrentSyncStatus() {
        return lastSyncStatus.get();
    }
    
    /**
     * Provides health check information for Spring Boot Actuator.
     * 
     * This method is called by Spring Boot Actuator to determine the health
     * of the scheduled sync service. It reports the status based on the last
     * sync operation.
     */
    @Override
    public Health health() {
        SyncStatus status = lastSyncStatus.get();
        
        Health.Builder builder = switch (status.state()) {
            case SUCCESS, PARTIAL_FAILURE -> Health.up();
            case IN_PROGRESS -> Health.up();
            case FAILED -> Health.down();
            case NOT_STARTED -> Health.unknown();
        };
        
        builder.withDetail("syncState", status.state());
        
        if (status.startTime() != null) {
            builder.withDetail("lastSyncStart", status.startTime());
        }
        
        if (status.endTime() != null) {
            builder.withDetail("lastSyncEnd", status.endTime());
        }
        
        builder.withDetail("message", status.message());
        builder.withDetail("schedule", syncConfig.schedule());
        
        return builder.build();
    }
    
    /**
     * Enumeration of possible sync states for status tracking.
     */
    public enum SyncState {
        NOT_STARTED,
        IN_PROGRESS,
        SUCCESS,
        PARTIAL_FAILURE,
        FAILED
    }
    
    /**
     * Record representing the current sync status.
     */
    public record SyncStatus(
        SyncState state,
        Instant startTime,
        Instant endTime,
        String message
    ) {}
}