package com.company.connector.service;

import com.company.connector.client.GitHubClient;
import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.model.GitHubIssue;
import com.company.connector.model.SyncResult;
import com.company.connector.repository.FirestoreRepository;
import com.company.connector.transformer.IssueTransformer;
import com.company.connector.transformer.TransformationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueServiceImpl Tests")
class IssueServiceImplTest {
    
    @Mock
    private GitHubClient gitHubClient;
    
    @Mock
    private IssueTransformer issueTransformer;
    
    @Mock
    private FirestoreRepository firestoreRepository;
    
    @Mock
    private org.springframework.retry.support.RetryTemplate retryTemplate;
    
    @Mock
    private com.company.connector.service.ErrorHandler errorHandler;
    
    @Mock
    private com.company.connector.monitoring.SyncMetricsService metricsService;
    
    private IssueServiceImpl issueService;
    
    @BeforeEach
    void setUp() {
        issueService = new IssueServiceImpl(gitHubClient, issueTransformer, firestoreRepository, retryTemplate, errorHandler, metricsService);
    }
    
    @Nested
    @DisplayName("Input Validation Tests")
    class InputValidationTests {
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        @DisplayName("Should throw IllegalArgumentException for invalid owner")
        void shouldThrowExceptionForInvalidOwner(String invalidOwner) {
            assertThatThrownBy(() -> issueService.syncIssues(invalidOwner, "repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Owner cannot be null or blank");
        }
        
        @Test
        @DisplayName("Should throw IllegalArgumentException for null owner")
        void shouldThrowExceptionForNullOwner() {
            assertThatThrownBy(() -> issueService.syncIssues(null, "repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Owner cannot be null or blank");
        }
        
        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        @DisplayName("Should throw IllegalArgumentException for invalid repo")
        void shouldThrowExceptionForInvalidRepo(String invalidRepo) {
            assertThatThrownBy(() -> issueService.syncIssues("owner", invalidRepo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Repository name cannot be null or blank");
        }
        
        @Test
        @DisplayName("Should throw IllegalArgumentException for null repo")
        void shouldThrowExceptionForNullRepo() {
            assertThatThrownBy(() -> issueService.syncIssues("owner", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Repository name cannot be null or blank");
        }
        
        @ParameterizedTest
        @MethodSource("invalidLimits")
        @DisplayName("Should throw IllegalArgumentException for invalid limits")
        void shouldThrowExceptionForInvalidLimits(int invalidLimit) {
            assertThatThrownBy(() -> issueService.syncIssues("owner", "repo", invalidLimit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limit must be between 1 and 100");
        }
        
        static Stream<Arguments> invalidLimits() {
            return Stream.of(
                Arguments.of(0),
                Arguments.of(-1),
                Arguments.of(-10),
                Arguments.of(101),
                Arguments.of(1000)
            );
        }
    }
    
    @Nested
    @DisplayName("Successful Sync Operations")
    class SuccessfulSyncTests {
        
        @Test
        @DisplayName("Should successfully sync new issues")
        void shouldSuccessfullySyncNewIssues() {
            // Given
            List<GitHubIssue> githubIssues = createSampleGitHubIssues(3);
            List<FirestoreIssueDocument> firestoreDocuments = createSampleFirestoreDocuments(3);
            
            when(gitHubClient.fetchRecentIssues("owner", "repo", 5))
                .thenReturn(CompletableFuture.completedFuture(githubIssues));
            when(issueTransformer.transformBatch(githubIssues))
                .thenReturn(firestoreDocuments);
            
            // Mock all documents as new (not existing)
            when(firestoreRepository.exists(anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));
            when(firestoreRepository.save(any(FirestoreIssueDocument.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo");
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            
            assertThat(syncResult).isInstanceOf(SyncResult.Success.class);
            SyncResult.Success success = (SyncResult.Success) syncResult;
            assertThat(success.processedCount()).isEqualTo(3);
            assertThat(success.duration()).isPositive();
            
            verify(gitHubClient).fetchRecentIssues("owner", "repo", 5);
            verify(issueTransformer).transformBatch(githubIssues);
            verify(firestoreRepository, times(3)).exists(anyString());
            verify(firestoreRepository, times(3)).save(any(FirestoreIssueDocument.class));
        }
        
        @Test
        @DisplayName("Should successfully sync with custom limit")
        void shouldSuccessfullySyncWithCustomLimit() {
            // Given
            List<GitHubIssue> githubIssues = createSampleGitHubIssues(10);
            List<FirestoreIssueDocument> firestoreDocuments = createSampleFirestoreDocuments(10);
            
            when(gitHubClient.fetchRecentIssues("owner", "repo", 10))
                .thenReturn(CompletableFuture.completedFuture(githubIssues));
            when(issueTransformer.transformBatch(githubIssues))
                .thenReturn(firestoreDocuments);
            when(firestoreRepository.exists(anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));
            when(firestoreRepository.save(any(FirestoreIssueDocument.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo", 10);
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            
            assertThat(syncResult).isInstanceOf(SyncResult.Success.class);
            SyncResult.Success success = (SyncResult.Success) syncResult;
            assertThat(success.processedCount()).isEqualTo(10);
            
            verify(gitHubClient).fetchRecentIssues("owner", "repo", 10);
        }
        
        @Test
        @DisplayName("Should handle empty issue list")
        void shouldHandleEmptyIssueList() {
            // Given
            when(gitHubClient.fetchRecentIssues("owner", "repo", 5))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
            
            // When
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo");
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            
            assertThat(syncResult).isInstanceOf(SyncResult.Success.class);
            SyncResult.Success success = (SyncResult.Success) syncResult;
            assertThat(success.processedCount()).isZero();
            
            verify(gitHubClient).fetchRecentIssues("owner", "repo", 5);
            verifyNoInteractions(issueTransformer, firestoreRepository);
        }
    }
    
    @Nested
    @DisplayName("Duplicate Detection Tests")
    class DuplicateDetectionTests {
        
        @Test
        @DisplayName("Should update existing issues")
        void shouldUpdateExistingIssues() {
            // Given
            List<GitHubIssue> githubIssues = createSampleGitHubIssues(2);
            List<FirestoreIssueDocument> firestoreDocuments = createSampleFirestoreDocuments(2);
            
            when(gitHubClient.fetchRecentIssues("owner", "repo", 5))
                .thenReturn(CompletableFuture.completedFuture(githubIssues));
            when(issueTransformer.transformBatch(githubIssues))
                .thenReturn(firestoreDocuments);
            
            // Mock all documents as existing
            when(firestoreRepository.exists(anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
            when(firestoreRepository.save(any(FirestoreIssueDocument.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo");
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            
            assertThat(syncResult).isInstanceOf(SyncResult.Success.class);
            SyncResult.Success success = (SyncResult.Success) syncResult;
            assertThat(success.processedCount()).isEqualTo(2);
            
            verify(firestoreRepository, times(2)).exists(anyString());
            verify(firestoreRepository, times(2)).save(any(FirestoreIssueDocument.class));
        }
        
        @Test
        @DisplayName("Should handle mixed new and existing issues")
        void shouldHandleMixedNewAndExistingIssues() {
            // Given
            List<GitHubIssue> githubIssues = createSampleGitHubIssues(3);
            List<FirestoreIssueDocument> firestoreDocuments = createSampleFirestoreDocuments(3);
            
            when(gitHubClient.fetchRecentIssues("owner", "repo", 5))
                .thenReturn(CompletableFuture.completedFuture(githubIssues));
            when(issueTransformer.transformBatch(githubIssues))
                .thenReturn(firestoreDocuments);
            
            // Mock first document as existing, others as new
            when(firestoreRepository.exists("1"))
                .thenReturn(CompletableFuture.completedFuture(true));
            when(firestoreRepository.exists("2"))
                .thenReturn(CompletableFuture.completedFuture(false));
            when(firestoreRepository.exists("3"))
                .thenReturn(CompletableFuture.completedFuture(false));
            
            when(firestoreRepository.save(any(FirestoreIssueDocument.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo");
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            
            assertThat(syncResult).isInstanceOf(SyncResult.Success.class);
            SyncResult.Success success = (SyncResult.Success) syncResult;
            assertThat(success.processedCount()).isEqualTo(3);
            
            verify(firestoreRepository, times(3)).exists(anyString());
            verify(firestoreRepository, times(3)).save(any(FirestoreIssueDocument.class));
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {
        
        @Test
        @DisplayName("Should handle GitHub client failure")
        void shouldHandleGitHubClientFailure() {
            // Given
            when(gitHubClient.fetchRecentIssues("owner", "repo", 5))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("GitHub API error")));
            
            // When
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo");
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            
            assertThat(syncResult).isInstanceOf(SyncResult.Failure.class);
            SyncResult.Failure failure = (SyncResult.Failure) syncResult;
            assertThat(failure.error()).contains("GitHub API error");
            assertThat(failure.duration()).isPositive();
            
            verifyNoInteractions(issueTransformer, firestoreRepository);
        }
        
        @Test
        @DisplayName("Should handle transformation failure")
        void shouldHandleTransformationFailure() {
            // Given
            List<GitHubIssue> githubIssues = createSampleGitHubIssues(2);
            
            when(gitHubClient.fetchRecentIssues("owner", "repo", 5))
                .thenReturn(CompletableFuture.completedFuture(githubIssues));
            when(issueTransformer.transformBatch(githubIssues))
                .thenThrow(new TransformationException("Invalid data format"));
            
            // When
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo");
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            
            assertThat(syncResult).isInstanceOf(SyncResult.Failure.class);
            SyncResult.Failure failure = (SyncResult.Failure) syncResult;
            assertThat(failure.error()).contains("Failed to transform issues");
            assertThat(failure.error()).contains("Invalid data format");
            
            verifyNoInteractions(firestoreRepository);
        }
        
        @Test
        @DisplayName("Should handle partial Firestore failures")
        void shouldHandlePartialFirestoreFailures() {
            // Given
            List<GitHubIssue> githubIssues = createSampleGitHubIssues(3);
            List<FirestoreIssueDocument> firestoreDocuments = createSampleFirestoreDocuments(3);
            
            when(gitHubClient.fetchRecentIssues("owner", "repo", 5))
                .thenReturn(CompletableFuture.completedFuture(githubIssues));
            when(issueTransformer.transformBatch(githubIssues))
                .thenReturn(firestoreDocuments);
            when(firestoreRepository.exists(anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));
            
            // Mock first save to succeed, second to fail, third to succeed
            when(firestoreRepository.save(firestoreDocuments.get(0)))
                .thenReturn(CompletableFuture.completedFuture(null));
            when(firestoreRepository.save(firestoreDocuments.get(1)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Firestore error")));
            when(firestoreRepository.save(firestoreDocuments.get(2)))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo");
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            
            assertThat(syncResult).isInstanceOf(SyncResult.PartialFailure.class);
            SyncResult.PartialFailure partialFailure = (SyncResult.PartialFailure) syncResult;
            assertThat(partialFailure.processedCount()).isEqualTo(2);
            assertThat(partialFailure.failedCount()).isEqualTo(1);
            assertThat(partialFailure.errors()).hasSize(1);
            assertThat(partialFailure.errors().get(0)).contains("Firestore error");
        }
        
        @Test
        @DisplayName("Should handle complete Firestore failure")
        void shouldHandleCompleteFirestoreFailure() {
            // Given
            List<GitHubIssue> githubIssues = createSampleGitHubIssues(2);
            List<FirestoreIssueDocument> firestoreDocuments = createSampleFirestoreDocuments(2);
            
            when(gitHubClient.fetchRecentIssues("owner", "repo", 5))
                .thenReturn(CompletableFuture.completedFuture(githubIssues));
            when(issueTransformer.transformBatch(githubIssues))
                .thenReturn(firestoreDocuments);
            when(firestoreRepository.exists(anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));
            when(firestoreRepository.save(any(FirestoreIssueDocument.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Firestore down")));
            
            // When
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo");
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            
            assertThat(syncResult).isInstanceOf(SyncResult.Failure.class);
            SyncResult.Failure failure = (SyncResult.Failure) syncResult;
            assertThat(failure.error()).contains("All sync operations failed");
        }
    }
    
    @Nested
    @DisplayName("Concurrent Processing Tests")
    class ConcurrentProcessingTests {
        
        @Test
        @DisplayName("Should process multiple issues concurrently")
        void shouldProcessMultipleIssuesConcurrently() {
            // Given
            List<GitHubIssue> githubIssues = createSampleGitHubIssues(5);
            List<FirestoreIssueDocument> firestoreDocuments = createSampleFirestoreDocuments(5);
            
            when(gitHubClient.fetchRecentIssues("owner", "repo", 5))
                .thenReturn(CompletableFuture.completedFuture(githubIssues));
            when(issueTransformer.transformBatch(githubIssues))
                .thenReturn(firestoreDocuments);
            when(firestoreRepository.exists(anyString()))
                .thenReturn(CompletableFuture.completedFuture(false));
            when(firestoreRepository.save(any(FirestoreIssueDocument.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            Instant start = Instant.now();
            CompletableFuture<SyncResult> result = issueService.syncIssues("owner", "repo");
            
            // Then
            assertThat(result).succeedsWithin(Duration.ofSeconds(5));
            SyncResult syncResult = result.join();
            Duration totalTime = Duration.between(start, Instant.now());
            
            assertThat(syncResult).isInstanceOf(SyncResult.Success.class);
            SyncResult.Success success = (SyncResult.Success) syncResult;
            assertThat(success.processedCount()).isEqualTo(5);
            
            // Verify all operations were called
            verify(firestoreRepository, times(5)).exists(anyString());
            verify(firestoreRepository, times(5)).save(any(FirestoreIssueDocument.class));
            
            // Processing should be reasonably fast due to concurrency
            assertThat(totalTime).isLessThan(Duration.ofSeconds(2));
        }
    }
    
    // Helper methods
    
    private List<GitHubIssue> createSampleGitHubIssues(int count) {
        return Stream.iterate(1, i -> i + 1)
            .limit(count)
            .map(i -> new GitHubIssue(
                (long) i,
                "Issue " + i,
                "open",
                "https://github.com/owner/repo/issues/" + i,
                Instant.now().minusSeconds(i * 3600)
            ))
            .toList();
    }
    
    private List<FirestoreIssueDocument> createSampleFirestoreDocuments(int count) {
        return Stream.iterate(1, i -> i + 1)
            .limit(count)
            .map(i -> new FirestoreIssueDocument(
                String.valueOf(i),
                "Issue " + i,
                "open",
                "https://github.com/owner/repo/issues/" + i,
                Instant.now().minusSeconds(i * 3600),
                Instant.now()
            ))
            .toList();
    }
}