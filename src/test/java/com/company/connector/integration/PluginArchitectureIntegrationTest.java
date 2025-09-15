package com.company.connector.integration;

import com.company.connector.connector.MockSlackConnector;
import com.company.connector.connector.OutputConnector;
import com.company.connector.model.GitHubIssue;
import com.company.connector.pipeline.DataTransformationPipeline;
import com.company.connector.pipeline.MockSlackPipeline;
import com.company.connector.registry.ConnectorRegistry;
import com.company.connector.service.PluginOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test demonstrating the extensible plugin architecture.
 * Shows how new connectors and transformation pipelines can be added
 * without modifying existing code.
 */
class PluginArchitectureIntegrationTest {
    
    private ConnectorRegistry registry;
    private PluginOrchestrationService orchestrationService;
    
    @BeforeEach
    void setUp() {
        registry = new ConnectorRegistry();
        orchestrationService = new PluginOrchestrationService(registry);
    }
    
    @Test
    void shouldDemonstrateExtensibilityWithMultipleConnectors() {
        // Given - Register multiple connectors for different output formats
        MockSlackConnector slackConnector = new MockSlackConnector();
        MockEmailConnector emailConnector = new MockEmailConnector();
        MockWebhookConnector webhookConnector = new MockWebhookConnector();
        
        registry.registerConnector(slackConnector);
        registry.registerConnector(emailConnector);
        registry.registerConnector(webhookConnector);
        
        // Register transformation pipelines
        MockSlackPipeline slackPipeline = new MockSlackPipeline();
        MockEmailPipeline emailPipeline = new MockEmailPipeline();
        MockWebhookPipeline webhookPipeline = new MockWebhookPipeline();
        
        registry.registerPipeline(slackPipeline);
        registry.registerPipeline(emailPipeline);
        registry.registerPipeline(webhookPipeline);
        
        // Initialize all connectors
        registry.initializeAll().join();
        
        // Given - Sample GitHub issue
        GitHubIssue issue = new GitHubIssue(
                123L,
                "Critical Bug: Application crashes on startup",
                "open",
                "https://github.com/company/app/issues/123",
                Instant.now()
        );
        
        // When - Process the same issue through different output channels
        // Note: All will use the highest priority pipeline (slack) since they have the same input/output types
        orchestrationService.processData(issue, GitHubIssue.class, String.class, "slack").join();
        orchestrationService.processData(issue, GitHubIssue.class, String.class, "email").join();
        orchestrationService.processData(issue, GitHubIssue.class, String.class, "webhook").join();
        
        // Then - Verify each connector received the transformed data
        assertEquals(1, slackConnector.getSentMessages().size());
        assertEquals(1, emailConnector.getSentEmails().size());
        assertEquals(1, webhookConnector.getSentPayloads().size());
        
        // Verify content is appropriately formatted for each channel
        // Note: Since all use the same input/output types, they all use the highest priority pipeline (slack)
        String slackMessage = slackConnector.getSentMessages().iterator().next();
        assertTrue(slackMessage.contains("🐛 New Issue"));
        assertTrue(slackMessage.contains("Critical Bug"));
        
        String emailContent = emailConnector.getSentEmails().iterator().next();
        assertTrue(emailContent.contains("🐛 New Issue")); // Will be slack format due to priority
        assertTrue(emailContent.contains("Critical Bug"));
        
        String webhookPayload = webhookConnector.getSentPayloads().iterator().next();
        assertTrue(webhookPayload.contains("🐛 New Issue")); // Will be slack format due to priority
        assertTrue(webhookPayload.contains("Critical Bug"));
    }
    
    @Test
    void shouldHandleMultiplePipelinesWithPriority() {
        // Given - Register multiple pipelines for the same transformation
        MockSlackPipeline lowPriorityPipeline = new MockSlackPipeline() {
            @Override
            public String getPipelineId() {
                return "github-to-slack-basic";
            }
            
            @Override
            public int getPriority() {
                return 10;
            }
        };
        
        MockSlackPipeline highPriorityPipeline = new MockSlackPipeline() {
            @Override
            public String getPipelineId() {
                return "github-to-slack-enhanced";
            }
            
            @Override
            public int getPriority() {
                return 100;
            }
        };
        
        MockSlackConnector slackConnector = new MockSlackConnector();
        
        registry.registerPipeline(lowPriorityPipeline);
        registry.registerPipeline(highPriorityPipeline);
        registry.registerConnector(slackConnector);
        
        GitHubIssue issue = new GitHubIssue(
                123L, "Test Issue", "open", "https://github.com/test/repo/issues/123", Instant.now());
        
        // When - Process data (should use highest priority pipeline)
        orchestrationService.processData(issue, GitHubIssue.class, String.class, "slack").join();
        
        // Then - Verify the high priority pipeline was used
        assertEquals(1, slackConnector.getSentMessages().size());
    }
    
    @Test
    void shouldProvideHealthCheckForAllConnectors() {
        // Given
        MockSlackConnector slackConnector = new MockSlackConnector();
        MockEmailConnector emailConnector = new MockEmailConnector();
        
        registry.registerConnector(slackConnector);
        registry.registerConnector(emailConnector);
        
        // Set different health states
        slackConnector.setHealthy(true);
        emailConnector.setHealthy(false);
        
        // When
        Map<String, Boolean> health = registry.checkHealth().join();
        
        // Then
        assertTrue(health.get("slack"));
        assertFalse(health.get("email"));
    }
    
    @Test
    void shouldGracefullyShutdownAllConnectors() {
        // Given
        MockSlackConnector slackConnector = new MockSlackConnector();
        MockEmailConnector emailConnector = new MockEmailConnector();
        
        registry.registerConnector(slackConnector);
        registry.registerConnector(emailConnector);
        
        registry.initializeAll().join();
        
        // When
        registry.shutdownAll().join();
        
        // Then
        assertFalse(slackConnector.isHealthy().join());
        assertFalse(emailConnector.isHealthy().join());
    }
    
    @Test
    void shouldDiscoverAvailablePlugins() {
        // Given
        registry.registerConnector(new MockSlackConnector());
        registry.registerConnector(new MockEmailConnector());
        registry.registerPipeline(new MockSlackPipeline());
        registry.registerPipeline(new MockEmailPipeline());
        
        // When
        List<String> availableConnectors = orchestrationService.getAvailableConnectors(String.class);
        List<String> availablePipelines = orchestrationService.getAvailablePipelines(GitHubIssue.class, String.class);
        
        // Then
        assertEquals(2, availableConnectors.size());
        assertTrue(availableConnectors.contains("slack"));
        assertTrue(availableConnectors.contains("email"));
        
        assertEquals(2, availablePipelines.size());
        assertTrue(availablePipelines.contains("github-to-slack"));
        assertTrue(availablePipelines.contains("github-to-email"));
    }
    
    // Mock connectors and pipelines for testing extensibility
    
    private static class MockEmailConnector extends MockSlackConnector {
        @Override
        public String getConnectorType() {
            return "email";
        }
        
        public java.util.Set<String> getSentEmails() {
            return getSentMessages();
        }
    }
    
    private static class MockWebhookConnector extends MockSlackConnector {
        @Override
        public String getConnectorType() {
            return "webhook";
        }
        
        public java.util.Set<String> getSentPayloads() {
            return getSentMessages();
        }
    }
    
    private static class MockEmailPipeline implements DataTransformationPipeline<GitHubIssue, String> {
        @Override
        public CompletableFuture<String> transform(GitHubIssue input) {
            return CompletableFuture.supplyAsync(() -> {
                if (input == null) {
                    throw new IllegalArgumentException("Input GitHubIssue cannot be null");
                }
                
                return String.format("Subject: New GitHub Issue - %s\n\n" +
                        "A new issue has been created:\n" +
                        "Title: %s\n" +
                        "State: %s\n" +
                        "URL: %s\n",
                        input.title(), input.title(), input.state(), input.htmlUrl());
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
                        .map(issue -> String.format("Subject: New GitHub Issue - %s\n\n" +
                                "A new issue has been created:\n" +
                                "Title: %s\n" +
                                "State: %s\n" +
                                "URL: %s\n",
                                issue.title(), issue.title(), issue.state(), issue.htmlUrl()))
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
            return "github-to-email";
        }
        
        @Override
        public int getPriority() {
            return 50;
        }
    }
    
    private static class MockWebhookPipeline implements DataTransformationPipeline<GitHubIssue, String> {
        @Override
        public CompletableFuture<String> transform(GitHubIssue input) {
            return CompletableFuture.supplyAsync(() -> {
                if (input == null) {
                    throw new IllegalArgumentException("Input GitHubIssue cannot be null");
                }
                
                return String.format("{\"event\":\"issue.created\",\"data\":{" +
                        "\"id\":%d,\"title\":\"%s\",\"state\":\"%s\",\"url\":\"%s\"}}",
                        input.id(), input.title(), input.state(), input.htmlUrl());
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
                        .map(issue -> String.format("{\"event\":\"issue.created\",\"data\":{" +
                                "\"id\":%d,\"title\":\"%s\",\"state\":\"%s\",\"url\":\"%s\"}}",
                                issue.id(), issue.title(), issue.state(), issue.htmlUrl()))
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
            return "github-to-webhook";
        }
        
        @Override
        public int getPriority() {
            return 30;
        }
    }
}