package com.company.connector.config;

import com.company.connector.exception.FirestoreException;
import com.company.connector.exception.GitHubApiException;
import com.company.connector.exception.SyncException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RetryConfig.
 */
class RetryConfigTest {
    
    private RetryConfig retryConfig;
    private RetryTemplate gitHubRetryTemplate;
    private RetryTemplate firestoreRetryTemplate;
    private RetryTemplate syncRetryTemplate;
    
    @BeforeEach
    void setUp() {
        retryConfig = new RetryConfig();
        gitHubRetryTemplate = retryConfig.gitHubRetryTemplate();
        firestoreRetryTemplate = retryConfig.firestoreRetryTemplate();
        syncRetryTemplate = retryConfig.syncRetryTemplate();
    }
    
    @Test
    void gitHubRetryTemplate_RetriesOnGitHubApiException() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        GitHubApiException retryableException = new GitHubApiException(
            "Server error", HttpStatus.INTERNAL_SERVER_ERROR);
        
        // When/Then
        assertThatThrownBy(() -> gitHubRetryTemplate.execute(context -> {
            attemptCount.incrementAndGet();
            throw retryableException;
        })).isInstanceOf(GitHubApiException.class);
        
        // Should retry 3 times (initial + 2 retries)
        assertThat(attemptCount.get()).isEqualTo(3);
    }
    
    @Test
    void gitHubRetryTemplate_DoesNotRetryOnAuthenticationFailure() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        GitHubApiException nonRetryableException = GitHubApiException.authenticationFailed(
            "Authentication failed", null);
        
        // When/Then
        assertThatThrownBy(() -> gitHubRetryTemplate.execute(context -> {
            attemptCount.incrementAndGet();
            throw nonRetryableException;
        })).isInstanceOf(GitHubApiException.class);
        
        // Should not retry authentication failures
        assertThat(attemptCount.get()).isEqualTo(1);
    }
    
    @Test
    void gitHubRetryTemplate_DoesNotRetryOnIllegalArgumentException() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        IllegalArgumentException validationException = new IllegalArgumentException("Invalid parameter");
        
        // When/Then
        assertThatThrownBy(() -> gitHubRetryTemplate.execute(context -> {
            attemptCount.incrementAndGet();
            throw validationException;
        })).isInstanceOf(IllegalArgumentException.class);
        
        // Should not retry validation errors
        assertThat(attemptCount.get()).isEqualTo(1);
    }
    
    @Test
    void firestoreRetryTemplate_RetriesOnRetryableFirestoreException() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        FirestoreException retryableException = FirestoreException.networkError(
            "Network error", new RuntimeException());
        
        // When/Then
        assertThatThrownBy(() -> firestoreRetryTemplate.execute(context -> {
            attemptCount.incrementAndGet();
            throw retryableException;
        })).isInstanceOf(FirestoreException.class);
        
        // Should retry 3 times (initial + 2 retries)
        assertThat(attemptCount.get()).isEqualTo(3);
    }
    
    @Test
    void firestoreRetryTemplate_DoesNotRetryOnNonRetryableFirestoreException() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        FirestoreException nonRetryableException = FirestoreException.authenticationFailed(
            "Authentication failed", null);
        
        // When/Then
        assertThatThrownBy(() -> firestoreRetryTemplate.execute(context -> {
            attemptCount.incrementAndGet();
            throw nonRetryableException;
        })).isInstanceOf(FirestoreException.class);
        
        // Should not retry authentication failures
        assertThat(attemptCount.get()).isEqualTo(1);
    }
    
    @Test
    void syncRetryTemplate_RetriesOnRetryableSyncException() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        SyncException retryableException = new SyncException("Sync failed", new RuntimeException());
        
        // When/Then
        assertThatThrownBy(() -> syncRetryTemplate.execute(context -> {
            attemptCount.incrementAndGet();
            throw retryableException;
        })).isInstanceOf(SyncException.class);
        
        // Should retry 2 times (initial + 1 retry)
        assertThat(attemptCount.get()).isEqualTo(2);
    }
    
    @Test
    void syncRetryTemplate_DoesNotRetryOnValidationException() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        SyncException validationException = SyncException.validationFailed(
            java.util.List.of("Validation error"));
        
        // When/Then
        assertThatThrownBy(() -> syncRetryTemplate.execute(context -> {
            attemptCount.incrementAndGet();
            throw validationException;
        })).isInstanceOf(SyncException.class);
        
        // Should not retry validation failures
        assertThat(attemptCount.get()).isEqualTo(1);
    }
    
    @Test
    void gitHubRetryTemplate_SucceedsAfterRetry() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        String expectedResult = "success";
        
        // When
        String result = gitHubRetryTemplate.execute(context -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 2) {
                throw new GitHubApiException("Temporary error", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            return expectedResult;
        });
        
        // Then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(attemptCount.get()).isEqualTo(2);
    }
    
    @Test
    void firestoreRetryTemplate_SucceedsAfterRetry() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        String expectedResult = "success";
        
        // When
        String result = firestoreRetryTemplate.execute(context -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 2) {
                throw FirestoreException.networkError("Temporary error", new RuntimeException());
            }
            return expectedResult;
        });
        
        // Then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(attemptCount.get()).isEqualTo(2);
    }
    
    @Test
    void syncRetryTemplate_SucceedsAfterRetry() {
        // Given
        AtomicInteger attemptCount = new AtomicInteger(0);
        String expectedResult = "success";
        
        // When
        String result = syncRetryTemplate.execute(context -> {
            int attempt = attemptCount.incrementAndGet();
            if (attempt < 2) {
                throw new SyncException("Temporary error", new RuntimeException());
            }
            return expectedResult;
        });
        
        // Then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(attemptCount.get()).isEqualTo(2);
    }
}