package com.company.connector.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for synchronization settings.
 * Uses Java Records for immutable configuration with validation.
 */
@ConfigurationProperties(prefix = "sync")
@Validated
public record SyncConfig(
        @Min(value = 1, message = "Default issue limit must be at least 1")
        @Max(value = 100, message = "Default issue limit cannot exceed 100")
        int defaultIssueLimit,
        
        @Min(value = 1, message = "Batch size must be at least 1")
        @Max(value = 50, message = "Batch size cannot exceed 50")
        int batchSize,
        
        @NotBlank(message = "Schedule cron expression is required")
        String schedule
) {
    
    /**
     * Validates that the schedule is a valid cron expression format.
     * This is a basic validation - Spring's @Scheduled will provide runtime validation.
     */
    public SyncConfig {
        if (schedule != null && !schedule.trim().isEmpty()) {
            String[] parts = schedule.trim().split("\\s+");
            if (parts.length != 6) {
                throw new IllegalArgumentException(
                    "Schedule must be a valid cron expression with 6 fields (second minute hour day month weekday)"
                );
            }
        }
    }
}