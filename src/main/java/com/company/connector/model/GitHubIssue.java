package com.company.connector.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;

/**
 * Record representing a GitHub issue with essential fields for synchronization.
 * 
 * This record uses Java 21 features and Jackson annotations for JSON deserialization
 * from the GitHub REST API response.
 */
public record GitHubIssue(
    @NotNull
    @Positive
    Long id,
    
    @NotBlank
    String title,
    
    @NotBlank
    String state,
    
    @NotBlank
    @JsonProperty("html_url")
    String htmlUrl,
    
    @NotNull
    @JsonProperty("created_at")
    Instant createdAt
) {
    
    /**
     * Compact constructor for validation and normalization.
     * Ensures all required fields are present and valid.
     */
    public GitHubIssue {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Issue ID must be positive");
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
        
        // Normalize state to lowercase for consistency
        state = state.toLowerCase();
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