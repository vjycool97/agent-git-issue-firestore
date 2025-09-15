package com.company.connector.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Duration;
import java.util.List;

/**
 * Sealed interface representing the result of a synchronization operation.
 * 
 * This uses Java 21's sealed interfaces to provide a type-safe way to represent
 * different outcomes of sync operations with pattern matching support.
 */
public sealed interface SyncResult 
    permits SyncResult.Success, SyncResult.PartialFailure, SyncResult.Failure {
    
    /**
     * Gets the duration of the sync operation.
     * 
     * @return the duration of the operation
     */
    Duration duration();
    
    /**
     * Checks if the sync operation was completely successful.
     * 
     * @return true if the operation was a complete success
     */
    default boolean isSuccess() {
        return this instanceof Success;
    }
    
    /**
     * Checks if the sync operation had partial failures.
     * 
     * @return true if the operation had some failures but some successes
     */
    default boolean isPartialFailure() {
        return this instanceof PartialFailure;
    }
    
    /**
     * Checks if the sync operation completely failed.
     * 
     * @return true if the operation completely failed
     */
    default boolean isFailure() {
        return this instanceof Failure;
    }
    
    /**
     * Record representing a completely successful sync operation.
     * 
     * @param processedCount the number of issues successfully processed
     * @param duration the time taken for the operation
     */
    record Success(
        @PositiveOrZero int processedCount,
        @NotNull Duration duration
    ) implements SyncResult {
        
        public Success {
            if (processedCount < 0) {
                throw new IllegalArgumentException("Processed count cannot be negative");
            }
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("Duration must be non-null and non-negative");
            }
        }
    }
    
    /**
     * Record representing a sync operation with partial failures.
     * 
     * @param processedCount the number of issues successfully processed
     * @param failedCount the number of issues that failed to process
     * @param errors list of error messages for failed items
     * @param duration the time taken for the operation
     */
    record PartialFailure(
        @PositiveOrZero int processedCount,
        @PositiveOrZero int failedCount,
        @NotNull List<String> errors,
        @NotNull Duration duration
    ) implements SyncResult {
        
        public PartialFailure {
            if (processedCount < 0) {
                throw new IllegalArgumentException("Processed count cannot be negative");
            }
            if (failedCount <= 0) {
                throw new IllegalArgumentException("Failed count must be positive for partial failure");
            }
            if (errors == null || errors.isEmpty()) {
                throw new IllegalArgumentException("Errors list cannot be null or empty for partial failure");
            }
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("Duration must be non-null and non-negative");
            }
            
            // Make errors list immutable
            errors = List.copyOf(errors);
        }
        
        /**
         * Gets the total number of items attempted.
         * 
         * @return the sum of processed and failed counts
         */
        public int totalAttempted() {
            return processedCount + failedCount;
        }
        
        /**
         * Gets the success rate as a percentage.
         * 
         * @return the success rate (0.0 to 1.0)
         */
        public double successRate() {
            int total = totalAttempted();
            return total > 0 ? (double) processedCount / total : 0.0;
        }
    }
    
    /**
     * Record representing a completely failed sync operation.
     * 
     * @param error the error message describing the failure
     * @param duration the time taken before the operation failed
     */
    record Failure(
        @NotNull String error,
        @NotNull Duration duration
    ) implements SyncResult {
        
        public Failure {
            if (error == null || error.isBlank()) {
                throw new IllegalArgumentException("Error message cannot be null or blank");
            }
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException("Duration must be non-null and non-negative");
            }
        }
    }
}