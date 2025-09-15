package com.company.connector.pipeline;

import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.model.GitHubIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class GitHubToFirestorePipelineTest {
    
    private GitHubToFirestorePipeline pipeline;
    
    @BeforeEach
    void setUp() {
        pipeline = new GitHubToFirestorePipeline();
    }
    
    @Test
    void shouldTransformSingleGitHubIssue() {
        // Given
        Instant createdAt = Instant.now().minusSeconds(3600);
        GitHubIssue issue = new GitHubIssue(
                123L,
                "Test Issue",
                "open",
                "https://github.com/test/repo/issues/123",
                createdAt
        );
        
        // When
        CompletableFuture<FirestoreIssueDocument> result = pipeline.transform(issue);
        FirestoreIssueDocument document = result.join();
        
        // Then
        assertNotNull(document);
        assertEquals("123", document.id());
        assertEquals("Test Issue", document.title());
        assertEquals("open", document.state());
        assertEquals("https://github.com/test/repo/issues/123", document.htmlUrl());
        assertEquals(createdAt, document.createdAt());
        assertNotNull(document.syncedAt());
        assertTrue(document.syncedAt().isAfter(createdAt));
    }
    
    @Test
    void shouldTransformBatchOfGitHubIssues() {
        // Given
        Instant now = Instant.now();
        List<GitHubIssue> issues = List.of(
                new GitHubIssue(123L, "Issue 1", "open", "https://github.com/test/repo/issues/123", now),
                new GitHubIssue(124L, "Issue 2", "closed", "https://github.com/test/repo/issues/124", now)
        );
        
        // When
        CompletableFuture<List<FirestoreIssueDocument>> result = pipeline.transformBatch(issues);
        List<FirestoreIssueDocument> documents = result.join();
        
        // Then
        assertNotNull(documents);
        assertEquals(2, documents.size());
        
        FirestoreIssueDocument doc1 = documents.get(0);
        assertEquals("123", doc1.id());
        assertEquals("Issue 1", doc1.title());
        assertEquals("open", doc1.state());
        
        FirestoreIssueDocument doc2 = documents.get(1);
        assertEquals("124", doc2.id());
        assertEquals("Issue 2", doc2.title());
        assertEquals("closed", doc2.state());
        
        // All documents should have the same sync time (within the same batch)
        assertEquals(doc1.syncedAt(), doc2.syncedAt());
    }
    
    @Test
    void shouldFilterNullIssuesInBatch() {
        // Given
        List<GitHubIssue> issues = new java.util.ArrayList<>();
        issues.add(new GitHubIssue(123L, "Issue 1", "open", "https://github.com/test/repo/issues/123", Instant.now()));
        issues.add(null);
        issues.add(new GitHubIssue(124L, "Issue 2", "closed", "https://github.com/test/repo/issues/124", Instant.now()));
        
        // When
        CompletableFuture<List<FirestoreIssueDocument>> result = pipeline.transformBatch(issues);
        List<FirestoreIssueDocument> documents = result.join();
        
        // Then
        assertNotNull(documents);
        assertEquals(2, documents.size()); // Null issue should be filtered out
        assertEquals("123", documents.get(0).id());
        assertEquals("124", documents.get(1).id());
    }
    
    @Test
    void shouldThrowExceptionForNullInput() {
        // When & Then
        assertThrows(Exception.class, () -> pipeline.transform(null).join());
    }
    
    @Test
    void shouldThrowExceptionForNullInputList() {
        // When & Then
        assertThrows(Exception.class, () -> pipeline.transformBatch(null).join());
    }
    
    @Test
    void shouldSupportCorrectTypes() {
        // When & Then
        assertTrue(pipeline.supports(GitHubIssue.class, FirestoreIssueDocument.class));
        assertFalse(pipeline.supports(String.class, FirestoreIssueDocument.class));
        assertFalse(pipeline.supports(GitHubIssue.class, String.class));
    }
    
    @Test
    void shouldHaveCorrectPipelineId() {
        // When & Then
        assertEquals("github-to-firestore", pipeline.getPipelineId());
    }
    
    @Test
    void shouldHaveHighPriority() {
        // When & Then
        assertEquals(100, pipeline.getPriority());
    }
}