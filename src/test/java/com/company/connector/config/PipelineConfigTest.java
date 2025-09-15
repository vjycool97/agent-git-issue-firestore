package com.company.connector.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PipelineConfigTest {
    
    @Test
    void shouldCreateValidPipelineConfig() {
        // Given
        var transformationConfig = new PipelineConfig.TransformationPipelineConfig(
                "test-pipeline",
                "github-firestore",
                "com.company.connector.model.GitHubIssue",
                "com.company.connector.model.FirestoreIssueDocument",
                100,
                true,
                Map.of("validateFields", true)
        );
        
        var outputConfig = new PipelineConfig.OutputConnectorConfig(
                "firestore",
                "com.company.connector.model.FirestoreIssueDocument",
                true,
                Map.of("collection", "github_issues")
        );
        
        // When
        var pipelineConfig = new PipelineConfig(
                List.of(transformationConfig),
                List.of(outputConfig),
                Map.of("enableHealthChecks", true)
        );
        
        // Then
        assertNotNull(pipelineConfig);
        assertEquals(1, pipelineConfig.transformations().size());
        assertEquals(1, pipelineConfig.outputs().size());
        assertEquals(1, pipelineConfig.globalSettings().size());
        
        assertEquals("test-pipeline", pipelineConfig.transformations().get(0).id());
        assertEquals("firestore", pipelineConfig.outputs().get(0).type());
        assertTrue((Boolean) pipelineConfig.globalSettings().get("enableHealthChecks"));
    }
    
    @Test
    void shouldProvideDefaultsForNullValues() {
        // When
        var pipelineConfig = new PipelineConfig(null, null, null);
        
        // Then
        assertNotNull(pipelineConfig.transformations());
        assertNotNull(pipelineConfig.outputs());
        assertNotNull(pipelineConfig.globalSettings());
        assertTrue(pipelineConfig.transformations().isEmpty());
        assertTrue(pipelineConfig.outputs().isEmpty());
        assertTrue(pipelineConfig.globalSettings().isEmpty());
    }
    
    @Test
    void shouldValidateTransformationPipelineConfig() {
        // Test valid config
        assertDoesNotThrow(() -> new PipelineConfig.TransformationPipelineConfig(
                "valid-id", "valid-type", "input", "output", 0, true, Map.of()));
        
        // Test invalid configs
        assertThrows(IllegalArgumentException.class, () -> 
                new PipelineConfig.TransformationPipelineConfig(
                        null, "type", "input", "output", 0, true, Map.of()));
        
        assertThrows(IllegalArgumentException.class, () -> 
                new PipelineConfig.TransformationPipelineConfig(
                        "", "type", "input", "output", 0, true, Map.of()));
        
        assertThrows(IllegalArgumentException.class, () -> 
                new PipelineConfig.TransformationPipelineConfig(
                        "id", null, "input", "output", 0, true, Map.of()));
        
        assertThrows(IllegalArgumentException.class, () -> 
                new PipelineConfig.TransformationPipelineConfig(
                        "id", "", "input", "output", 0, true, Map.of()));
        
        assertThrows(IllegalArgumentException.class, () -> 
                new PipelineConfig.TransformationPipelineConfig(
                        "id", "type", "input", "output", -1, true, Map.of()));
    }
    
    @Test
    void shouldValidateOutputConnectorConfig() {
        // Test valid config
        assertDoesNotThrow(() -> new PipelineConfig.OutputConnectorConfig(
                "valid-type", "valid-data-type", true, Map.of()));
        
        // Test invalid configs
        assertThrows(IllegalArgumentException.class, () -> 
                new PipelineConfig.OutputConnectorConfig(
                        null, "data-type", true, Map.of()));
        
        assertThrows(IllegalArgumentException.class, () -> 
                new PipelineConfig.OutputConnectorConfig(
                        "", "data-type", true, Map.of()));
        
        assertThrows(IllegalArgumentException.class, () -> 
                new PipelineConfig.OutputConnectorConfig(
                        "type", null, true, Map.of()));
        
        assertThrows(IllegalArgumentException.class, () -> 
                new PipelineConfig.OutputConnectorConfig(
                        "type", "", true, Map.of()));
    }
    
    @Test
    void shouldHandleNullSettingsMaps() {
        // When
        var transformationConfig = new PipelineConfig.TransformationPipelineConfig(
                "test-pipeline", "type", "input", "output", 0, true, null);
        
        var outputConfig = new PipelineConfig.OutputConnectorConfig(
                "test-connector", "data-type", true, null);
        
        // Then
        assertNull(transformationConfig.settings());
        assertNull(outputConfig.settings());
    }
}