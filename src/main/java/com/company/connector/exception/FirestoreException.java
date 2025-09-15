package com.company.connector.exception;

/**
 * Exception thrown when Firestore operations fail.
 * 
 * This exception encapsulates various Firestore error scenarios including
 * authentication failures, permission errors, network issues, and quota limits.
 */
public class FirestoreException extends ConnectorException {
    
    private final FirestoreErrorType errorType;
    
    /**
     * Enumeration of Firestore error types.
     */
    public enum FirestoreErrorType {
        AUTHENTICATION_FAILED,
        PERMISSION_DENIED,
        QUOTA_EXCEEDED,
        NETWORK_ERROR,
        INVALID_DOCUMENT,
        COLLECTION_NOT_FOUND,
        UNKNOWN_ERROR
    }
    
    /**
     * Constructs a new Firestore exception with the specified error type and message.
     * 
     * @param errorType the type of Firestore error
     * @param message the detail message
     */
    public FirestoreException(FirestoreErrorType errorType, String message) {
        super("FIRESTORE_ERROR", message);
        this.errorType = errorType;
    }
    
    /**
     * Constructs a new Firestore exception with the specified error type, message, and cause.
     * 
     * @param errorType the type of Firestore error
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public FirestoreException(FirestoreErrorType errorType, String message, Throwable cause) {
        super("FIRESTORE_ERROR", message, cause);
        this.errorType = errorType;
    }
    
    /**
     * Returns the Firestore error type.
     * 
     * @return the error type
     */
    public FirestoreErrorType getErrorType() {
        return errorType;
    }
    
    /**
     * Determines if this exception represents a retryable error.
     * 
     * @return true for network errors and quota exceeded, false for authentication and permission errors
     */
    @Override
    public boolean isRetryable() {
        return switch (errorType) {
            case NETWORK_ERROR, QUOTA_EXCEEDED, UNKNOWN_ERROR -> true;
            case AUTHENTICATION_FAILED, PERMISSION_DENIED, INVALID_DOCUMENT, COLLECTION_NOT_FOUND -> false;
        };
    }
    
    /**
     * Creates a Firestore exception for authentication failures.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @return a new FirestoreException for authentication failures
     */
    public static FirestoreException authenticationFailed(String message, Throwable cause) {
        return new FirestoreException(FirestoreErrorType.AUTHENTICATION_FAILED, message, cause);
    }
    
    /**
     * Creates a Firestore exception for permission denied errors.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @return a new FirestoreException for permission denied errors
     */
    public static FirestoreException permissionDenied(String message, Throwable cause) {
        return new FirestoreException(FirestoreErrorType.PERMISSION_DENIED, message, cause);
    }
    
    /**
     * Creates a Firestore exception for quota exceeded errors.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @return a new FirestoreException for quota exceeded errors
     */
    public static FirestoreException quotaExceeded(String message, Throwable cause) {
        return new FirestoreException(FirestoreErrorType.QUOTA_EXCEEDED, message, cause);
    }
    
    /**
     * Creates a Firestore exception for network errors.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @return a new FirestoreException for network errors
     */
    public static FirestoreException networkError(String message, Throwable cause) {
        return new FirestoreException(FirestoreErrorType.NETWORK_ERROR, message, cause);
    }
}