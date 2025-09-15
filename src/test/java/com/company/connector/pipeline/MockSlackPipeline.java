package com.company.connector.pipeline;

import com.company.connector.model.GitHubIssue;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Mock transformation pipeline for testing extensibility.
 * Transforms GitHub issues to Slack message format.
 */
public class MockSlackPipeline implements DataTransformationPipeline<GitHubIssue, String> {
    
    private static final String PIPELINE_ID = "github-to-slack";
    private static final int PRIORITY = 100;
    
    @Override
    public CompletableFuture<String> transform(GitHubIssue input) {
        return CompletableFuture.supplyAsync(() -> {
            if (input == null) {
                throw new IllegalArgumentException("Input GitHubIssue cannot be null");
            }
            
            return String.format("🐛 New Issue: *%s* (%s)\n<%s|View on GitHub>", 
                    input.title(), input.state(), input.htmlUrl());
        });
    }
    
    @Override
    public CompletableFuture<List<String>> transformBatch(List<GitHubIssue> inputs) {
        return CompletableFuture.supplyAsync(() -> {
            if (inputs == null) {
                throw new IllegalArgumentException("Input list cannot be null");
            }
            
            return inputs.stream()
                    .filter(issue -> issue != null)
                    .map(issue -> String.format("🐛 New Issue: *%s* (%s)\n<%s|View on GitHub>", 
                            issue.title(), issue.state(), issue.htmlUrl()))
                    .toList();
        });
    }
    
    @Override
    public boolean supports(Class<?> inputType, Class<?> outputType) {
        return GitHubIssue.class.isAssignableFrom(inputType) && 
               String.class.isAssignableFrom(outputType);
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