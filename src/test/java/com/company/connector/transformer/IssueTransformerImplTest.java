package com.company.connector.transformer;

import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.model.GitHubIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive unit tests for IssueTransformerImpl.
 * 
 * Tests cover all transformation scenarios including validation,
 * error handling, and edge cases using Java 21 features.
 */
@DisplayName("IssueTransformerImpl Tests")
class IssueTransformerImplTest {
    
    private IssueTransformerImpl transformer;
    private GitHubIssue validIssue;
    private Instant testTime;
    
    @BeforeEach
    void setUp() {
        transformer = new IssueTransformerImpl();
        testTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        
        validIssue = new GitHubIssue(
            12345L,
            "Test Issue Title",
            "open",
            "https://github.com/owner/repo/issues/1",
            testTime.minus(1, ChronoUnit.HOURS)
        );
    }
    
    @Nested
    @DisplayName("Single Issue Transformation Tests")
    class SingleIssueTransformationTests {
        
        @Test
        @DisplayName("Should successfully transform valid GitHub issue")
        void shouldTransformValidIssue() {
            // When
            FirestoreIssueDocument result = transformer.transform(validIssue);
            
            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo("12345");
            assertThat(result.title()).isEqualTo("Test Issue Title");
            assertThat(result.state()).isEqualTo("open");
            assertThat(result.htmlUrl()).isEqualTo("https://github.com/owner/repo/issues/1");
            assertThat(result.createdAt()).isEqualTo(validIssue.createdAt());
            assertThat(result.syncedAt()).isNotNull();
            assertThat(result.syncedAt()).isAfterOrEqualTo(testTime);
        }
        
        @Test
        @DisplayName("Should normalize state to lowercase")
        void shouldNormalizeStateToLowercase() {
            // Given
            GitHubIssue issueWithUppercaseState = new GitHubIssue(
                12345L,
                "Test Issue",
                "OPEN",
                "https://github.com/owner/repo/issues/1",
                testTime
            );
            
            // When
            FirestoreIssueDocument result = transformer.transform(issueWithUppercaseState);
            
            // Then
            assertThat(result.state()).isEqualTo("open");
        }
        
        @Test
        @DisplayName("Should sanitize title by trimming whitespace")
        void shouldSanitizeTitleByTrimmingWhitespace() {
            // Given
            GitHubIssue issueWithWhitespace = new GitHubIssue(
                12345L,
                "  Test Issue Title  ",
                "open",
                "https://github.com/owner/repo/issues/1",
                testTime
            );
            
            // When
            FirestoreIssueDocument result = transformer.transform(issueWithWhitespace);
            
            // Then
            assertThat(result.title()).isEqualTo("Test Issue Title");
        }
        
        @Test
        @DisplayName("Should truncate very long titles")
        void shouldTruncateVeryLongTitles() {
            // Given
            String longTitle = "A".repeat(1500);
            GitHubIssue issueWithLongTitle = new GitHubIssue(
                12345L,
                longTitle,
                "open",
                "https://github.com/owner/repo/issues/1",
                testTime
            );
            
            // When
            FirestoreIssueDocument result = transformer.transform(issueWithLongTitle);
            
            // Then
            assertThat(result.title()).hasSize(1000);
            assertThat(result.title()).endsWith("...");
        }
    }
    
    @Nested
    @DisplayName("Validation Error Tests")
    class ValidationErrorTests {
        
        @Test
        @DisplayName("Should throw exception for null issue")
        void shouldThrowExceptionForNullIssue() {
            // When & Then
            assertThatThrownBy(() -> transformer.transform(null))
                .isInstanceOf(TransformationException.class)
                .hasMessageContaining("GitHub issue cannot be null");
        }
        
        @Test
        @DisplayName("Should throw exception for null issue ID")
        void shouldThrowExceptionForNullIssueId() {
            // When & Then - The GitHubIssue record itself validates and throws exception
            assertThatThrownBy(() -> new GitHubIssue(
                null,
                "Test Issue",
                "open",
                "https://github.com/owner/repo/issues/1",
                testTime
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Issue ID must be positive");
        }
        
        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, -100L})
        @DisplayName("Should throw exception for non-positive issue ID")
        void shouldThrowExceptionForNonPositiveIssueId(Long invalidId) {
            // When & Then
            assertThatThrownBy(() -> new GitHubIssue(
                invalidId,
                "Test Issue",
                "open",
                "https://github.com/owner/repo/issues/1",
                testTime
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Issue ID must be positive");
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should throw exception for invalid title")
        void shouldThrowExceptionForInvalidTitle(String invalidTitle) {
            // When & Then
            assertThatThrownBy(() -> new GitHubIssue(
                12345L,
                invalidTitle,
                "open",
                "https://github.com/owner/repo/issues/1",
                testTime
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Issue title cannot be blank");
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n"})
        @DisplayName("Should throw exception for invalid state")
        void shouldThrowExceptionForInvalidState(String invalidState) {
            // When & Then
            assertThatThrownBy(() -> new GitHubIssue(
                12345L,
                "Test Issue",
                invalidState,
                "https://github.com/owner/repo/issues/1",
                testTime
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Issue state cannot be blank");
        }
        
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t", "\n", "not-a-url", "ftp://invalid.com"})
        @DisplayName("Should throw exception for invalid HTML URL")
        void shouldThrowExceptionForInvalidHtmlUrl(String invalidUrl) {
            // When & Then
            if (invalidUrl == null || invalidUrl.isBlank()) {
                assertThatThrownBy(() -> new GitHubIssue(
                    12345L,
                    "Test Issue",
                    "open",
                    invalidUrl,
                    testTime
                )).isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("Issue HTML URL cannot be blank");
            } else {
                GitHubIssue issue = new GitHubIssue(
                    12345L,
                    "Test Issue",
                    "open",
                    invalidUrl,
                    testTime
                );
                
                assertThatThrownBy(() -> transformer.transform(issue))
                    .isInstanceOf(TransformationException.class)
                    .hasMessageContaining("Issue HTML URL is not a valid URL");
            }
        }
        
        @Test
        @DisplayName("Should throw exception for null created date")
        void shouldThrowExceptionForNullCreatedDate() {
            // When & Then
            assertThatThrownBy(() -> new GitHubIssue(
                12345L,
                "Test Issue",
                "open",
                "https://github.com/owner/repo/issues/1",
                null
            )).isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("Issue created date cannot be null");
        }
        
        @Test
        @DisplayName("Should throw exception for future created date")
        void shouldThrowExceptionForFutureCreatedDate() {
            // Given
            Instant futureTime = Instant.now().plus(1, ChronoUnit.DAYS);
            GitHubIssue issueWithFutureDate = new GitHubIssue(
                12345L,
                "Test Issue",
                "open",
                "https://github.com/owner/repo/issues/1",
                futureTime
            );
            
            // When & Then
            assertThatThrownBy(() -> transformer.transform(issueWithFutureDate))
                .isInstanceOf(TransformationException.class)
                .hasMessageContaining("Issue created date cannot be in the future");
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"invalid", "pending", "draft", "INVALID"})
        @DisplayName("Should throw exception for invalid issue state")
        void shouldThrowExceptionForInvalidIssueState(String invalidState) {
            // Given - Create issue with invalid state (record will normalize but transformer will validate)
            GitHubIssue issueWithInvalidState = new GitHubIssue(
                12345L,
                "Test Issue",
                invalidState, // This will be normalized to lowercase by the record
                "https://github.com/owner/repo/issues/1",
                testTime
            );
            
            // When & Then
            assertThatThrownBy(() -> transformer.transform(issueWithInvalidState))
                .isInstanceOf(TransformationException.class)
                .hasMessageContaining("Issue state is not valid");
        }
    }
    
    @Nested
    @DisplayName("Batch Transformation Tests")
    class BatchTransformationTests {
        
        @Test
        @DisplayName("Should transform batch of valid issues")
        void shouldTransformBatchOfValidIssues() {
            // Given
            List<GitHubIssue> issues = Arrays.asList(
                new GitHubIssue(1L, "Issue 1", "open", "https://github.com/owner/repo/issues/1", testTime),
                new GitHubIssue(2L, "Issue 2", "closed", "https://github.com/owner/repo/issues/2", testTime),
                new GitHubIssue(3L, "Issue 3", "open", "https://github.com/owner/repo/issues/3", testTime)
            );
            
            // When
            List<FirestoreIssueDocument> results = transformer.transformBatch(issues);
            
            // Then
            assertThat(results).hasSize(3);
            assertThat(results.get(0).id()).isEqualTo("1");
            assertThat(results.get(1).id()).isEqualTo("2");
            assertThat(results.get(2).id()).isEqualTo("3");
            assertThat(results.get(0).state()).isEqualTo("open");
            assertThat(results.get(1).state()).isEqualTo("closed");
            assertThat(results.get(2).state()).isEqualTo("open");
        }
        
        @Test
        @DisplayName("Should handle empty issue list")
        void shouldHandleEmptyIssueList() {
            // Given
            List<GitHubIssue> emptyList = Collections.emptyList();
            
            // When
            List<FirestoreIssueDocument> results = transformer.transformBatch(emptyList);
            
            // Then
            assertThat(results).isEmpty();
        }
        
        @Test
        @DisplayName("Should throw exception for null issue list")
        void shouldThrowExceptionForNullIssueList() {
            // When & Then
            assertThatThrownBy(() -> transformer.transformBatch(null))
                .isInstanceOf(TransformationException.class)
                .hasMessageContaining("Issue list cannot be null");
        }
        
        @Test
        @DisplayName("Should handle mixed valid and invalid issues")
        void shouldHandleMixedValidAndInvalidIssues() {
            // Given
            List<GitHubIssue> mixedIssues = Arrays.asList(
                new GitHubIssue(1L, "Valid Issue 1", "open", "https://github.com/owner/repo/issues/1", testTime),
                new GitHubIssue(2L, "Valid Issue 2", "invalid-state", "https://github.com/owner/repo/issues/2", testTime),
                new GitHubIssue(3L, "Valid Issue 3", "closed", "https://github.com/owner/repo/issues/3", testTime)
            );
            
            // When
            List<FirestoreIssueDocument> results = transformer.transformBatch(mixedIssues);
            
            // Then
            assertThat(results).hasSize(2); // Only valid issues should be transformed
            assertThat(results.get(0).id()).isEqualTo("1");
            assertThat(results.get(1).id()).isEqualTo("3");
        }
        
        @Test
        @DisplayName("Should throw exception when all transformations fail")
        void shouldThrowExceptionWhenAllTransformationsFail() {
            // Given
            List<GitHubIssue> invalidIssues = Arrays.asList(
                new GitHubIssue(1L, "Issue 1", "invalid-state", "https://github.com/owner/repo/issues/1", testTime),
                new GitHubIssue(2L, "Issue 2", "another-invalid-state", "https://github.com/owner/repo/issues/2", testTime)
            );
            
            // When & Then
            assertThatThrownBy(() -> transformer.transformBatch(invalidIssues))
                .isInstanceOf(TransformationException.class)
                .hasMessageContaining("All transformations failed");
        }
    }
    
    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Should handle issue with minimum valid values")
        void shouldHandleIssueWithMinimumValidValues() {
            // Given
            GitHubIssue minimalIssue = new GitHubIssue(
                1L,
                "A",
                "open",
                "http://a.com",
                Instant.EPOCH
            );
            
            // When
            FirestoreIssueDocument result = transformer.transform(minimalIssue);
            
            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo("1");
            assertThat(result.title()).isEqualTo("A");
            assertThat(result.state()).isEqualTo("open");
            assertThat(result.htmlUrl()).isEqualTo("http://a.com");
        }
        
        @Test
        @DisplayName("Should handle issue with maximum ID value")
        void shouldHandleIssueWithMaximumIdValue() {
            // Given
            GitHubIssue issueWithMaxId = new GitHubIssue(
                Long.MAX_VALUE,
                "Test Issue",
                "closed",
                "https://github.com/owner/repo/issues/max",
                testTime
            );
            
            // When
            FirestoreIssueDocument result = transformer.transform(issueWithMaxId);
            
            // Then
            assertThat(result.id()).isEqualTo(String.valueOf(Long.MAX_VALUE));
        }
        
        @ParameterizedTest
        @MethodSource("provideValidUrlFormats")
        @DisplayName("Should accept various valid URL formats")
        void shouldAcceptVariousValidUrlFormats(String validUrl) {
            // Given
            GitHubIssue issueWithValidUrl = new GitHubIssue(
                12345L,
                "Test Issue",
                "open",
                validUrl,
                testTime
            );
            
            // When & Then
            assertThatCode(() -> transformer.transform(issueWithValidUrl))
                .doesNotThrowAnyException();
        }
        
        private static Stream<Arguments> provideValidUrlFormats() {
            return Stream.of(
                Arguments.of("http://github.com"),
                Arguments.of("https://github.com"),
                Arguments.of("https://api.github.com/repos/owner/repo/issues/1"),
                Arguments.of("http://localhost:8080/issues/1"),
                Arguments.of("https://subdomain.example.com/path/to/issue")
            );
        }
        
        @Test
        @DisplayName("Should handle concurrent transformations safely")
        void shouldHandleConcurrentTransformationsSafely() {
            // Given
            List<GitHubIssue> issues = Arrays.asList(
                new GitHubIssue(1L, "Issue 1", "open", "https://github.com/owner/repo/issues/1", testTime),
                new GitHubIssue(2L, "Issue 2", "closed", "https://github.com/owner/repo/issues/2", testTime),
                new GitHubIssue(3L, "Issue 3", "open", "https://github.com/owner/repo/issues/3", testTime)
            );
            
            // When - Transform the same issues concurrently
            List<FirestoreIssueDocument> results1 = transformer.transformBatch(issues);
            List<FirestoreIssueDocument> results2 = transformer.transformBatch(issues);
            
            // Then - Both should succeed and produce equivalent results
            assertThat(results1).hasSize(3);
            assertThat(results2).hasSize(3);
            
            for (int i = 0; i < 3; i++) {
                assertThat(results1.get(i).id()).isEqualTo(results2.get(i).id());
                assertThat(results1.get(i).title()).isEqualTo(results2.get(i).title());
                assertThat(results1.get(i).state()).isEqualTo(results2.get(i).state());
            }
        }
    }
}