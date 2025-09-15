package com.company.connector.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GitHubApiException.
 */
class GitHubApiExceptionTest {
    
    @Test
    void constructor_WithMessageAndStatus_SetsProperties() {
        // Given
        String message = "API error";
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        
        // When
        GitHubApiException exception = new GitHubApiException(message, status);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getHttpStatus()).isEqualTo(status);
        assertThat(exception.getErrorCode()).isEqualTo("GITHUB_API_ERROR");
        assertThat(exception.getRetryAfter()).isNull();
    }
    
    @Test
    void constructor_WithMessageStatusAndCause_SetsProperties() {
        // Given
        String message = "API error";
        HttpStatus status = HttpStatus.BAD_REQUEST;
        RuntimeException cause = new RuntimeException("Root cause");
        
        // When
        GitHubApiException exception = new GitHubApiException(message, status, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getHttpStatus()).isEqualTo(status);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.getRetryAfter()).isNull();
    }
    
    @Test
    void constructor_WithRateLimitParameters_SetsRetryAfter() {
        // Given
        String message = "Rate limit exceeded";
        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        Duration retryAfter = Duration.ofMinutes(1);
        RuntimeException cause = new RuntimeException("Rate limit");
        
        // When
        GitHubApiException exception = new GitHubApiException(message, status, retryAfter, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getHttpStatus()).isEqualTo(status);
        assertThat(exception.getRetryAfter()).isEqualTo(retryAfter);
        assertThat(exception.getErrorCode()).isEqualTo("GITHUB_RATE_LIMIT");
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    @Test
    void isRetryable_WithServerError_ReturnsTrue() {
        // Given
        GitHubApiException exception = new GitHubApiException(
            "Server error", HttpStatus.INTERNAL_SERVER_ERROR);
        
        // When/Then
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void isRetryable_WithRateLimit_ReturnsTrue() {
        // Given
        GitHubApiException exception = new GitHubApiException(
            "Rate limit", HttpStatus.TOO_MANY_REQUESTS, Duration.ofMinutes(1), null);
        
        // When/Then
        assertThat(exception.isRetryable()).isTrue();
    }
    
    @Test
    void isRetryable_WithUnauthorized_ReturnsFalse() {
        // Given
        GitHubApiException exception = new GitHubApiException(
            "Unauthorized", HttpStatus.UNAUTHORIZED);
        
        // When/Then
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void isRetryable_WithForbidden_ReturnsFalse() {
        // Given
        GitHubApiException exception = new GitHubApiException(
            "Forbidden", HttpStatus.FORBIDDEN);
        
        // When/Then
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void isRetryable_WithBadRequest_ReturnsFalse() {
        // Given
        GitHubApiException exception = new GitHubApiException(
            "Bad request", HttpStatus.BAD_REQUEST);
        
        // When/Then
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void isRetryable_WithNullStatus_ReturnsFalse() {
        // Given
        GitHubApiException exception = new GitHubApiException("Error", null);
        
        // When/Then
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void authenticationFailed_CreatesCorrectException() {
        // Given
        String message = "Auth failed";
        RuntimeException cause = new RuntimeException("Token invalid");
        
        // When
        GitHubApiException exception = GitHubApiException.authenticationFailed(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void authorizationFailed_CreatesCorrectException() {
        // Given
        String message = "Authorization failed";
        RuntimeException cause = new RuntimeException("Insufficient permissions");
        
        // When
        GitHubApiException exception = GitHubApiException.authorizationFailed(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.isRetryable()).isFalse();
    }
    
    @Test
    void rateLimitExceeded_CreatesCorrectException() {
        // Given
        String message = "Rate limit exceeded";
        Duration retryAfter = Duration.ofSeconds(30);
        RuntimeException cause = new RuntimeException("Too many requests");
        
        // When
        GitHubApiException exception = GitHubApiException.rateLimitExceeded(message, retryAfter, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getHttpStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exception.getRetryAfter()).isEqualTo(retryAfter);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception.isRetryable()).isTrue();
        assertThat(exception.getErrorCode()).isEqualTo("GITHUB_RATE_LIMIT");
    }
}