package com.company.connector.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FirestoreIssueDocument Record Tests")
class FirestoreIssueDocumentTest {
    
    @Test
    @DisplayName("Should create valid FirestoreIssueDocument with all required fields")
    void shouldCreateValidFirestoreIssueDocument() {
        // Given
        String id = "123456789";
        String title = "Bug: Application crashes on startup";
        String state = "open";
        String htmlUrl = "https://github.com/owner/repo/issues/1";
        Instant createdAt = Instant.parse("2024-01-15T10:30:00Z");
        Instant syncedAt = Instant.parse("2024-01-15T11:00:00Z");
        
        // When
        FirestoreIssueDocument document = new FirestoreIssueDocument(
            id, title, state, htmlUrl, createdAt, syncedAt
        );
        
        // Then
        assertEquals(id, document.id());
        assertEquals(title, document.title());
        assertEquals("open", document.state()); // Should be normalized to lowercase
        assertEquals(htmlUrl, document.htmlUrl());
        assertEquals(createdAt, document.createdAt());
        assertEquals(syncedAt, document.syncedAt());
    }
    
    @Test
    @DisplayName("Should normalize state to lowercase")
    void shouldNormalizeStateToLowercase() {
        // Given
        String upperCaseState = "CLOSED";
        
        // When
        FirestoreIssueDocument document = new FirestoreIssueDocument(
            "123", "Title", upperCaseState, "https://github.com/test", 
            Instant.now(), Instant.now()
        );
        
        // Then
        assertEquals("closed", document.state());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Should throw exception for blank ID")
    void shouldThrowExceptionForBlankId(String blankId) {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new FirestoreIssueDocument(
                blankId, "Title", "open", "https://github.com/test", 
                Instant.now(), Instant.now()
            )
        );
        assertEquals("Document ID cannot be blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for null ID")
    void shouldThrowExceptionForNullId() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new FirestoreIssueDocument(
                null, "Title", "open", "https://github.com/test", 
                Instant.now(), Instant.now()
            )
        );
        assertEquals("Document ID cannot be blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for null sync timestamp")
    void shouldThrowExceptionForNullSyncTimestamp() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new FirestoreIssueDocument(
                "123", "Title", "open", "https://github.com/test", 
                Instant.now(), null
            )
        );
        assertEquals("Sync timestamp cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should create from GitHubIssue with current timestamp")
    void shouldCreateFromGitHubIssueWithCurrentTimestamp() {
        // Given
        GitHubIssue gitHubIssue = new GitHubIssue(
            123456789L,
            "Bug: Application crashes on startup",
            "open",
            "https://github.com/owner/repo/issues/1",
            Instant.parse("2024-01-15T10:30:00Z")
        );
        Instant beforeCreation = Instant.now();
        
        // When
        FirestoreIssueDocument document = FirestoreIssueDocument.fromGitHubIssue(gitHubIssue);
        
        // Then
        assertEquals("123456789", document.id());
        assertEquals(gitHubIssue.title(), document.title());
        assertEquals(gitHubIssue.state(), document.state());
        assertEquals(gitHubIssue.htmlUrl(), document.htmlUrl());
        assertEquals(gitHubIssue.createdAt(), document.createdAt());
        assertTrue(document.syncedAt().isAfter(beforeCreation) || document.syncedAt().equals(beforeCreation));
    }
    
    @Test
    @DisplayName("Should create from GitHubIssue with specified timestamp")
    void shouldCreateFromGitHubIssueWithSpecifiedTimestamp() {
        // Given
        GitHubIssue gitHubIssue = new GitHubIssue(
            123456789L,
            "Bug: Application crashes on startup",
            "open",
            "https://github.com/owner/repo/issues/1",
            Instant.parse("2024-01-15T10:30:00Z")
        );
        Instant specificSyncTime = Instant.parse("2024-01-15T11:00:00Z");
        
        // When
        FirestoreIssueDocument document = FirestoreIssueDocument.fromGitHubIssue(
            gitHubIssue, specificSyncTime
        );
        
        // Then
        assertEquals("123456789", document.id());
        assertEquals(gitHubIssue.title(), document.title());
        assertEquals(gitHubIssue.state(), document.state());
        assertEquals(gitHubIssue.htmlUrl(), document.htmlUrl());
        assertEquals(gitHubIssue.createdAt(), document.createdAt());
        assertEquals(specificSyncTime, document.syncedAt());
    }
    
    @Test
    @DisplayName("Should correctly identify open issues")
    void shouldCorrectlyIdentifyOpenIssues() {
        // Given
        FirestoreIssueDocument openDocument = new FirestoreIssueDocument(
            "123", "Title", "open", "https://github.com/test", 
            Instant.now(), Instant.now()
        );
        
        // When & Then
        assertTrue(openDocument.isOpen());
        assertFalse(openDocument.isClosed());
    }
    
    @Test
    @DisplayName("Should correctly identify closed issues")
    void shouldCorrectlyIdentifyClosedIssues() {
        // Given
        FirestoreIssueDocument closedDocument = new FirestoreIssueDocument(
            "123", "Title", "closed", "https://github.com/test", 
            Instant.now(), Instant.now()
        );
        
        // When & Then
        assertTrue(closedDocument.isClosed());
        assertFalse(closedDocument.isOpen());
    }
    
    @Test
    @DisplayName("Should maintain equality and hashCode contract")
    void shouldMaintainEqualityAndHashCodeContract() {
        // Given
        Instant createdAt = Instant.parse("2024-01-15T10:30:00Z");
        Instant syncedAt = Instant.parse("2024-01-15T11:00:00Z");
        
        FirestoreIssueDocument document1 = new FirestoreIssueDocument(
            "123", "Title", "open", "https://github.com/test", createdAt, syncedAt
        );
        FirestoreIssueDocument document2 = new FirestoreIssueDocument(
            "123", "Title", "open", "https://github.com/test", createdAt, syncedAt
        );
        FirestoreIssueDocument document3 = new FirestoreIssueDocument(
            "456", "Title", "open", "https://github.com/test", createdAt, syncedAt
        );
        
        // When & Then
        assertEquals(document1, document2);
        assertEquals(document1.hashCode(), document2.hashCode());
        assertNotEquals(document1, document3);
        assertNotEquals(document1.hashCode(), document3.hashCode());
    }
    
    @Test
    @DisplayName("Should handle conversion from GitHubIssue with different states")
    void shouldHandleConversionFromGitHubIssueWithDifferentStates() {
        // Given
        GitHubIssue closedIssue = new GitHubIssue(
            987654321L,
            "Feature: Add new functionality",
            "CLOSED",
            "https://github.com/owner/repo/issues/2",
            Instant.parse("2024-01-10T08:00:00Z")
        );
        
        // When
        FirestoreIssueDocument document = FirestoreIssueDocument.fromGitHubIssue(closedIssue);
        
        // Then
        assertEquals("987654321", document.id());
        assertEquals("closed", document.state()); // Should be normalized
        assertTrue(document.isClosed());
        assertFalse(document.isOpen());
    }
}