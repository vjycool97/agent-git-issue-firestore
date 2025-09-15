package com.company.connector.transformer;

import com.company.connector.exception.ConnectorException;

/**
 * Exception thrown when data transformation operations fail.
 * 
 * This exception is used to indicate issues during the transformation
 * of GitHub issues to Firestore documents, such as missing required
 * fields or invalid data formats.
 */
public class TransformationException extends ConnectorException {
    
    /**
     * Constructs a new transformation exception with the specified detail message.
     * 
     * @param message the detail message
     */
    public TransformationException(String message) {
        super("TRANSFORMATION_ERROR", message);
    }
    
    /**
     * Constructs a new transformation exception with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public TransformationException(String message, Throwable cause) {
        super("TRANSFORMATION_ERROR", message, cause);
    }
    
    /**
     * Transformation errors are generally not retryable as they indicate
     * data quality issues or configuration problems.
     * 
     * @return false, as transformation errors are typically not retryable
     */
    @Override
    public boolean isRetryable() {
        return false;
    }
}