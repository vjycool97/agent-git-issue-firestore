package com.company.connector.client;

import com.company.connector.config.GitHubConfig;
import com.company.connector.service.ErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Basic tests for GitHubClient interface and parameter validation.
 */
class GitHubClientBasicTest {
    
    @Mock
    private RetryTemplate retryTemplate;
    
    @Mock
    private ErrorHandler errorHandler;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    void gitHubClientImpl_Creation_Success() {
        // Arrange
        GitHubConfig.RetryConfig retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofMillis(100));
        GitHubConfig.RateLimitConfig rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        GitHubConfig config = new GitHubConfig(
            "test-token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );
        
        // Act & Assert
        assertThatCode(() -> new GitHubClientImpl(config, retryTemplate, errorHandler))
            .doesNotThrowAnyException();
    }
    
    @Test
    void fetchRecentIssues_InvalidOwner_ThrowsIllegalArgumentException() {
        // Arrange
        GitHubConfig.RetryConfig retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofMillis(100));
        GitHubConfig.RateLimitConfig rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        GitHubConfig config = new GitHubConfig(
            "test-token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );
        GitHubClient client = new GitHubClientImpl(config, retryTemplate, errorHandler);
        
        // Act & Assert
        assertThatThrownBy(() -> client.fetchRecentIssues(null, "repo", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Repository owner cannot be null or blank");
        
        assertThatThrownBy(() -> client.fetchRecentIssues("", "repo", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Repository owner cannot be null or blank");
        
        assertThatThrownBy(() -> client.fetchRecentIssues("   ", "repo", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Repository owner cannot be null or blank");
    }
    
    @Test
    void fetchRecentIssues_InvalidRepo_ThrowsIllegalArgumentException() {
        // Arrange
        GitHubConfig.RetryConfig retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofMillis(100));
        GitHubConfig.RateLimitConfig rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        GitHubConfig config = new GitHubConfig(
            "test-token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );
        GitHubClient client = new GitHubClientImpl(config, retryTemplate, errorHandler);
        
        // Act & Assert
        assertThatThrownBy(() -> client.fetchRecentIssues("owner", null, 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Repository name cannot be null or blank");
        
        assertThatThrownBy(() -> client.fetchRecentIssues("owner", "", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Repository name cannot be null or blank");
    }
    
    @Test
    void fetchRecentIssues_InvalidLimit_ThrowsIllegalArgumentException() {
        // Arrange
        GitHubConfig.RetryConfig retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofMillis(100));
        GitHubConfig.RateLimitConfig rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        GitHubConfig config = new GitHubConfig(
            "test-token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );
        GitHubClient client = new GitHubClientImpl(config, retryTemplate, errorHandler);
        
        // Act & Assert
        assertThatThrownBy(() -> client.fetchRecentIssues("owner", "repo", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit must be between 1 and 100");
        
        assertThatThrownBy(() -> client.fetchRecentIssues("owner", "repo", 101))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Limit must be between 1 and 100");
    }
    
    @Test
    void fetchRecentIssues_DefaultLimit_UsesDefaultMethod() {
        // Arrange
        GitHubConfig.RetryConfig retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofMillis(100));
        GitHubConfig.RateLimitConfig rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        GitHubConfig config = new GitHubConfig(
            "test-token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );
        GitHubClient client = new GitHubClientImpl(config, retryTemplate, errorHandler);
        
        // Act & Assert - Should not throw exception for valid parameters
        assertThatCode(() -> client.fetchRecentIssues("owner", "repo"))
            .doesNotThrowAnyException();
    }
}