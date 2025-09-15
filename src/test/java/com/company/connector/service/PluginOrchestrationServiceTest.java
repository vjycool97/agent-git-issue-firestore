package com.company.connector.service;

import com.company.connector.connector.MockSlackConnector;
import com.company.connector.model.GitHubIssue;
import com.company.connector.pipeline.MockSlackPipeline;
import com.company.connector.registry.ConnectorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class PluginOrchestrationServiceTest {
    
    private PluginOrchestrationService orchestrationService;
    private ConnectorRegistry registry;
    private MockSlackConnector slackConnector;
    private MockSlackPipeline slackPipeline;
    
    @BeforeEach
    void setUp() {
        registry = new ConnectorRegistry();
        orchestrationService = new PluginOrchestrationService(registry);
        slackConnector = new MockSlackConnector();
        slackPipeline = new MockSlackPipeline();
        
        // Register test plugins
        registry.registerConnector(slackConnector);
        registry.registerPipeline(slackPipeline);
    }
    
    @Test
    void shouldProcessSingleDataItem() {
        // Given
        GitHubIssue issue = new GitHubIssue(
                123L,
                "Test Issue",
                "open",
                "https://github.com/test/repo/issues/123",
                Instant.now()
        );
        
        // When
        CompletableFuture<Void> result = orchestrationService.processData(
                issue, GitHubIssue.class, String.class, "slack");
        
        // Then
        assertDoesNotThrow(() -> result.join());
        assertEquals(1, slackConnector.getSentMessages().size());
        
        String sentMessage = slackConnector.getSentMessages().iterator().next();
        assertTrue(sentMessage.contains("Test Issue"));
        assertTrue(sentMessage.contains("open"));
        assertTrue(sentMessage.contains("https://github.com/test/repo/issues/123"));
    }
    
    @Test
    void shouldProcessBatchOfDataItems() {
        // Given
        List<GitHubIssue> issues = List.of(
                new GitHubIssue(123L, "Issue 1", "open", "https://github.com/test/repo/issues/123", Instant.now()),
                new GitHubIssue(124L, "Issue 2", "closed", "https://github.com/test/repo/issues/124", Instant.now())
        );
        
        // When
        CompletableFuture<Void> result = orchestrationService.processBatch(
                issues, GitHubIssue.class, String.class, "slack");
        
        // Then
        assertDoesNotThrow(() -> result.join());
        assertEquals(2, slackConnector.getSentMessages().size());
        
        assertTrue(slackConnector.getSentMessages().stream()
                .anyMatch(msg -> msg.contains("Issue 1") && msg.contains("open")));
        assertTrue(slackConnector.getSentMessages().stream()
                .anyMatch(msg -> msg.contains("Issue 2") && msg.contains("closed")));
    }
    
    @Test
    void shouldFailWhenNoPipelineFound() {
        // Given - no pipeline registered for Integer -> String transformation
        
        // When
        CompletableFuture<Void> result = orchestrationService.processData(
                42, Integer.class, String.class, "slack");
        
        // Then
        assertThrows(Exception.class, () -> result.join());
    }
    
    @Test
    void shouldFailWhenNoConnectorFound() {
        // Given
        GitHubIssue issue = new GitHubIssue(
                123L, "Test Issue", "open", "https://github.com/test/repo/issues/123", Instant.now());
        
        // When - trying to use non-existent connector
        CompletableFuture<Void> result = orchestrationService.processData(
                issue, GitHubIssue.class, String.class, "nonexistent");
        
        // Then
        assertThrows(Exception.class, () -> result.join());
    }
    
    @Test
    void shouldCheckDataExistence() {
        // Given - First write a message to the connector
        String testMessage = "test message";
        slackConnector.write(testMessage).join();
        
        // When
        CompletableFuture<Boolean> result = orchestrationService.dataExists(
                testMessage, "slack", String.class);
        
        // Then
        assertTrue(result.join());
    }
    
    @Test
    void shouldReturnFalseForNonExistentData() {
        // When
        CompletableFuture<Boolean> result = orchestrationService.dataExists(
                "nonexistent", "slack", String.class);
        
        // Then
        assertFalse(result.join());
    }
    
    @Test
    void shouldGetAvailablePipelines() {
        // When
        List<String> pipelines = orchestrationService.getAvailablePipelines(
                GitHubIssue.class, String.class);
        
        // Then
        assertEquals(1, pipelines.size());
        assertTrue(pipelines.contains("github-to-slack"));
    }
    
    @Test
    void shouldReturnEmptyListWhenNoPipelinesAvailable() {
        // When
        List<String> pipelines = orchestrationService.getAvailablePipelines(
                Integer.class, Double.class);
        
        // Then
        assertTrue(pipelines.isEmpty());
    }
    
    @Test
    void shouldGetAvailableConnectors() {
        // When
        List<String> connectors = orchestrationService.getAvailableConnectors(String.class);
        
        // Then
        assertEquals(1, connectors.size());
        assertTrue(connectors.contains("slack"));
    }
    
    @Test
    void shouldReturnEmptyListWhenNoConnectorsAvailable() {
        // When
        List<String> connectors = orchestrationService.getAvailableConnectors(Integer.class);
        
        // Then
        assertTrue(connectors.isEmpty());
    }
    
    @Test
    void shouldHandleConnectorFailure() {
        // Given
        slackConnector.setHealthy(false);
        GitHubIssue issue = new GitHubIssue(
                123L, "Test Issue", "open", "https://github.com/test/repo/issues/123", Instant.now());
        
        // When
        CompletableFuture<Void> result = orchestrationService.processData(
                issue, GitHubIssue.class, String.class, "slack");
        
        // Then
        assertThrows(Exception.class, () -> result.join());
    }
}