package com.company.connector.config;

import com.company.connector.exception.ConnectorException;
import com.company.connector.exception.FirestoreException;
import com.company.connector.exception.GitHubApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for retry mechanisms across the application.
 * 
 * This configuration provides centralized retry policies for different
 * types of operations, with appropriate backoff strategies and retry limits.
 */
@Configuration
@EnableRetry
public class RetryConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryConfig.class);
    
    /**
     * Creates a retry template for GitHub API operations.
     * 
     * @return configured RetryTemplate for GitHub operations
     */
    @Bean("gitHubRetryTemplate")
    public RetryTemplate gitHubRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Configure retry policy
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(GitHubApiException.class, true);
        retryableExceptions.put(RuntimeException.class, true);
        
        Map<Class<? extends Throwable>, Boolean> nonRetryableExceptions = new HashMap<>();
        nonRetryableExceptions.put(IllegalArgumentException.class, false);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true, true);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Configure exponential backoff
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000); // 1 second
        backOffPolicy.setMultiplier(2.0);
        backOffPolicy.setMaxInterval(30000); // 30 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        // Add retry listener for logging
        retryTemplate.registerListener(new GitHubRetryListener());
        
        return retryTemplate;
    }
    
    /**
     * Creates a retry template for Firestore operations.
     * 
     * @return configured RetryTemplate for Firestore operations
     */
    @Bean("firestoreRetryTemplate")
    public RetryTemplate firestoreRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Configure retry policy with custom logic for Firestore exceptions
        retryTemplate.setRetryPolicy(new FirestoreRetryPolicy());
        
        // Configure exponential backoff with jitter
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500); // 500ms
        backOffPolicy.setMultiplier(1.5);
        backOffPolicy.setMaxInterval(10000); // 10 seconds
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        // Add retry listener for logging
        retryTemplate.registerListener(new FirestoreRetryListener());
        
        return retryTemplate;
    }
    
    /**
     * Creates a retry template for general sync operations.
     * 
     * @return configured RetryTemplate for sync operations
     */
    @Bean("syncRetryTemplate")
    public RetryTemplate syncRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Configure retry policy for sync operations
        retryTemplate.setRetryPolicy(new ConnectorRetryPolicy());
        
        // Configure linear backoff for sync operations
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(2000); // 2 seconds
        backOffPolicy.setMultiplier(1.0); // Linear backoff
        backOffPolicy.setMaxInterval(2000); // Keep it constant
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        // Add retry listener for logging
        retryTemplate.registerListener(new SyncRetryListener());
        
        return retryTemplate;
    }
    
    /**
     * Custom retry policy for Firestore operations.
     */
    private static class FirestoreRetryPolicy extends SimpleRetryPolicy {
        
        public FirestoreRetryPolicy() {
            super(3); // Max 3 attempts
        }
        
        @Override
        public boolean canRetry(RetryContext context) {
            Throwable lastThrowable = context.getLastThrowable();
            
            if (lastThrowable instanceof FirestoreException firestoreException) {
                return firestoreException.isRetryable() && super.canRetry(context);
            }
            
            return super.canRetry(context);
        }
    }
    
    /**
     * Custom retry policy for connector operations.
     */
    private static class ConnectorRetryPolicy extends SimpleRetryPolicy {
        
        public ConnectorRetryPolicy() {
            super(2); // Max 2 attempts for sync operations
        }
        
        @Override
        public boolean canRetry(RetryContext context) {
            Throwable lastThrowable = context.getLastThrowable();
            
            if (lastThrowable instanceof ConnectorException connectorException) {
                return connectorException.isRetryable() && super.canRetry(context);
            }
            
            return super.canRetry(context);
        }
    }
    
    /**
     * Retry listener for GitHub operations.
     */
    private static class GitHubRetryListener implements RetryListener {
        
        @Override
        public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            logger.warn("GitHub operation failed (attempt {}): {}", 
                       context.getRetryCount(), throwable.getMessage());
            
            if (throwable instanceof GitHubApiException gitHubException) {
                Duration retryAfter = gitHubException.getRetryAfter();
                if (retryAfter != null) {
                    logger.info("Rate limit detected, will retry after {} seconds", retryAfter.getSeconds());
                }
            }
        }
    }
    
    /**
     * Retry listener for Firestore operations.
     */
    private static class FirestoreRetryListener implements RetryListener {
        
        @Override
        public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            logger.warn("Firestore operation failed (attempt {}): {}", 
                       context.getRetryCount(), throwable.getMessage());
        }
    }
    
    /**
     * Retry listener for sync operations.
     */
    private static class SyncRetryListener implements RetryListener {
        
        @Override
        public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            logger.warn("Sync operation failed (attempt {}): {}", 
                       context.getRetryCount(), throwable.getMessage());
        }
    }
}