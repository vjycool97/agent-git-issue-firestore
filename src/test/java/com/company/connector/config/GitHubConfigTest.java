package com.company.connector.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GitHubConfig Validation Tests")
class GitHubConfigTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should create valid GitHubConfig with all required fields")
    void shouldCreateValidGitHubConfig() {
        // Given
        var retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofSeconds(1));
        var rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        var config = new GitHubConfig(
            "ghp_test_token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );

        // When
        Set<ConstraintViolation<GitHubConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when token is blank")
    void shouldFailValidationWhenTokenIsBlank() {
        // Given
        var retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofSeconds(1));
        var rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        var config = new GitHubConfig(
            "",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );

        // When
        Set<ConstraintViolation<GitHubConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("GitHub token is required");
    }

    @Test
    @DisplayName("Should fail validation when API URL is blank")
    void shouldFailValidationWhenApiUrlIsBlank() {
        // Given
        var retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofSeconds(1));
        var rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        var config = new GitHubConfig(
            "ghp_test_token",
            "",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );

        // When
        Set<ConstraintViolation<GitHubConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("GitHub API URL is required");
    }

    @Test
    @DisplayName("Should fail validation when timeout is null")
    void shouldFailValidationWhenTimeoutIsNull() {
        // Given
        var retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofSeconds(1));
        var rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        var config = new GitHubConfig(
            "ghp_test_token",
            "https://api.github.com",
            null,
            retryConfig,
            rateLimitConfig
        );

        // When
        Set<ConstraintViolation<GitHubConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Timeout duration is required");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 11})
    @DisplayName("Should fail validation when retry max attempts is out of range")
    void shouldFailValidationWhenRetryMaxAttemptsIsOutOfRange(int maxAttempts) {
        // Given
        var retryConfig = new GitHubConfig.RetryConfig(maxAttempts, Duration.ofSeconds(1));
        var rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        var config = new GitHubConfig(
            "ghp_test_token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );

        // When
        Set<ConstraintViolation<GitHubConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        var violation = violations.iterator().next();
        assertThat(violation.getMessage())
            .matches("Max attempts must be at least 1|Max attempts cannot exceed 10");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 10001})
    @DisplayName("Should fail validation when requests per hour is out of range")
    void shouldFailValidationWhenRequestsPerHourIsOutOfRange(int requestsPerHour) {
        // Given
        var retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofSeconds(1));
        var rateLimitConfig = new GitHubConfig.RateLimitConfig(requestsPerHour, 100);
        var config = new GitHubConfig(
            "ghp_test_token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );

        // When
        Set<ConstraintViolation<GitHubConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        var violation = violations.iterator().next();
        assertThat(violation.getMessage())
            .matches("Requests per hour must be at least 1|Requests per hour cannot exceed 10000");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1001})
    @DisplayName("Should fail validation when burst capacity is out of range")
    void shouldFailValidationWhenBurstCapacityIsOutOfRange(int burstCapacity) {
        // Given
        var retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofSeconds(1));
        var rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, burstCapacity);
        var config = new GitHubConfig(
            "ghp_test_token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            rateLimitConfig
        );

        // When
        Set<ConstraintViolation<GitHubConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        var violation = violations.iterator().next();
        assertThat(violation.getMessage())
            .matches("Burst capacity must be at least 1|Burst capacity cannot exceed 1000");
    }

    @Test
    @DisplayName("Should fail validation when retry config is null")
    void shouldFailValidationWhenRetryConfigIsNull() {
        // Given
        var rateLimitConfig = new GitHubConfig.RateLimitConfig(5000, 100);
        var config = new GitHubConfig(
            "ghp_test_token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            null,
            rateLimitConfig
        );

        // When
        Set<ConstraintViolation<GitHubConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Retry configuration is required");
    }

    @Test
    @DisplayName("Should fail validation when rate limit config is null")
    void shouldFailValidationWhenRateLimitConfigIsNull() {
        // Given
        var retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofSeconds(1));
        var config = new GitHubConfig(
            "ghp_test_token",
            "https://api.github.com",
            Duration.ofSeconds(30),
            retryConfig,
            null
        );

        // When
        Set<ConstraintViolation<GitHubConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Rate limit configuration is required");
    }
}