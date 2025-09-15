package com.company.connector.exception;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/**
 * Exception thrown when GitHub API operations fail.
 * 
 * This exception encapsulates various GitHub API error scenarios including
 * authentication failures, rate limiting, network errors, and server errors.
 */
public class GitHubApiException extends ConnectorException {
    
    private final HttpStatus httpStatus;
    private final Duration retryAfter;
    
    /**
     * Constructs a new GitHub API exception with the specified message and HTTP status.
     * 
     * @param message the detail message
     * @param httpStatus the HTTP status code from the GitHub API response
     */
    public GitHubApiException(String message, HttpStatus httpStatus) {
        super("GITHUB_API_ERROR", message);
        this.httpStatus = httpStatus;
        this.retryAfter = null;
    }
    
    /**
     * Constructs a new GitHub API exception with the specified message, HTTP status, and cause.
     * 
     * @param message the detail message
     * @param httpStatus the HTTP status code from the GitHub API response
     * @param cause the cause of the exception
     */
    public GitHubApiException(String message, HttpStatus httpStatus, Throwable cause) {
        super("GITHUB_API_ERROR", message, cause);
        this.httpStatus = httpStatus;
        this.retryAfter = null;
    }
    
    /**
     * Constructs a new GitHub API exception for rate limiting scenarios.
     * 
     * @param message the detail message
     * @param httpStatus the HTTP status code (should be 429)
     * @param retryAfter the duration to wait before retrying
     * @param cause the cause of the exception
     */
    public GitHubApiException(String message, HttpStatus httpStatus, Duration retryAfter, Throwable cause) {
        super("GITHUB_RATE_LIMIT", message, cause);
        this.httpStatus = httpStatus;
        this.retryAfter = retryAfter;
    }
    
    /**
     * Returns the HTTP status code from the GitHub API response.
     * 
     * @return the HTTP status code
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
    
    /**
     * Returns the retry-after duration for rate limiting scenarios.
     * 
     * @return the retry-after duration, or null if not applicable
     */
    public Duration getRetryAfter() {
        return retryAfter;
    }
    
    /**
     * Determines if this exception represents a retryable error.
     * 
     * @return true for server errors (5xx) and rate limiting (429), false for client errors (4xx)
     */
    @Override
    public boolean isRetryable() {
        if (httpStatus == null) {
            return false;
        }
        
        // Rate limiting is retryable
        if (httpStatus == HttpStatus.TOO_MANY_REQUESTS) {
            return true;
        }
        
        // Server errors are retryable
        if (httpStatus.is5xxServerError()) {
            return true;
        }
        
        // Client errors are generally not retryable
        return false;
    }
    
    /**
     * Creates a GitHub API exception for authentication failures.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @return a new GitHubApiException for authentication failures
     */
    public static GitHubApiException authenticationFailed(String message, Throwable cause) {
        return new GitHubApiException(message, HttpStatus.UNAUTHORIZED, cause);
    }
    
    /**
     * Creates a GitHub API exception for authorization failures.
     * 
     * @param message the detail message
     * @param cause the cause of the exception
     * @return a new GitHubApiException for authorization failures
     */
    public static GitHubApiException authorizationFailed(String message, Throwable cause) {
        return new GitHubApiException(message, HttpStatus.FORBIDDEN, cause);
    }
    
    /**
     * Creates a GitHub API exception for rate limiting scenarios.
     * 
     * @param message the detail message
     * @param retryAfter the duration to wait before retrying
     * @param cause the cause of the exception
     * @return a new GitHubApiException for rate limiting
     */
    public static GitHubApiException rateLimitExceeded(String message, Duration retryAfter, Throwable cause) {
        return new GitHubApiException(message, HttpStatus.TOO_MANY_REQUESTS, retryAfter, cause);
    }
}