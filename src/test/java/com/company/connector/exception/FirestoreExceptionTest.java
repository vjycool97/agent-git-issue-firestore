package com.company.connector.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FirestoreException.
 */
class FirestoreExceptionTest {
    
    @Test
    void constructor_WithErrorTypeAndMessage_SetsProperties() {
        // Given
        FirestoreException.FirestoreErrorType errorType = FirestoreException.FirestoreErrorType.NETWORK_ERROR;
        String message = "Network error";
        
        // When
        FirestoreException exception = new FirestoreException(errorType, message);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorType()).isEqualTo(errorType);
        assertThat(exception.getErrorCode()).isEqualTo("FIRESTORE_ERROR");
    }
    
    @Test
    void constructor_WithErrorTypeMessageAndCause_SetsProperties() {
        // Given
        FirestoreException.FirestoreErrorType errorType = FirestoreException.FirestoreErrorType.PERMISSION_DENIED;
        String message = "Permission denied";
        RuntimeException cause = new RuntimeException("Access denied");
        
        // When
        FirestoreException exception = new FirestoreException(errorType, message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorType()).isEqualTo(errorType);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void isRetryable_WithNetworkError_ReturnsTrue() {
        // Given
        FirestoreException exception = new FirestoreException(
            FirestoreException.FirestoreErrorType.NETWORK_ERROR, "Network error");
        
        // When/Then
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void isRetryable_WithQuotaExceeded_ReturnsTrue() {
        // Given
        FirestoreException exception = new FirestoreException(
            FirestoreException.FirestoreErrorType.QUOTA_EXCEEDED, "Quota exceeded");
        
        // When/Then
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void isRetryable_WithUnknownError_ReturnsTrue() {
        // Given
        FirestoreException exception = new FirestoreException(
            FirestoreException.FirestoreErrorType.UNKNOWN_ERROR, "Unknown error");
        
        // When/Then
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void isRetryable_WithAuthenticationFailed_ReturnsFalse() {
        // Given
        FirestoreException exception = new FirestoreException(
            FirestoreException.FirestoreErrorType.AUTHENTICATION_FAILED, "Auth failed");
        
        // When/Then
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void isRetryable_WithPermissionDenied_ReturnsFalse() {
        // Given
        FirestoreException exception = new FirestoreException(
            FirestoreException.FirestoreErrorType.PERMISSION_DENIED, "Permission denied");
        
        // When/Then
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void isRetryable_WithInvalidDocument_ReturnsFalse() {
        // Given
        FirestoreException exception = new FirestoreException(
            FirestoreException.FirestoreErrorType.INVALID_DOCUMENT, "Invalid document");
        
        // When/Then
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void isRetryable_WithCollectionNotFound_ReturnsFalse() {
        // Given
        FirestoreException exception = new FirestoreException(
            FirestoreException.FirestoreErrorType.COLLECTION_NOT_FOUND, "Collection not found");
        
        // When/Then
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void authenticationFailed_CreatesCorrectException() {
        // Given
        String message = "Authentication failed";
        RuntimeException cause = new RuntimeException("Invalid credentials");
        
        // When
        FirestoreException exception = FirestoreException.authenticationFailed(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorType()).isEqualTo(FirestoreException.FirestoreErrorType.AUTHENTICATION_FAILED);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void permissionDenied_CreatesCorrectException() {
        // Given
        String message = "Permission denied";
        RuntimeException cause = new RuntimeException("Access denied");
        
        // When
        FirestoreException exception = FirestoreException.permissionDenied(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorType()).isEqualTo(FirestoreException.FirestoreErrorType.PERMISSION_DENIED);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void quotaExceeded_CreatesCorrectException() {
        // Given
        String message = "Quota exceeded";
        RuntimeException cause = new RuntimeException("Rate limit hit");
        
        // When
        FirestoreException exception = FirestoreException.quotaExceeded(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorType()).isEqualTo(FirestoreException.FirestoreErrorType.QUOTA_EXCEEDED);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void networkError_CreatesCorrectException() {
        // Given
        String message = "Network error";
        RuntimeException cause = new RuntimeException("Connection failed");
        
        // When
        FirestoreException exception = FirestoreException.networkError(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorType()).isEqualTo(FirestoreException.FirestoreErrorType.NETWORK_ERROR);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.isRetryable()).isTrue();
    }
}