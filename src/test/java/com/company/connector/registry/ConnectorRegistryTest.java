package com.company.connector.registry;

import com.company.connector.connector.MockSlackConnector;
import com.company.connector.connector.OutputConnector;
import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.model.GitHubIssue;
import com.company.connector.pipeline.DataTransformationPipeline;
import com.company.connector.pipeline.MockSlackPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ConnectorRegistryTest {
    
    private ConnectorRegistry registry;
    private MockSlackConnector slackConnector;
    private MockSlackPipeline slackPipeline;
    
    @BeforeEach
    void setUp() {
        registry = new ConnectorRegistry();
        slackConnector = new MockSlackConnector();
        slackPipeline = new MockSlackPipeline();
    }
    
    @Test
    void shouldRegisterAndRetrieveOutputConnector() {
        // When
        registry.registerConnector(slackConnector);
        
        // Then
        Optional<OutputConnector<String>> retrieved = 
                registry.getConnector("slack", String.class);
        
        assertTrue(retrieved.isPresent());
        assertEquals("slack", retrieved.get().getConnectorType());
        assertEquals(String.class, retrieved.get().getSupportedDataType());
    }
    
    @Test
    void shouldRegisterAndRetrieveTransformationPipeline() {
        // When
        registry.registerPipeline(slackPipeline);
        
        // Then
        Optional<DataTransformationPipeline<GitHubIssue, String>> retrieved = 
                registry.getPipeline("github-to-slack", GitHubIssue.class, String.class);
        
        assertTrue(retrieved.isPresent());
        assertEquals("github-to-slack", retrieved.get().getPipelineId());
        assertTrue(retrieved.get().supports(GitHubIssue.class, String.class));
    }
    
    @Test
    void shouldFindBestPipelineByPriority() {
        // Given
        DataTransformationPipeline<GitHubIssue, String> lowPriorityPipeline = 
                new TestPipeline("low-priority", 10);
        DataTransformationPipeline<GitHubIssue, String> highPriorityPipeline = 
                new TestPipeline("high-priority", 100);
        
        registry.registerPipeline(lowPriorityPipeline);
        registry.registerPipeline(highPriorityPipeline);
        
        // When
        Optional<DataTransformationPipeline<GitHubIssue, String>> best = 
                registry.findBestPipeline(GitHubIssue.class, String.class);
        
        // Then
        assertTrue(best.isPresent());
        assertEquals("high-priority", best.get().getPipelineId());
        assertEquals(100, best.get().getPriority());
    }
    
    @Test
    void shouldGetConnectorsByDataType() {
        // Given
        MockSlackConnector slack1 = new MockSlackConnector();
        MockSlackConnector slack2 = new MockSlackConnector() {
            @Override
            public String getConnectorType() {
                return "slack-webhook";
            }
        };
        
        registry.registerConnector(slack1);
        registry.registerConnector(slack2);
        
        // When
        List<OutputConnector<String>> connectors = 
                registry.getConnectorsByDataType(String.class);
        
        // Then
        assertEquals(2, connectors.size());
        assertTrue(connectors.stream().anyMatch(c -> "slack".equals(c.getConnectorType())));
        assertTrue(connectors.stream().anyMatch(c -> "slack-webhook".equals(c.getConnectorType())));
    }
    
    @Test
    void shouldInitializeAllConnectors() {
        // Given
        registry.registerConnector(slackConnector);
        
        // When
        CompletableFuture<Void> result = registry.initializeAll();
        
        // Then
        assertDoesNotThrow(() -> result.join());
        assertTrue(slackConnector.isHealthy().join());
    }
    
    @Test
    void shouldShutdownAllConnectors() {
        // Given
        registry.registerConnector(slackConnector);
        registry.initializeAll().join();
        
        // When
        CompletableFuture<Void> result = registry.shutdownAll();
        
        // Then
        assertDoesNotThrow(() -> result.join());
        assertFalse(slackConnector.isHealthy().join());
    }
    
    @Test
    void shouldCheckHealthOfAllConnectors() {
        // Given
        registry.registerConnector(slackConnector);
        slackConnector.setHealthy(true);
        
        // When
        CompletableFuture<Map<String, Boolean>> healthResult = registry.checkHealth();
        Map<String, Boolean> health = healthResult.join();
        
        // Then
        assertTrue(health.containsKey("slack"));
        assertTrue(health.get("slack"));
    }
    
    @Test
    void shouldHandleUnhealthyConnectorInHealthCheck() {
        // Given
        registry.registerConnector(slackConnector);
        slackConnector.setHealthy(false);
        
        // When
        CompletableFuture<Map<String, Boolean>> healthResult = registry.checkHealth();
        Map<String, Boolean> health = healthResult.join();
        
        // Then
        assertTrue(health.containsKey("slack"));
        assertFalse(health.get("slack"));
    }
    
    @Test
    void shouldReturnRegisteredConnectorTypes() {
        // Given
        registry.registerConnector(slackConnector);
        
        // When
        var types = registry.getRegisteredConnectorTypes();
        
        // Then
        assertEquals(1, types.size());
        assertTrue(types.contains("slack"));
    }
    
    @Test
    void shouldReturnRegisteredPipelineIds() {
        // Given
        registry.registerPipeline(slackPipeline);
        
        // When
        var ids = registry.getRegisteredPipelineIds();
        
        // Then
        assertEquals(1, ids.size());
        assertTrue(ids.contains("github-to-slack"));
    }
    
    @Test
    void shouldReplaceExistingConnectorWithWarning() {
        // Given
        MockSlackConnector originalConnector = new MockSlackConnector();
        MockSlackConnector replacementConnector = new MockSlackConnector();
        
        registry.registerConnector(originalConnector);
        
        // When
        registry.registerConnector(replacementConnector);
        
        // Then
        Optional<OutputConnector<String>> retrieved = 
                registry.getConnector("slack", String.class);
        assertTrue(retrieved.isPresent());
        assertSame(replacementConnector, retrieved.get());
    }
    
    // Helper class for testing pipeline priority
    private static class TestPipeline implements DataTransformationPipeline<GitHubIssue, String> {
        private final String id;
        private final int priority;
        
        TestPipeline(String id, int priority) {
            this.id = id;
            this.priority = priority;
        }
        
        @Override
        public CompletableFuture<String> transform(GitHubIssue input) {
            return CompletableFuture.completedFuture("test");
        }
        
        @Override
        public CompletableFuture<List<String>> transformBatch(List<GitHubIssue> inputs) {
            return CompletableFuture.completedFuture(List.of("test"));
        }
        
        @Override
        public boolean supports(Class<?> inputType, Class<?> outputType) {
            return GitHubIssue.class.isAssignableFrom(inputType) && 
                   String.class.isAssignableFrom(outputType);
        }
        
        @Override
        public String getPipelineId() {
            return id;
        }
        
        @Override
        public int getPriority() {
            return priority;
        }
    }
}