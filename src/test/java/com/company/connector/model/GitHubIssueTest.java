package com.company.connector.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GitHubIssue Record Tests")
class GitHubIssueTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }
    
    @Test
    @DisplayName("Should create valid GitHubIssue with all required fields")
    void shouldCreateValidGitHubIssue() {
        // Given
        Long id = 123456789L;
        String title = "Bug: Application crashes on startup";
        String state = "open";
        String htmlUrl = "https://github.com/owner/repo/issues/1";
        Instant createdAt = Instant.parse("2024-01-15T10:30:00Z");
        
        // When
        GitHubIssue issue = new GitHubIssue(id, title, state, htmlUrl, createdAt);
        
        // Then
        assertEquals(id, issue.id());
        assertEquals(title, issue.title());
        assertEquals("open", issue.state()); // Should be normalized to lowercase
        assertEquals(htmlUrl, issue.htmlUrl());
        assertEquals(createdAt, issue.createdAt());
    }
    
    @Test
    @DisplayName("Should normalize state to lowercase")
    void shouldNormalizeStateToLowercase() {
        // Given
        String upperCaseState = "OPEN";
        
        // When
        GitHubIssue issue = new GitHubIssue(
            123L, "Title", upperCaseState, "https://github.com/test", Instant.now()
        );
        
        // Then
        assertEquals("open", issue.state());
    }
    
    @Test
    @DisplayName("Should throw exception for null ID")
    void shouldThrowExceptionForNullId() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GitHubIssue(null, "Title", "open", "https://github.com/test", Instant.now())
        );
        assertEquals("Issue ID must be positive", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for negative ID")
    void shouldThrowExceptionForNegativeId() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GitHubIssue(-1L, "Title", "open", "https://github.com/test", Instant.now())
        );
        assertEquals("Issue ID must be positive", exception.getMessage());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Should throw exception for blank title")
    void shouldThrowExceptionForBlankTitle(String blankTitle) {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GitHubIssue(123L, blankTitle, "open", "https://github.com/test", Instant.now())
        );
        assertEquals("Issue title cannot be blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for null title")
    void shouldThrowExceptionForNullTitle() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GitHubIssue(123L, null, "open", "https://github.com/test", Instant.now())
        );
        assertEquals("Issue title cannot be blank", exception.getMessage());
    }
    
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t", "\n"})
    @DisplayName("Should throw exception for blank state")
    void shouldThrowExceptionForBlankState(String blankState) {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GitHubIssue(123L, "Title", blankState, "https://github.com/test", Instant.now())
        );
        assertEquals("Issue state cannot be blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for null HTML URL")
    void shouldThrowExceptionForNullHtmlUrl() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GitHubIssue(123L, "Title", "open", null, Instant.now())
        );
        assertEquals("Issue HTML URL cannot be blank", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should throw exception for null created date")
    void shouldThrowExceptionForNullCreatedDate() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GitHubIssue(123L, "Title", "open", "https://github.com/test", null)
        );
        assertEquals("Issue created date cannot be null", exception.getMessage());
    }
    
    @Test
    @DisplayName("Should correctly identify open issues")
    void shouldCorrectlyIdentifyOpenIssues() {
        // Given
        GitHubIssue openIssue = new GitHubIssue(
            123L, "Title", "open", "https://github.com/test", Instant.now()
        );
        
        // When & Then
        assertTrue(openIssue.isOpen());
        assertFalse(openIssue.isClosed());
    }
    
    @Test
    @DisplayName("Should correctly identify closed issues")
    void shouldCorrectlyIdentifyClosedIssues() {
        // Given
        GitHubIssue closedIssue = new GitHubIssue(
            123L, "Title", "closed", "https://github.com/test", Instant.now()
        );
        
        // When & Then
        assertTrue(closedIssue.isClosed());
        assertFalse(closedIssue.isOpen());
    }
    
    @Test
    @DisplayName("Should serialize to JSON correctly")
    void shouldSerializeToJsonCorrectly() throws Exception {
        // Given
        GitHubIssue issue = new GitHubIssue(
            123456789L,
            "Bug: Application crashes on startup",
            "open",
            "https://github.com/owner/repo/issues/1",
            Instant.parse("2024-01-15T10:30:00Z")
        );
        
        // When
        String json = objectMapper.writeValueAsString(issue);
        
        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"id\":123456789"), "JSON should contain id field: " + json);
        assertTrue(json.contains("\"title\":\"Bug: Application crashes on startup\""), "JSON should contain title field: " + json);
        assertTrue(json.contains("\"state\":\"open\""), "JSON should contain state field: " + json);
        // Note: Jackson may serialize htmlUrl as "htmlUrl" instead of "html_url" for records
        assertTrue(json.contains("\"htmlUrl\":\"https://github.com/owner/repo/issues/1\"") || 
                  json.contains("\"html_url\":\"https://github.com/owner/repo/issues/1\""), 
                  "JSON should contain htmlUrl field: " + json);
        // Note: Jackson serializes Instant as numeric timestamp by default with JavaTimeModule
        assertTrue(json.contains("\"created_at\":1705314600.000000000") || 
                  json.contains("\"createdAt\":1705314600.000000000"), 
                  "JSON should contain createdAt field: " + json);
    }
    
    @Test
    @DisplayName("Should deserialize from JSON correctly")
    void shouldDeserializeFromJsonCorrectly() throws Exception {
        // Given
        String json = """
            {
                "id": 123456789,
                "title": "Bug: Application crashes on startup",
                "state": "OPEN",
                "html_url": "https://github.com/owner/repo/issues/1",
                "created_at": "2024-01-15T10:30:00Z"
            }
            """;
        
        // When
        GitHubIssue issue = objectMapper.readValue(json, GitHubIssue.class);
        
        // Then
        assertEquals(123456789L, issue.id());
        assertEquals("Bug: Application crashes on startup", issue.title());
        assertEquals("open", issue.state()); // Should be normalized to lowercase
        assertEquals("https://github.com/owner/repo/issues/1", issue.htmlUrl());
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), issue.createdAt());
    }
    
    @Test
    @DisplayName("Should maintain equality and hashCode contract")
    void shouldMaintainEqualityAndHashCodeContract() {
        // Given
        Instant now = Instant.now();
        GitHubIssue issue1 = new GitHubIssue(123L, "Title", "open", "https://github.com/test", now);
        GitHubIssue issue2 = new GitHubIssue(123L, "Title", "open", "https://github.com/test", now);
        GitHubIssue issue3 = new GitHubIssue(456L, "Title", "open", "https://github.com/test", now);
        
        // When & Then
        assertEquals(issue1, issue2);
        assertEquals(issue1.hashCode(), issue2.hashCode());
        assertNotEquals(issue1, issue3);
        assertNotEquals(issue1.hashCode(), issue3.hashCode());
    }
}