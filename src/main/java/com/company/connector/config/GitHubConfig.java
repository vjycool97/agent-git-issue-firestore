package com.company.connector.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for GitHub API integration.
 * Uses Java Records for immutable configuration with validation.
 */
@ConfigurationProperties(prefix = "github")
@Validated
public record GitHubConfig(
        @NotBlank(message = "GitHub token is required")
        String token,
        
        @NotBlank(message = "GitHub API URL is required")
        String apiUrl,
        
        @NotNull(message = "Timeout duration is required")
        Duration timeout,
        
        @Valid
        @NotNull(message = "Retry configuration is required")
        RetryConfig retry,
        
        @Valid
        @NotNull(message = "Rate limit configuration is required")
        RateLimitConfig rateLimit
) {
    
    /**
     * Retry configuration for GitHub API calls.
     */
    public record RetryConfig(
            @Min(value = 1, message = "Max attempts must be at least 1")
            @Max(value = 10, message = "Max attempts cannot exceed 10")
            int maxAttempts,
            
            @NotNull(message = "Backoff delay is required")
            Duration backoffDelay
    ) {}
    
    /**
     * Rate limiting configuration for GitHub API.
     */
    public record RateLimitConfig(
            @Min(value = 1, message = "Requests per hour must be at least 1")
            @Max(value = 10000, message = "Requests per hour cannot exceed 10000")
            int requestsPerHour,
            
            @Min(value = 1, message = "Burst capacity must be at least 1")
            @Max(value = 1000, message = "Burst capacity cannot exceed 1000")
            int burstCapacity
    ) {}
}