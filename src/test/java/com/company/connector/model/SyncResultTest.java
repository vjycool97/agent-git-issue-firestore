package com.company.connector.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SyncResult Sealed Interface Tests")
class SyncResultTest {
    
    @Test
    @DisplayName("Should create valid Success result")
    void shouldCreateValidSuccessResult() {
        // Given
        int processedCount = 5;
        Duration duration = Duration.ofSeconds(10);
        
        // When
        SyncResult.Success success = new SyncResult.Success(processedCount, duration);
        
        // Then
        assertEquals(processedCount, success.processedCount());
        assertEquals(duration, success.duration());
        assertTrue(success.isSuccess());
        assertFalse(success.isPartialFailure());
        assertFalse(success.isFailure());
    }
    
    @Test
    @DisplayName("Should throw exception for negative processed count in Success")
    void shouldThrowExceptionForNegativeProcessedCountInSuccess() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SyncResult.Success(-1, Duration.ofSeconds(10))
        );
        assertEquals("Processed count cannot be negative", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for null duration in Success")
    void shouldThrowExceptionForNullDurationInSuccess() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SyncResult.Success(5, null)
        );
        assertEquals("Duration must be non-null and non-negative", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for negative duration in Success")
    void shouldThrowExceptionForNegativeDurationInSuccess() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SyncResult.Success(5, Duration.ofSeconds(-1))
        );
        assertEquals("Duration must be non-null and non-negative", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should create valid PartialFailure result")
    void shouldCreateValidPartialFailureResult() {
        // Given
        int processedCount = 3;
        int failedCount = 2;
        List<String> errors = List.of("Error 1", "Error 2");
        Duration duration = Duration.ofSeconds(15);
        
        // When
        SyncResult.PartialFailure partialFailure = new SyncResult.PartialFailure(
            processedCount, failedCount, errors, duration
        );
        
        // Then
        assertEquals(processedCount, partialFailure.processedCount());
        assertEquals(failedCount, partialFailure.failedCount());
        assertEquals(errors, partialFailure.errors());
        assertEquals(duration, partialFailure.duration());
        assertFalse(partialFailure.isSuccess());
        assertTrue(partialFailure.isPartialFailure());
        assertFalse(partialFailure.isFailure());
    }
    
    @Test
    @DisplayName("Should calculate total attempted and success rate in PartialFailure")
    void shouldCalculateTotalAttemptedAndSuccessRateInPartialFailure() {
        // Given
        SyncResult.PartialFailure partialFailure = new SyncResult.PartialFailure(
            3, 2, List.of("Error 1", "Error 2"), Duration.ofSeconds(15)
        );
        
        // When & Then
        assertEquals(5, partialFailure.totalAttempted());
        assertEquals(0.6, partialFailure.successRate(), 0.001);
    }
    
    @Test
    @DisplayName("Should handle zero processed count in PartialFailure success rate")
    void shouldHandleZeroProcessedCountInPartialFailureSuccessRate() {
        // Given
        SyncResult.PartialFailure partialFailure = new SyncResult.PartialFailure(
            0, 5, List.of("Error 1", "Error 2", "Error 3", "Error 4", "Error 5"), Duration.ofSeconds(15)
        );
        
        // When & Then
        assertEquals(5, partialFailure.totalAttempted());
        assertEquals(0.0, partialFailure.successRate(), 0.001);
    }
    
    @Test
    @DisplayName("Should throw exception for zero failed count in PartialFailure")
    void shouldThrowExceptionForZeroFailedCountInPartialFailure() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SyncResult.PartialFailure(5, 0, List.of("Error"), Duration.ofSeconds(10))
        );
        assertEquals("Failed count must be positive for partial failure", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for empty errors list in PartialFailure")
    void shouldThrowExceptionForEmptyErrorsListInPartialFailure() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SyncResult.PartialFailure(3, 2, List.of(), Duration.ofSeconds(10))
        );
        assertEquals("Errors list cannot be null or empty for partial failure", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should make errors list immutable in PartialFailure")
    void shouldMakeErrorsListImmutableInPartialFailure() {
        // Given
        List<String> mutableErrors = List.of("Error 1", "Error 2");
        
        // When
        SyncResult.PartialFailure partialFailure = new SyncResult.PartialFailure(
            3, 2, mutableErrors, Duration.ofSeconds(15)
        );
        
        // Then
        assertThrows(UnsupportedOperationException.class, () -> {
            partialFailure.errors().add("New Error");
        });
    }
    
    @Test
    @DisplayName("Should create valid Failure result")
    void shouldCreateValidFailureResult() {
        // Given
        String error = "Connection timeout to GitHub API";
        Duration duration = Duration.ofSeconds(30);
        
        // When
        SyncResult.Failure failure = new SyncResult.Failure(error, duration);
        
        // Then
        assertEquals(error, failure.error());
        assertEquals(duration, failure.duration());
        assertFalse(failure.isSuccess());
        assertFalse(failure.isPartialFailure());
        assertTrue(failure.isFailure());
    }
    
    @Test
    @DisplayName("Should throw exception for null error in Failure")
    void shouldThrowExceptionForNullErrorInFailure() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SyncResult.Failure(null, Duration.ofSeconds(10))
        );
        assertEquals("Error message cannot be null or blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for blank error in Failure")
    void shouldThrowExceptionForBlankErrorInFailure() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SyncResult.Failure("   ", Duration.ofSeconds(10))
        );
        assertEquals("Error message cannot be null or blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should support type checking with sealed interface")
    void shouldSupportTypeCheckingWithSealedInterface() {
        // Given
        SyncResult success = new SyncResult.Success(5, Duration.ofSeconds(10));
        SyncResult partialFailure = new SyncResult.PartialFailure(
            3, 2, List.of("Error 1", "Error 2"), Duration.ofSeconds(15)
        );
        SyncResult failure = new SyncResult.Failure("Connection failed", Duration.ofSeconds(30));
        
        // When & Then
        String successMessage = getResultMessage(success);
        String partialFailureMessage = getResultMessage(partialFailure);
        String failureMessage = getResultMessage(failure);
        
        assertEquals("Processed 5 items successfully", successMessage);
        assertEquals("Partial success: 3 processed, 2 failed", partialFailureMessage);
        assertEquals("Failed: Connection failed", failureMessage);
    }
    
    private String getResultMessage(SyncResult result) {
        if (result instanceof SyncResult.Success s) {
            return "Processed " + s.processedCount() + " items successfully";
        } else if (result instanceof SyncResult.PartialFailure pf) {
            return "Partial success: " + pf.processedCount() + " processed, " + pf.failedCount() + " failed";
        } else if (result instanceof SyncResult.Failure f) {
            return "Failed: " + f.error();
        }
        throw new IllegalArgumentException("Unknown result type");
    }
    
    @Test
    @DisplayName("Should maintain equality and hashCode contract for Success")
    void shouldMaintainEqualityAndHashCodeContractForSuccess() {
        // Given
        Duration duration = Duration.ofSeconds(10);
        SyncResult.Success success1 = new SyncResult.Success(5, duration);
        SyncResult.Success success2 = new SyncResult.Success(5, duration);
        SyncResult.Success success3 = new SyncResult.Success(3, duration);
        
        // When & Then
        assertEquals(success1, success2);
        assertEquals(success1.hashCode(), success2.hashCode());
        assertNotEquals(success1, success3);
        assertNotEquals(success1.hashCode(), success3.hashCode());
    }
    
    @Test
    @DisplayName("Should maintain equality and hashCode contract for PartialFailure")
    void shouldMaintainEqualityAndHashCodeContractForPartialFailure() {
        // Given
        Duration duration = Duration.ofSeconds(15);
        List<String> errors = List.of("Error 1", "Error 2");
        
        SyncResult.PartialFailure pf1 = new SyncResult.PartialFailure(3, 2, errors, duration);
        SyncResult.PartialFailure pf2 = new SyncResult.PartialFailure(3, 2, errors, duration);
        SyncResult.PartialFailure pf3 = new SyncResult.PartialFailure(2, 2, errors, duration);
        
        // When & Then
        assertEquals(pf1, pf2);
        assertEquals(pf1.hashCode(), pf2.hashCode());
        assertNotEquals(pf1, pf3);
        assertNotEquals(pf1.hashCode(), pf3.hashCode());
    }
    
    @Test
    @DisplayName("Should maintain equality and hashCode contract for Failure")
    void shouldMaintainEqualityAndHashCodeContractForFailure() {
        // Given
        Duration duration = Duration.ofSeconds(30);
        SyncResult.Failure failure1 = new SyncResult.Failure("Connection failed", duration);
        SyncResult.Failure failure2 = new SyncResult.Failure("Connection failed", duration);
        SyncResult.Failure failure3 = new SyncResult.Failure("Authentication failed", duration);
        
        // When & Then
        assertEquals(failure1, failure2);
        assertEquals(failure1.hashCode(), failure2.hashCode());
        assertNotEquals(failure1, failure3);
        assertNotEquals(failure1.hashCode(), failure3.hashCode());
    }
}