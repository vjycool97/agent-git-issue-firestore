package com.company.connector.exception;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SyncException.
 */
class SyncExceptionTest {
    
    @Test
    void constructor_WithMessage_SetsProperties() {
        // Given
        String message = "Sync failed";
        
        // When
        SyncException exception = new SyncException(message);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("SYNC_ERROR");
        assertThat(exception.getValidationErrors()).isEmpty();
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void constructor_WithMessageAndCause_SetsProperties() {
        // Given
        String message = "Sync failed";
        RuntimeException cause = new RuntimeException("Root cause");
        
        // When
        SyncException exception = new SyncException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getValidationErrors()).isEmpty();
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void constructor_WithValidationErrors_SetsProperties() {
        // Given
        String message = "Validation failed";
        List<String> validationErrors = List.of("Error 1", "Error 2");
        
        // When
        SyncException exception = new SyncException(message, validationErrors);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getErrorCode()).isEqualTo("SYNC_VALIDATION_ERROR");
        assertThat(exception.getValidationErrors()).containsExactly("Error 1", "Error 2");
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void isRetryable_WithoutValidationErrors_ReturnsTrue() {
        // Given
        SyncException exception = new SyncException("Sync failed");
        
        // When/Then
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void isRetryable_WithValidationErrors_ReturnsFalse() {
        // Given
        SyncException exception = new SyncException("Validation failed", List.of("Error"));
        
        // When/Then
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void validationFailed_CreatesCorrectException() {
        // Given
        List<String> validationErrors = List.of("Field missing", "Invalid format");
        
        // When
        SyncException exception = SyncException.validationFailed(validationErrors);
        
        // Then
        assertThat(exception.getMessage()).contains("Sync validation failed with 2 errors");
        assertThat(exception.getErrorCode()).isEqualTo("SYNC_VALIDATION_ERROR");
        assertThat(exception.getValidationErrors()).containsExactly("Field missing", "Invalid format");
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void configurationError_CreatesCorrectException() {
        // Given
        String message = "Invalid config";
        RuntimeException cause = new RuntimeException("Config parse error");
        
        // When
        SyncException exception = SyncException.configurationError(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo("Configuration error: " + message);
        assertThat(exception.getErrorCode()).isEqualTo("SYNC_ERROR");
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void getValidationErrors_ReturnsImmutableList() {
        // Given
        List<String> originalErrors = List.of("Error 1", "Error 2");
        SyncException exception = new SyncException("Validation failed", originalErrors);
        
        // When
        List<String> retrievedErrors = exception.getValidationErrors();
        
        // Then
        assertThat(retrievedErrors).containsExactly("Error 1", "Error 2");
        
        // Verify it's immutable (this should not affect the original)
        assertThat(retrievedErrors).isInstanceOf(List.class);
        // The returned list should be a copy, not the original
        assertThat(retrievedErrors).isNotSameAs(originalErrors);
    }
}