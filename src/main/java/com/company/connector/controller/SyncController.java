package com.company.connector.controller;

import com.company.connector.model.SyncResult;
import com.company.connector.service.IssueService;
import com.company.connector.service.ScheduledSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for manual synchronization operations.
 * 
 * This controller provides endpoints for manually triggering sync operations
 * and checking sync status. It complements the scheduled sync functionality
 * by allowing on-demand synchronization.
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncController.class);
    
    private final IssueService issueService;
    private final ScheduledSyncService scheduledSyncService;
    
    public SyncController(IssueService issueService, ScheduledSyncService scheduledSyncService) {
        this.issueService = issueService;
        this.scheduledSyncService = scheduledSyncService;
    }
    
    /**
     * Manually triggers a sync operation for a specific repository.
     * 
     * @param owner the repository owner (username or organization)
     * @param repo the repository name
     * @param limit optional limit for number of issues to sync (default: 5)
     * @return ResponseEntity containing the sync result
     */
    @PostMapping("/trigger")
    public CompletableFuture<ResponseEntity<SyncResult>> triggerSync(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam(defaultValue = "5") int limit) {
        
        logger.info("Manual sync triggered for repository {}/{} with limit {}", owner, repo, limit);
        
        return issueService.syncIssues(owner, repo, limit)
            .thenApply(result -> {
                logger.info("Manual sync completed for repository {}/{}: {}", owner, repo, result);
                return ResponseEntity.ok(result);
            })
            .exceptionally(throwable -> {
                logger.error("Manual sync failed for repository {}/{}: {}", owner, repo, throwable.getMessage(), throwable);
                SyncResult.Failure failureResult = new SyncResult.Failure(
                    "Manual sync failed: " + throwable.getMessage(),
                    java.time.Duration.ZERO
                );
                return ResponseEntity.internalServerError().body(failureResult);
            });
    }
    
    /**
     * Gets the current sync status from the scheduled sync service.
     * 
     * @return ResponseEntity containing the current sync status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        ScheduledSyncService.SyncStatus status = scheduledSyncService.getCurrentSyncStatus();
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("state", status.state());
        response.put("message", status.message());
        
        if (status.startTime() != null) {
            response.put("startTime", status.startTime());
        }
        
        if (status.endTime() != null) {
            response.put("endTime", status.endTime());
        }
        
        logger.debug("Sync status requested: {}", response);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Triggers the scheduled sync operation manually.
     * This is useful for testing or immediate execution of the scheduled task.
     * 
     * @return ResponseEntity with a message indicating the sync has been triggered
     */
    @PostMapping("/trigger-scheduled")
    public ResponseEntity<Map<String, String>> triggerScheduledSync() {
        logger.info("Manual trigger of scheduled sync requested");
        
        try {
            // Execute the scheduled sync in a separate thread to avoid blocking the request
            CompletableFuture.runAsync(scheduledSyncService::performScheduledSync);
            
            Map<String, String> response = Map.of(
                "message", "Scheduled sync triggered successfully",
                "status", "TRIGGERED"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to trigger scheduled sync: {}", e.getMessage(), e);
            
            Map<String, String> response = Map.of(
                "message", "Failed to trigger scheduled sync: " + e.getMessage(),
                "status", "ERROR"
            );
            
            return ResponseEntity.internalServerError().body(response);
        }
    }
}