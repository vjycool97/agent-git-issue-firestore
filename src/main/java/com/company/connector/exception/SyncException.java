package com.company.connector.exception;

import java.util.List;

/**
 * Exception thrown when synchronization operations fail.
 * 
 * This exception represents failures in the overall sync process,
 * including validation errors, configuration issues, and orchestration failures.
 */
public class SyncException extends ConnectorException {
    
    private final List<String> validationErrors;
    
    /**
     * Constructs a new sync exception with the specified message.
     * 
     * @param message the detail message
     */
    public SyncException(String message) {
        super("SYNC_ERROR", message);
        this.validationErrors = List.of();
    }
    
    /**
     * Constructs a new sync exception with the specified message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public SyncException(String message, Throwable cause) {
        super("SYNC_ERROR", message, cause);
        this.validationErrors = List.of();
    }
    
    /**
     * Constructs a new sync exception with validation errors.
     * 
     * @param message the detail message
     * @param validationErrors list of validation error messages
     */
    public SyncException(String message, List<String> validationErrors) {
        super("SYNC_VALIDATION_ERROR", message);
        this.validationErrors = List.copyOf(validationErrors);
    }
    
    /**
     * Returns the list of validation errors.
     * 
     * @return the validation errors, or empty list if none
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }
    
    /**
     * Determines if this exception represents a retryable error.
     * Validation errors are generally not retryable.
     * 
     * @return true if the sync operation can be retried, false for validation errors
     */
    @Override
    public boolean isRetryable() {
        return validationErrors.isEmpty();
    }
    
    /**
     * Creates a sync exception for validation failures.
     * 
     * @param validationErrors list of validation error messages
     * @return a new SyncException for validation failures
     */
    public static SyncException validationFailed(List<String> validationErrors) {
        String message = "Sync validation failed with " + validationErrors.size() + " errors";
        return new SyncException(message, validationErrors);
    }
    
    /**
     * Creates a sync exception for configuration errors.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @return a new SyncException for configuration errors
     */
    public static SyncException configurationError(String message, Throwable cause) {
        return new SyncException("Configuration error: " + message, cause);
    }
}