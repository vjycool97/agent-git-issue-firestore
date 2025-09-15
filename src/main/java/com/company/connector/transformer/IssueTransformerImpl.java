package com.company.connector.transformer;

import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.model.GitHubIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of IssueTransformer that transforms GitHub issues to Firestore documents.
 * 
 * This implementation uses Java 21 Pattern Matching for validation and provides
 * comprehensive error handling with detailed logging for transformation failures.
 */
@Component
public class IssueTransformerImpl implements IssueTransformer {
    
    private static final Logger logger = LoggerFactory.getLogger(IssueTransformerImpl.class);
    
    @Override
    public FirestoreIssueDocument transform(GitHubIssue issue) {
        logger.debug("Transforming GitHub issue with ID: {}", issue != null ? issue.id() : "null");
        
        try {
            // Validate input using pattern matching
            return switch (validateIssue(issue)) {
                case ValidationResult.Valid() -> performTransformation(issue);
                case ValidationResult.Invalid(String error) -> {
                    logger.warn("Transformation failed for issue {}: {}", 
                        issue != null ? issue.id() : "null", error);
                    throw new TransformationException("Invalid GitHub issue: " + error);
                }
            };
        } catch (TransformationException e) {
            // Re-throw validation exceptions as-is
            throw e;
        } catch (Exception e) {
            String issueId = issue != null ? issue.id().toString() : "unknown";
            logger.error("Unexpected error during transformation of issue {}: {}", issueId, e.getMessage(), e);
            throw new TransformationException("Failed to transform issue " + issueId, e);
        }
    }
    
    @Override
    public List<FirestoreIssueDocument> transformBatch(List<GitHubIssue> issues) {
        if (issues == null) {
            logger.warn("Attempted to transform null issue list");
            throw new TransformationException("Issue list cannot be null");
        }
        
        logger.info("Transforming batch of {} GitHub issues", issues.size());
        
        List<FirestoreIssueDocument> transformedIssues = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < issues.size(); i++) {
            GitHubIssue issue = issues.get(i);
            try {
                FirestoreIssueDocument transformed = transform(issue);
                transformedIssues.add(transformed);
                logger.debug("Successfully transformed issue {} at index {}", 
                    issue != null ? issue.id() : "null", i);
            } catch (TransformationException e) {
                String error = String.format("Failed to transform issue at index %d: %s", i, e.getMessage());
                errors.add(error);
                logger.warn(error);
            }
        }
        
        if (!errors.isEmpty()) {
            String errorSummary = String.format("Batch transformation completed with %d errors out of %d issues", 
                errors.size(), issues.size());
            logger.warn("{}: {}", errorSummary, String.join("; ", errors));
            
            // If all transformations failed, throw exception
            if (transformedIssues.isEmpty()) {
                throw new TransformationException(errorSummary + ". All transformations failed.");
            }
        }
        
        logger.info("Batch transformation completed: {} successful, {} failed", 
            transformedIssues.size(), errors.size());
        
        return transformedIssues;
    }
    
    /**
     * Validates a GitHub issue using Java 21 Pattern Matching.
     * 
     * @param issue the issue to validate
     * @return ValidationResult indicating success or failure with details
     */
    private ValidationResult validateIssue(GitHubIssue issue) {
        return switch (issue) {
            case null -> new ValidationResult.Invalid("GitHub issue cannot be null");
            case GitHubIssue i when i.id() == null -> 
                new ValidationResult.Invalid("Issue ID cannot be null");
            case GitHubIssue i when i.id() <= 0 -> 
                new ValidationResult.Invalid("Issue ID must be positive, got: " + i.id());
            case GitHubIssue i when i.title() == null || i.title().isBlank() -> 
                new ValidationResult.Invalid("Issue title cannot be null or blank");
            case GitHubIssue i when i.state() == null || i.state().isBlank() -> 
                new ValidationResult.Invalid("Issue state cannot be null or blank");
            case GitHubIssue i when i.htmlUrl() == null || i.htmlUrl().isBlank() -> 
                new ValidationResult.Invalid("Issue HTML URL cannot be null or blank");
            case GitHubIssue i when i.createdAt() == null -> 
                new ValidationResult.Invalid("Issue created date cannot be null");
            case GitHubIssue i when !isValidUrl(i.htmlUrl()) -> 
                new ValidationResult.Invalid("Issue HTML URL is not a valid URL: " + i.htmlUrl());
            case GitHubIssue i when !isValidState(i.state()) -> 
                new ValidationResult.Invalid("Issue state is not valid: " + i.state());
            case GitHubIssue i when i.createdAt().isAfter(Instant.now()) -> 
                new ValidationResult.Invalid("Issue created date cannot be in the future: " + i.createdAt());
            default -> new ValidationResult.Valid();
        };
    }
    
    /**
     * Performs the actual transformation from GitHubIssue to FirestoreIssueDocument.
     * 
     * @param issue the validated GitHub issue
     * @return the transformed Firestore document
     */
    private FirestoreIssueDocument performTransformation(GitHubIssue issue) {
        Instant syncTimestamp = Instant.now();
        
        try {
            return new FirestoreIssueDocument(
                issue.id().toString(),
                sanitizeTitle(issue.title()),
                normalizeState(issue.state()),
                issue.htmlUrl(),
                issue.createdAt(),
                syncTimestamp
            );
        } catch (Exception e) {
            logger.error("Failed to create FirestoreIssueDocument for issue {}: {}", 
                issue.id(), e.getMessage(), e);
            throw new TransformationException("Failed to create Firestore document", e);
        }
    }
    
    /**
     * Validates if a URL string is properly formatted.
     * 
     * @param url the URL to validate
     * @return true if the URL is valid
     */
    private boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        
        // Basic URL validation - should start with http or https
        return url.startsWith("http://") || url.startsWith("https://");
    }
    
    /**
     * Validates if an issue state is one of the expected values.
     * 
     * @param state the state to validate
     * @return true if the state is valid
     */
    private boolean isValidState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        
        String normalizedState = state.toLowerCase().trim();
        return "open".equals(normalizedState) || "closed".equals(normalizedState);
    }
    
    /**
     * Sanitizes the issue title by trimming whitespace and limiting length.
     * 
     * @param title the title to sanitize
     * @return the sanitized title
     */
    private String sanitizeTitle(String title) {
        if (title == null) {
            return "";
        }
        
        String sanitized = title.trim();
        
        // Limit title length to prevent Firestore document size issues
        if (sanitized.length() > 1000) {
            sanitized = sanitized.substring(0, 997) + "...";
            logger.debug("Title truncated to 1000 characters");
        }
        
        return sanitized;
    }
    
    /**
     * Normalizes the issue state to lowercase.
     * 
     * @param state the state to normalize
     * @return the normalized state
     */
    private String normalizeState(String state) {
        return state != null ? state.toLowerCase().trim() : "";
    }
    
    /**
     * Sealed interface for validation results using Java 21 sealed classes.
     */
    private sealed interface ValidationResult 
        permits ValidationResult.Valid, ValidationResult.Invalid {
        
        record Valid() implements ValidationResult {}
        record Invalid(String error) implements ValidationResult {}
    }
}