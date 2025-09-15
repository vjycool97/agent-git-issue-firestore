package com.company.connector.service;

import com.company.connector.exception.ConnectorException;
import com.company.connector.exception.FirestoreException;
import com.company.connector.exception.GitHubApiException;
import com.company.connector.exception.SyncException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ErrorHandler.
 */
class ErrorHandlerTest {
    
    private ErrorHandler errorHandler;
    
    @BeforeEach
    void setUp() {
        errorHandler = new ErrorHandler();
    }
    
    @Test
    void handleGitHubError_WithUnauthorizedException_ReturnsAuthenticationFailedException() {
        // Given
        WebClientResponseException exception = WebClientResponseException.create(
            401, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);
        
        // When
        GitHubApiException result = errorHandler.handleGitHubError(exception, "test context");
        
        // Then
        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.getErrorCode()).isEqualTo("GITHUB_API_ERROR");
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getMessage()).contains("GitHub authentication failed");
    }
    
    @Test
    void handleGitHubError_WithForbiddenException_ReturnsAuthorizationFailedException() {
        // Given
        WebClientResponseException exception = WebClientResponseException.create(
            403, "Forbidden", HttpHeaders.EMPTY, new byte[0], null);
        
        // When
        GitHubApiException result = errorHandler.handleGitHubError(exception, "test context");
        
        // Then
        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(result.getErrorCode()).isEqualTo("GITHUB_API_ERROR");
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getMessage()).contains("GitHub authorization failed");
    }
    
    @Test
    void handleGitHubError_WithRateLimitException_ReturnsRateLimitException() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.add("Retry-After", "60");
        WebClientResponseException exception = WebClientResponseException.create(
            429, "Too Many Requests", headers, new byte[0], null);
        
        // When
        GitHubApiException result = errorHandler.handleGitHubError(exception, "test context");
        
        // Then
        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result.getErrorCode()).isEqualTo("GITHUB_RATE_LIMIT");
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getRetryAfter()).isEqualTo(Duration.ofSeconds(60));
        assertThat(result.getMessage()).contains("GitHub rate limit exceeded");
    }
    
    @Test
    void handleGitHubError_WithServerError_ReturnsRetryableException() {
        // Given
        WebClientResponseException exception = WebClientResponseException.create(
            500, "Internal Server Error", HttpHeaders.EMPTY, new byte[0], null);
        
        // When
        GitHubApiException result = errorHandler.handleGitHubError(exception, "test context");
        
        // Then
        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.isRetryable()).isTrue();
    }
    
    @Test
    void handleGitHubError_WithNetworkException_ReturnsNetworkException() {
        // Given
        ConnectException networkException = new ConnectException("Connection refused");
        
        // When
        GitHubApiException result = errorHandler.handleGitHubError(networkException, "test context");
        
        // Then
        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("Network error while accessing GitHub API");
    }
    
    @Test
    void handleGitHubError_WithCompletionException_UnwrapsAndHandles() {
        // Given
        WebClientResponseException webClientException = WebClientResponseException.create(
            401, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null);
        CompletionException completionException = new CompletionException(webClientException);
        
        // When
        GitHubApiException result = errorHandler.handleGitHubError(completionException, "test context");
        
        // Then
        assertThat(result.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(result.isRetryable()).isFalse();
    }
    
    @Test
    void handleFirestoreError_WithUnauthenticatedException_ReturnsAuthenticationException() {
        // Given
        RuntimeException exception = new RuntimeException("Unauthenticated authentication failed");
        
        // When
        FirestoreException result = errorHandler.handleFirestoreError(exception, "test context");
        
        // Then
        assertThat(result.getErrorType()).isEqualTo(FirestoreException.FirestoreErrorType.AUTHENTICATION_FAILED);
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getMessage()).contains("Authentication failed for Firestore");
    }
    
    @Test
    void handleFirestoreError_WithPermissionDeniedException_ReturnsPermissionException() {
        // Given
        RuntimeException exception = new RuntimeException("Permission denied access");
        
        // When
        FirestoreException result = errorHandler.handleFirestoreError(exception, "test context");
        
        // Then
        assertThat(result.getErrorType()).isEqualTo(FirestoreException.FirestoreErrorType.PERMISSION_DENIED);
        assertThat(result.isRetryable()).isFalse();
        assertThat(result.getMessage()).contains("Permission denied");
    }
    
    @Test
    void handleFirestoreError_WithResourceExhaustedException_ReturnsQuotaException() {
        // Given
        RuntimeException exception = new RuntimeException("Quota exceeded resource exhausted");
        
        // When
        FirestoreException result = errorHandler.handleFirestoreError(exception, "test context");
        
        // Then
        assertThat(result.getErrorType()).isEqualTo(FirestoreException.FirestoreErrorType.QUOTA_EXCEEDED);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("quota");
    }
    
    @Test
    void handleFirestoreError_WithUnavailableException_ReturnsNetworkException() {
        // Given
        RuntimeException exception = new RuntimeException("Service unavailable network error");
        
        // When
        FirestoreException result = errorHandler.handleFirestoreError(exception, "test context");
        
        // Then
        assertThat(result.getErrorType()).isEqualTo(FirestoreException.FirestoreErrorType.NETWORK_ERROR);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("unavailable");
    }
    
    @Test
    void handleFirestoreError_WithNetworkException_ReturnsNetworkException() {
        // Given
        SocketTimeoutException networkException = new SocketTimeoutException("Read timeout");
        
        // When
        FirestoreException result = errorHandler.handleFirestoreError(networkException, "test context");
        
        // Then
        assertThat(result.getErrorType()).isEqualTo(FirestoreException.FirestoreErrorType.NETWORK_ERROR);
        assertThat(result.isRetryable()).isTrue();
        assertThat(result.getMessage()).contains("Network error while accessing Firestore");
    }
    
    @Test
    void handleSyncError_WithConnectorException_WrapsSyncException() {
        // Given
        GitHubApiException gitHubException = GitHubApiException.authenticationFailed("Auth failed", null);
        
        // When
        SyncException result = errorHandler.handleSyncError(gitHubException, "test context");
        
        // Then
        assertThat(result.getErrorCode()).isEqualTo("SYNC_ERROR");
        assertThat(result.getMessage()).contains("Sync operation failed");
        assertThat(result.getCause()).isEqualTo(gitHubException);
    }
    
    @Test
    void handleSyncError_WithIllegalArgumentException_ReturnsValidationException() {
        // Given
        IllegalArgumentException validationException = new IllegalArgumentException("Invalid parameter");
        
        // When
        SyncException result = errorHandler.handleSyncError(validationException, "test context");
        
        // Then
        assertThat(result.getErrorCode()).isEqualTo("SYNC_ERROR");
        assertThat(result.getMessage()).contains("Sync validation failed");
        assertThat(result.getCause()).isEqualTo(validationException);
    }
    
    @Test
    void logStructuredError_WithRetryableException_LogsAsWarning() {
        // Given
        GitHubApiException retryableException = new GitHubApiException(
            "Server error", HttpStatus.INTERNAL_SERVER_ERROR);
        
        // When/Then - This test verifies the method doesn't throw exceptions
        // In a real scenario, you would verify log output using a test appender
        errorHandler.logStructuredError(retryableException, "test operation", "test context");
        
        // Verify the exception properties
        assertThat(retryableException.isRetryable()).isTrue();
        assertThat(retryableException.getErrorCode()).isEqualTo("GITHUB_API_ERROR");
    }
    
    @Test
    void logStructuredError_WithNonRetryableException_LogsAsError() {
        // Given
        GitHubApiException nonRetryableException = GitHubApiException.authenticationFailed("Auth failed", null);
        
        // When/Then - This test verifies the method doesn't throw exceptions
        errorHandler.logStructuredError(nonRetryableException, "test operation", "test context");
        
        // Verify the exception properties
        assertThat(nonRetryableException.isRetryable()).isFalse();
        assertThat(nonRetryableException.getErrorCode()).isEqualTo("GITHUB_API_ERROR");
    }
}