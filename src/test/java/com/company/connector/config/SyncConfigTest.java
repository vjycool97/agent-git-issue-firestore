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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SyncConfig Validation Tests")
class SyncConfigTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should create valid SyncConfig with all required fields")
    void shouldCreateValidSyncConfig() {
        // Given
        var config = new SyncConfig(
            5,
            10,
            "0 */15 * * * *"
        );

        // When
        Set<ConstraintViolation<SyncConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    @DisplayName("Should fail validation when default issue limit is out of range")
    void shouldFailValidationWhenDefaultIssueLimitIsOutOfRange(int defaultIssueLimit) {
        // Given
        var config = new SyncConfig(
            defaultIssueLimit,
            10,
            "0 */15 * * * *"
        );

        // When
        Set<ConstraintViolation<SyncConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        var violation = violations.iterator().next();
        assertThat(violation.getMessage())
            .matches("Default issue limit must be at least 1|Default issue limit cannot exceed 100");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 51})
    @DisplayName("Should fail validation when batch size is out of range")
    void shouldFailValidationWhenBatchSizeIsOutOfRange(int batchSize) {
        // Given
        var config = new SyncConfig(
            5,
            batchSize,
            "0 */15 * * * *"
        );

        // When
        Set<ConstraintViolation<SyncConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        var violation = violations.iterator().next();
        assertThat(violation.getMessage())
            .matches("Batch size must be at least 1|Batch size cannot exceed 50");
    }

    @Test
    @DisplayName("Should fail validation when schedule is blank")
    void shouldFailValidationWhenScheduleIsBlank() {
        // Given
        var config = new SyncConfig(
            5,
            10,
            ""
        );

        // When
        Set<ConstraintViolation<SyncConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Schedule cron expression is required");
    }

    @Test
    @DisplayName("Should validate valid cron expressions")
    void shouldValidateValidCronExpressions() {
        // Given - Various valid cron expressions
        String[] validCronExpressions = {
            "0 */15 * * * *",     // Every 15 minutes
            "0 0 * * * *",        // Every hour
            "0 0 0 * * *",        // Every day at midnight
            "0 0 12 * * MON-FRI", // Every weekday at noon
            "0 30 9 * * *"        // Every day at 9:30 AM
        };

        for (String cronExpression : validCronExpressions) {
            // When
            var config = new SyncConfig(5, 10, cronExpression);
            Set<ConstraintViolation<SyncConfig>> violations = validator.validate(config);

            // Then
            assertThat(violations).isEmpty();
        }
    }

    @Test
    @DisplayName("Should throw exception for invalid cron expression format")
    void shouldThrowExceptionForInvalidCronExpressionFormat() {
        // Given - Invalid cron expressions (wrong number of fields)
        String[] invalidCronExpressions = {
            "0 */15 * * *",       // Only 5 fields (missing seconds)
            "0 */15 * * * * *",   // 7 fields (too many)
            "*/15 * * *",         // Only 4 fields
            "0"                   // Only 1 field
        };

        for (String cronExpression : invalidCronExpressions) {
            // When & Then
            assertThatThrownBy(() -> new SyncConfig(5, 10, cronExpression))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schedule must be a valid cron expression with 6 fields");
        }
    }

    @Test
    @DisplayName("Should handle null schedule in compact constructor")
    void shouldHandleNullScheduleInCompactConstructor() {
        // When & Then - null schedule should not throw exception in compact constructor
        // The @NotBlank validation will catch this during validation
        var config = new SyncConfig(5, 10, null);
        Set<ConstraintViolation<SyncConfig>> violations = validator.validate(config);
        
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Schedule cron expression is required");
    }

    @Test
    @DisplayName("Should handle whitespace-only schedule")
    void shouldHandleWhitespaceOnlySchedule() {
        // Given
        var config = new SyncConfig(5, 10, "   ");

        // When
        Set<ConstraintViolation<SyncConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage())
            .isEqualTo("Schedule cron expression is required");
    }

    @Test
    @DisplayName("Should validate boundary values")
    void shouldValidateBoundaryValues() {
        // Given - Boundary values that should be valid
        var config = new SyncConfig(
            1,    // Minimum valid issue limit
            1,    // Minimum valid batch size
            "0 0 0 1 1 *"  // Valid cron expression
        );

        // When
        Set<ConstraintViolation<SyncConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should validate maximum boundary values")
    void shouldValidateMaximumBoundaryValues() {
        // Given - Maximum boundary values that should be valid
        var config = new SyncConfig(
            100,  // Maximum valid issue limit
            50,   // Maximum valid batch size
            "59 59 23 31 12 SUN"  // Valid cron expression
        );

        // When
        Set<ConstraintViolation<SyncConfig>> violations = validator.validate(config);

        // Then
        assertThat(violations).isEmpty();
    }
}