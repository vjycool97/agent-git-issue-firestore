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

@DisplayName("FirebaseConfig Validation Tests")
class FirebaseConfigTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should create valid FirebaseConfig with all required fields")
    void shouldCreateValidFirebaseConfig() {
        // Given
        var connectionPoolConfig = new FirebaseConfig.ConnectionPoolConfig(50, Duration.ofSeconds(10));
        var config = new FirebaseConfig(
            "/path/to/service-account.json",
            "test-project-id",
            "github_issues",
            connectionPoolConfig
        );

        // When
        Set<ConstraintViolation<FirebaseConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should fail validation when service account path is blank")
    void shouldFailValidationWhenServiceAccountPathIsBlank() {
        // Given
        var connectionPoolConfig = new FirebaseConfig.ConnectionPoolConfig(50, Duration.ofSeconds(10));
        var config = new FirebaseConfig(
            "",
            "test-project-id",
            "github_issues",
            connectionPoolConfig
        );

        // When
        Set<ConstraintViolation<FirebaseConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Firebase service account path is required");
    }

    @Test
    @DisplayName("Should fail validation when project ID is blank")
    void shouldFailValidationWhenProjectIdIsBlank() {
        // Given
        var connectionPoolConfig = new FirebaseConfig.ConnectionPoolConfig(50, Duration.ofSeconds(10));
        var config = new FirebaseConfig(
            "/path/to/service-account.json",
            "",
            "github_issues",
            connectionPoolConfig
        );

        // When
        Set<ConstraintViolation<FirebaseConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Firebase project ID is required");
    }

    @Test
    @DisplayName("Should fail validation when collection name is blank")
    void shouldFailValidationWhenCollectionNameIsBlank() {
        // Given
        var connectionPoolConfig = new FirebaseConfig.ConnectionPoolConfig(50, Duration.ofSeconds(10));
        var config = new FirebaseConfig(
            "/path/to/service-account.json",
            "test-project-id",
            "",
            connectionPoolConfig
        );

        // When
        Set<ConstraintViolation<FirebaseConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Firestore collection name is required");
    }

    @Test
    @DisplayName("Should fail validation when connection pool config is null")
    void shouldFailValidationWhenConnectionPoolConfigIsNull() {
        // Given
        var config = new FirebaseConfig(
            "/path/to/service-account.json",
            "test-project-id",
            "github_issues",
            null
        );

        // When
        Set<ConstraintViolation<FirebaseConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Connection pool configuration is required");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 201})
    @DisplayName("Should fail validation when max connections is out of range")
    void shouldFailValidationWhenMaxConnectionsIsOutOfRange(int maxConnections) {
        // Given
        var connectionPoolConfig = new FirebaseConfig.ConnectionPoolConfig(maxConnections, Duration.ofSeconds(10));
        var config = new FirebaseConfig(
            "/path/to/service-account.json",
            "test-project-id",
            "github_issues",
            connectionPoolConfig
        );

        // When
        Set<ConstraintViolation<FirebaseConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        var violation = violations.iterator().next();
        assertThat(violation.getMessage())
            .matches("Max connections must be at least 1|Max connections cannot exceed 200");
    }

    @Test
    @DisplayName("Should fail validation when connection timeout is null")
    void shouldFailValidationWhenConnectionTimeoutIsNull() {
        // Given
        var connectionPoolConfig = new FirebaseConfig.ConnectionPoolConfig(50, null);
        var config = new FirebaseConfig(
            "/path/to/service-account.json",
            "test-project-id",
            "github_issues",
            connectionPoolConfig
        );

        // When
        Set<ConstraintViolation<FirebaseConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Connection timeout is required");
    }

    @Test
    @DisplayName("Should validate nested connection pool configuration")
    void shouldValidateNestedConnectionPoolConfiguration() {
        // Given - Valid connection pool config
        var validConnectionPoolConfig = new FirebaseConfig.ConnectionPoolConfig(100, Duration.ofSeconds(5));

        // When
        Set<ConstraintViolation<FirebaseConfig.ConnectionPoolConfig>> violations = 
            validator.validate(validConnectionPoolConfig);

        // Then
        assertThat(violations).isEmpty();
    }
}