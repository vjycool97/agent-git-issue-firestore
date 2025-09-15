package com.company.connector.exception;

/**
 * Base exception class for all connector-related exceptions.
 * 
 * This serves as the root exception for the GitHub-Firebase connector,
 * providing a common base for all custom exceptions in the system.
 */
public abstract class ConnectorException extends RuntimeException {
    
    private final String errorCode;
    
    /**
     * Constructs a new connector exception with the specified error code and message.
     * 
     * @param errorCode a unique code identifying the type of error
     * @param message the detail message
     */
    protected ConnectorException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    /**
     * Constructs a new connector exception with the specified error code, message, and cause.
     * 
     * @param errorCode a unique code identifying the type of error
     * @param message the detail message
     * @param cause the cause of the exception
     */
    protected ConnectorException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    /**
     * Returns the error code associated with this exception.
     * 
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }
    
    /**
     * Returns whether this exception represents a retryable error.
     * Subclasses should override this method to indicate if the operation
     * that caused this exception can be safely retried.
     * 
     * @return true if the operation can be retried, false otherwise
     */
    public abstract boolean isRetryable();
}