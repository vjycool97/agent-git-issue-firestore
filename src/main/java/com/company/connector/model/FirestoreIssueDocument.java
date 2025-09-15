package com.company.connector.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Record representing a GitHub issue document stored in Firestore.
 * 
 * This record extends the GitHub issue data with additional metadata
 * for tracking synchronization status and timestamps.
 */
public record FirestoreIssueDocument(
    @NotBlank
    String id,
    
    @NotBlank
    String title,
    
    @NotBlank
    String state,
    
    @NotBlank
    String htmlUrl,
    
    @NotNull
    Instant createdAt,
    
    @NotNull
    Instant syncedAt
) {
    
    /**
     * Compact constructor for validation and normalization.
     * Ensures all required fields are present and valid.
     */
    public FirestoreIssueDocument {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Document ID cannot be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Issue title cannot be blank");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("Issue state cannot be blank");
        }
        if (htmlUrl == null || htmlUrl.isBlank()) {
            throw new IllegalArgumentException("Issue HTML URL cannot be blank");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Issue created date cannot be null");
        }
        if (syncedAt == null) {
            throw new IllegalArgumentException("Sync timestamp cannot be null");
        }
        
        // Normalize state to lowercase for consistency
        state = state.toLowerCase();
    }
    
    /**
     * Creates a FirestoreIssueDocument from a GitHubIssue with current timestamp.
     * 
     * @param gitHubIssue the source GitHub issue
     * @return a new FirestoreIssueDocument with sync timestamp set to now
     */
    public static FirestoreIssueDocument fromGitHubIssue(GitHubIssue gitHubIssue) {
        return new FirestoreIssueDocument(
            gitHubIssue.id().toString(),
            gitHubIssue.title(),
            gitHubIssue.state(),
            gitHubIssue.htmlUrl(),
            gitHubIssue.createdAt(),
            Instant.now()
        );
    }
    
    /**
     * Creates a FirestoreIssueDocument from a GitHubIssue with specified sync timestamp.
     * 
     * @param gitHubIssue the source GitHub issue
     * @param syncTimestamp the sync timestamp to use
     * @return a new FirestoreIssueDocument with the specified sync timestamp
     */
    public static FirestoreIssueDocument fromGitHubIssue(GitHubIssue gitHubIssue, Instant syncTimestamp) {
        return new FirestoreIssueDocument(
            gitHubIssue.id().toString(),
            gitHubIssue.title(),
            gitHubIssue.state(),
            gitHubIssue.htmlUrl(),
            gitHubIssue.createdAt(),
            syncTimestamp
        );
    }
    
    /**
     * Checks if this issue is in an open state.
     * 
     * @return true if the issue state is "open"
     */
    public boolean isOpen() {
        return "open".equals(state);
    }
    
    /**
     * Checks if this issue is in a closed state.
     * 
     * @return true if the issue state is "closed"
     */
    public boolean isClosed() {
        return "closed".equals(state);
    }
}