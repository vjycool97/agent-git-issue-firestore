package com.company.connector.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service for tracking sync operation metrics using Micrometer.
 */
@Service
public class SyncMetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(SyncMetricsService.class);
    
    private final Counter syncSuccessCounter;
    private final Counter syncFailureCounter;
    private final Counter issuesProcessedCounter;
    private final Timer syncDurationTimer;
    private final SyncHealthIndicator healthIndicator;
    
    public SyncMetricsService(MeterRegistry meterRegistry, SyncHealthIndicator healthIndicator) {
        this.healthIndicator = healthIndicator;
        
        // Initialize counters
        this.syncSuccessCounter = Counter.builder("sync.operations.success")
                .description("Number of successful sync operations")
                .register(meterRegistry);
                
        this.syncFailureCounter = Counter.builder("sync.operations.failure")
                .description("Number of failed sync operations")
                .register(meterRegistry);
                
        this.issuesProcessedCounter = Counter.builder("sync.issues.processed")
                .description("Total number of issues processed")
                .register(meterRegistry);
                
        this.syncDurationTimer = Timer.builder("sync.duration")
                .description("Duration of sync operations")
                .register(meterRegistry);
    }
    
    /**
     * Record a successful sync operation.
     */
    public void recordSyncSuccess(int issuesProcessed, Duration duration) {
        syncSuccessCounter.increment();
        issuesProcessedCounter.increment(issuesProcessed);
        syncDurationTimer.record(duration);
        healthIndicator.recordSuccessfulSync();
        
        logger.info("Sync operation completed successfully. Issues processed: {}, Duration: {}ms", 
                   issuesProcessed, duration.toMillis());
    }
    
    /**
     * Record a failed sync operation.
     */
    public void recordSyncFailure(String errorMessage, Duration duration) {
        syncFailureCounter.increment();
        syncDurationTimer.record(duration);
        healthIndicator.recordFailedSync();
        
        logger.error("Sync operation failed. Duration: {}ms, Error: {}", 
                    duration.toMillis(), errorMessage);
    }
    
    /**
     * Record a partial sync failure.
     */
    public void recordPartialSyncFailure(int successCount, int failureCount, Duration duration) {
        // Count as success since some issues were processed
        syncSuccessCounter.increment();
        issuesProcessedCounter.increment(successCount);
        syncDurationTimer.record(duration);
        healthIndicator.recordSuccessfulSync();
        
        logger.warn("Sync operation completed with partial failures. " +
                   "Successful: {}, Failed: {}, Duration: {}ms", 
                   successCount, failureCount, duration.toMillis());
    }
}