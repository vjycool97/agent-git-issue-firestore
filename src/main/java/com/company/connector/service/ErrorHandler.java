package com.company.connector.service;

import com.company.connector.exception.ConnectorException;
import com.company.connector.exception.GitHubApiException;
import com.company.connector.exception.SyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.concurrent.CompletionException;

/**
 * Centralized error handler for the connector application.
 * 
 * This component provides consistent error handling and exception translation
 * across all layers of the application, converting low-level exceptions into
 * appropriate domain-specific exceptions.
 */
@Component
public class ErrorHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);
    
    /**
     * Handles GitHub API related errors and converts them to appropriate exceptions.
     * 
     * @param throwable the original exception
     * @param context additional context for error logging
     * @return a GitHubApiException with appropriate error details
     */
    public GitHubApiException handleGitHubError(Throwable throwable, String context) {
        logger.error("GitHub API error in context '{}': {}", context, throwable.getMessage(), throwable);
        
        if (throwable instanceof WebClientResponseException webClientException) {
            return handleWebClientException(webClientException, context);
        }
        
        if (throwable instanceof CompletionException completionException && 
            completionException.getCause() instanceof WebClientResponseException webClientException) {
            return handleWebClientException(webClientException, context);
        }
        
        // Handle other network-related exceptions
        if (isNetworkException(throwable)) {
            return new GitHubApiException(
                "Network error while accessing GitHub API: " + throwable.getMessage(),
                HttpStatus.SERVICE_UNAVAILABLE,
                throwable
            );
        }
        
        // Default to server error for unknown exceptions
        return new GitHubApiException(
            "Unexpected error while accessing GitHub API: " + throwable.getMessage(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            throwable
        );
    }
    
    /**
     * Handles Firestore related errors and converts them to appropriate exceptions.
     * 
     * @param throwable the original exception
     * @param context additional context for error logging
     * @return a FirestoreException with appropriate error details
     */
    public com.company.connector.exception.FirestoreException handleFirestoreError(Throwable throwable, String context) {
        logger.error("Firestore error in context '{}': {}", context, throwable.getMessage(), throwable);
        
        if (throwable instanceof com.google.cloud.firestore.FirestoreException firestoreException) {
            return handleFirestoreException(firestoreException, context);
        }
        
        if (throwable instanceof CompletionException completionException && 
            completionException.getCause() instanceof com.google.cloud.firestore.FirestoreException firestoreException) {
            return handleFirestoreException(firestoreException, context);
        }
        
        // Handle authentication exceptions
        if (isAuthenticationException(throwable)) {
            return com.company.connector.exception.FirestoreException.authenticationFailed(
                "Authentication failed for Firestore: " + throwable.getMessage(),
                throwable
            );
        }
        
        // Handle network exceptions
        if (isNetworkException(throwable)) {
            return com.company.connector.exception.FirestoreException.networkError(
                "Network error while accessing Firestore: " + throwable.getMessage(),
                throwable
            );
        }
        
        // Default to unknown error
        return new com.company.connector.exception.FirestoreException(
            com.company.connector.exception.FirestoreException.FirestoreErrorType.UNKNOWN_ERROR,
            "Unexpected Firestore error: " + throwable.getMessage(),
            throwable
        );
    }
    
    /**
     * Handles sync operation errors and converts them to appropriate exceptions.
     * 
     * @param throwable the original exception
     * @param context additional context for error logging
     * @return a SyncException with appropriate error details
     */
    public SyncException handleSyncError(Throwable throwable, String context) {
        logger.error("Sync error in context '{}': {}", context, throwable.getMessage(), throwable);
        
        // If it's already a connector exception, wrap it in a sync exception
        if (throwable instanceof ConnectorException connectorException) {
            return new SyncException(
                "Sync operation failed: " + connectorException.getMessage(),
                connectorException
            );
        }
        
        // Handle validation exceptions
        if (throwable instanceof IllegalArgumentException) {
            return new SyncException(
                "Sync validation failed: " + throwable.getMessage(),
                throwable
            );
        }
        
        // Default sync exception
        return new SyncException(
            "Sync operation failed: " + throwable.getMessage(),
            throwable
        );
    }
    
    /**
     * Logs error details with structured information for monitoring and debugging.
     * 
     * @param exception the exception to log
     * @param operation the operation that failed
     * @param additionalContext additional context information
     */
    public void logStructuredError(ConnectorException exception, String operation, String additionalContext) {
        String errorDetails = String.format("""
            === ERROR REPORT ===
            Operation: %s
            Error Code: %s
            Error Type: %s
            Retryable: %s
            Context: %s
            Message: %s
            Timestamp: %s
            Thread: %s
            ===================
            """,
            operation,
            exception.getErrorCode(),
            exception.getClass().getSimpleName(),
            exception.isRetryable(),
            additionalContext,
            exception.getMessage(),
            java.time.Instant.now(),
            Thread.currentThread().getName()
        );
        
        if (exception.isRetryable()) {
            logger.warn("RETRYABLE_ERROR: {}", errorDetails);
        } else {
            logger.error("FATAL_ERROR: {}", errorDetails);
        }
    }
    
    /**
     * Logs a clear troubleshooting message for common error scenarios.
     * 
     * @param exception the exception that occurred
     * @param operation the operation that failed
     */
    public void logTroubleshootingGuidance(ConnectorException exception, String operation) {
        String guidance = generateTroubleshootingGuidance(exception, operation);
        logger.info("TROUBLESHOOTING_GUIDANCE for {}: {}", operation, guidance);
    }
    
    private String generateTroubleshootingGuidance(ConnectorException exception, String operation) {
        return switch (exception) {
            case GitHubApiException gitHubException -> switch (gitHubException.getHttpStatus()) {
                case UNAUTHORIZED -> """
                    GitHub authentication failed. Please check:
                    1. Verify GITHUB_TOKEN environment variable is set
                    2. Ensure the token has not expired
                    3. Check token permissions for repository access
                    4. Test token manually: curl -H "Authorization: token YOUR_TOKEN" https://api.github.com/user
                    """;
                case FORBIDDEN -> """
                    GitHub authorization failed. Please check:
                    1. Verify the token has sufficient permissions
                    2. Check if the repository exists and is accessible
                    3. Ensure the token has 'repo' scope for private repositories
                    4. Verify organization permissions if applicable
                    """;
                case TOO_MANY_REQUESTS -> """
                    GitHub rate limit exceeded. Please:
                    1. Wait for the rate limit to reset (check X-RateLimit-Reset header)
                    2. Consider using a GitHub App instead of personal access token
                    3. Reduce sync frequency in configuration
                    4. Check if multiple instances are running with the same token
                    """;
                case NOT_FOUND -> """
                    GitHub resource not found. Please check:
                    1. Verify the repository owner and name are correct
                    2. Ensure the repository exists and is accessible
                    3. Check if the repository has been renamed or moved
                    4. Verify network connectivity to GitHub
                    """;
                default -> "Check GitHub API status and network connectivity. Review GitHub API documentation for error code: " + gitHubException.getHttpStatus();
            };
            case com.company.connector.exception.FirestoreException firestoreException -> switch (firestoreException.getErrorType()) {
                case AUTHENTICATION_FAILED -> """
                    Firestore authentication failed. Please check:
                    1. Verify FIREBASE_SERVICE_ACCOUNT_PATH points to valid service account JSON
                    2. Ensure the service account file has correct permissions (readable)
                    3. Verify FIREBASE_PROJECT_ID matches your Firebase project
                    4. Check if the service account has Firestore permissions
                    """;
                case PERMISSION_DENIED -> """
                    Firestore permission denied. Please check:
                    1. Verify service account has 'Cloud Datastore User' role
                    2. Check Firestore security rules allow writes to the collection
                    3. Ensure the Firebase project has Firestore enabled
                    4. Verify the collection name in configuration is correct
                    """;
                case QUOTA_EXCEEDED -> """
                    Firestore quota exceeded. Please:
                    1. Check Firebase console for quota usage
                    2. Consider upgrading to a higher Firebase plan
                    3. Reduce batch size in sync configuration
                    4. Implement exponential backoff for retries
                    """;
                case NETWORK_ERROR -> """
                    Firestore network error. Please check:
                    1. Verify internet connectivity
                    2. Check if firewall allows HTTPS traffic to Google APIs
                    3. Test connectivity: curl https://firestore.googleapis.com
                    4. Check for DNS resolution issues
                    """;
                default -> "Check Firestore service status and network connectivity. Review Firebase documentation for error details.";
            };
            case SyncException syncException -> """
                Sync operation failed. Please check:
                1. Review application logs for underlying cause
                2. Verify both GitHub and Firestore configurations
                3. Check network connectivity to both services
                4. Ensure sufficient system resources (memory, CPU)
                5. Consider reducing sync batch size or frequency
                """;
            default -> "Review application logs and configuration. Check service status for GitHub and Firebase.";
        };
    }
    
    /**
     * Handles WebClientResponseException and converts to GitHubApiException.
     */
    private GitHubApiException handleWebClientException(WebClientResponseException exception, String context) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        
        return switch (status) {
            case UNAUTHORIZED -> GitHubApiException.authenticationFailed(
                "GitHub authentication failed: Invalid or expired token",
                exception
            );
            case FORBIDDEN -> GitHubApiException.authorizationFailed(
                "GitHub authorization failed: Insufficient permissions",
                exception
            );
            case TOO_MANY_REQUESTS -> {
                Duration retryAfter = extractRetryAfter(exception);
                yield GitHubApiException.rateLimitExceeded(
                    "GitHub rate limit exceeded",
                    retryAfter,
                    exception
                );
            }
            case NOT_FOUND -> new GitHubApiException(
                "GitHub resource not found: " + context,
                status,
                exception
            );
            default -> new GitHubApiException(
                "GitHub API error: " + exception.getMessage(),
                status,
                exception
            );
        };
    }
    
    /**
     * Handles Firestore exceptions and converts to FirestoreException.
     */
    private com.company.connector.exception.FirestoreException handleFirestoreException(com.google.cloud.firestore.FirestoreException exception, String context) {
        String message = exception.getMessage();
        String lowerMessage = message != null ? message.toLowerCase() : "";
        
        // Check for specific error patterns in the message since getCode() returns int
        if (lowerMessage.contains("unauthenticated") || lowerMessage.contains("authentication")) {
            return com.company.connector.exception.FirestoreException.authenticationFailed(
                "Firestore authentication failed: " + exception.getMessage(),
                exception
            );
        }
        
        if (lowerMessage.contains("permission denied") || lowerMessage.contains("forbidden")) {
            return com.company.connector.exception.FirestoreException.permissionDenied(
                "Firestore permission denied: " + exception.getMessage(),
                exception
            );
        }
        
        if (lowerMessage.contains("quota") || lowerMessage.contains("resource exhausted")) {
            return com.company.connector.exception.FirestoreException.quotaExceeded(
                "Firestore quota exceeded: " + exception.getMessage(),
                exception
            );
        }
        
        if (lowerMessage.contains("unavailable") || lowerMessage.contains("deadline") || 
            lowerMessage.contains("timeout") || lowerMessage.contains("network")) {
            return com.company.connector.exception.FirestoreException.networkError(
                "Firestore network error: " + exception.getMessage(),
                exception
            );
        }
        
        if (lowerMessage.contains("invalid") || lowerMessage.contains("argument")) {
            return new com.company.connector.exception.FirestoreException(
                com.company.connector.exception.FirestoreException.FirestoreErrorType.INVALID_DOCUMENT,
                "Invalid Firestore document: " + exception.getMessage(),
                exception
            );
        }
        
        if (lowerMessage.contains("not found")) {
            return new com.company.connector.exception.FirestoreException(
                com.company.connector.exception.FirestoreException.FirestoreErrorType.COLLECTION_NOT_FOUND,
                "Firestore collection not found: " + exception.getMessage(),
                exception
            );
        }
        
        // Default to unknown error
        return new com.company.connector.exception.FirestoreException(
            com.company.connector.exception.FirestoreException.FirestoreErrorType.UNKNOWN_ERROR,
            "Unknown Firestore error: " + exception.getMessage(),
            exception
        );
    }
    
    /**
     * Extracts retry-after duration from WebClient exception headers.
     */
    private Duration extractRetryAfter(WebClientResponseException exception) {
        String retryAfterHeader = exception.getHeaders().getFirst("Retry-After");
        if (retryAfterHeader != null) {
            try {
                long seconds = Long.parseLong(retryAfterHeader);
                return Duration.ofSeconds(seconds);
            } catch (NumberFormatException e) {
                logger.warn("Invalid Retry-After header value: {}", retryAfterHeader);
            }
        }
        return Duration.ofMinutes(1); // Default retry after 1 minute
    }
    
    /**
     * Checks if the exception is network-related.
     */
    private boolean isNetworkException(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("connection") ||
               lowerMessage.contains("timeout") ||
               lowerMessage.contains("network") ||
               lowerMessage.contains("socket") ||
               throwable instanceof java.net.ConnectException ||
               throwable instanceof java.net.SocketTimeoutException ||
               throwable instanceof java.io.IOException;
    }
    
    /**
     * Checks if the exception is authentication-related.
     */
    private boolean isAuthenticationException(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null) {
            return false;
        }
        
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("authentication") ||
               lowerMessage.contains("credential") ||
               lowerMessage.contains("unauthorized") ||
               lowerMessage.contains("invalid token");
    }
}