package com.company.connector.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Custom health indicator for monitoring sync operations.
 * Tracks the last successful sync and failure counts.
 */
@Component
public class SyncHealthIndicator implements HealthIndicator {
    
    private final AtomicReference<Instant> lastSuccessfulSync = new AtomicReference<>();
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private final AtomicLong totalSyncs = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    
    @Override
    public Health health() {
        Health.Builder builder = new Health.Builder();
        
        Instant lastSync = lastSuccessfulSync.get();
        long failures = consecutiveFailures.get();
        long total = totalSyncs.get();
        long totalFailed = totalFailures.get();
        
        // Determine health status
        if (failures >= 5) {
            builder.down()
                   .withDetail("status", "Too many consecutive failures")
                   .withDetail("consecutiveFailures", failures)
                   .withDetail("reason", "Sync operations are consistently failing");
        } else if (lastSync == null) {
            builder.down()
                   .withDetail("status", "No successful sync yet")
                   .withDetail("reason", "Application has not completed any successful sync operations");
        } else if (isLastSyncTooOld(lastSync)) {
            builder.down()
                   .withDetail("status", "Last sync too old")
                   .withDetail("lastSuccessfulSync", lastSync)
                   .withDetail("reason", "No successful sync in the last hour");
        } else {
            builder.up()
                   .withDetail("status", "Healthy")
                   .withDetail("lastSuccessfulSync", lastSync);
        }
        
        // Add common details
        builder.withDetail("totalSyncs", total)
               .withDetail("totalFailures", totalFailed)
               .withDetail("consecutiveFailures", failures)
               .withDetail("successRate", calculateSuccessRate(total, totalFailed));
        
        return builder.build();
    }
    
    /**
     * Record a successful sync operation.
     */
    public void recordSuccessfulSync() {
        lastSuccessfulSync.set(Instant.now());
        consecutiveFailures.set(0);
        totalSyncs.incrementAndGet();
    }
    
    /**
     * Record a failed sync operation.
     */
    public void recordFailedSync() {
        consecutiveFailures.incrementAndGet();
        totalSyncs.incrementAndGet();
        totalFailures.incrementAndGet();
    }
    
    private boolean isLastSyncTooOld(Instant lastSync) {
        return ChronoUnit.HOURS.between(lastSync, Instant.now()) > 1;
    }
    
    private String calculateSuccessRate(long total, long failed) {
        if (total == 0) {
            return "N/A";
        }
        double rate = ((double) (total - failed) / total) * 100;
        return String.format("%.2f%%", rate);
    }
}