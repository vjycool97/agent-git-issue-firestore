package com.company.connector.pipeline;

import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.model.GitHubIssue;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Transformation pipeline that converts GitHub issues to Firestore documents.
 * This is the default pipeline used by the connector.
 */
@Component
public class GitHubToFirestorePipeline implements DataTransformationPipeline<GitHubIssue, FirestoreIssueDocument> {
    
    private static final String PIPELINE_ID = "github-to-firestore";
    private static final int PRIORITY = 100; // High priority as default pipeline
    
    @Override
    public CompletableFuture<FirestoreIssueDocument> transform(GitHubIssue input) {
        return CompletableFuture.supplyAsync(() -> {
            if (input == null) {
                throw new IllegalArgumentException("Input GitHubIssue cannot be null");
            }
            
            return new FirestoreIssueDocument(
                    String.valueOf(input.id()),
                    input.title(),
                    input.state(),
                    input.htmlUrl(),
                    input.createdAt(),
                    Instant.now() // Set sync timestamp
            );
        });
    }
    
    @Override
    public CompletableFuture<List<FirestoreIssueDocument>> transformBatch(List<GitHubIssue> inputs) {
        return CompletableFuture.supplyAsync(() -> {
            if (inputs == null) {
                throw new IllegalArgumentException("Input list cannot be null");
            }
            
            Instant syncTime = Instant.now();
            return inputs.stream()
                    .filter(issue -> issue != null)
                    .map(issue -> new FirestoreIssueDocument(
                            String.valueOf(issue.id()),
                            issue.title(),
                            issue.state(),
                            issue.htmlUrl(),
                            issue.createdAt(),
                            syncTime
                    ))
                    .toList();
        });
    }
    
    @Override
    public boolean supports(Class<?> inputType, Class<?> outputType) {
        return GitHubIssue.class.isAssignableFrom(inputType) && 
               FirestoreIssueDocument.class.isAssignableFrom(outputType);
    }
    
    @Override
    public String getPipelineId() {
        return PIPELINE_ID;
    }
    
    @Override
    public int getPriority() {
        return PRIORITY;
    }
}