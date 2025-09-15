package com.company.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for transformation pipelines and output connectors.
 */
@ConfigurationProperties(prefix = "connector.pipeline")
public record PipelineConfig(
        List<TransformationPipelineConfig> transformations,
        List<OutputConnectorConfig> outputs,
        Map<String, Object> globalSettings
) {
    
    /**
     * Configuration for a single transformation pipeline.
     */
    public record TransformationPipelineConfig(
            String id,
            String type,
            String inputType,
            String outputType,
            int priority,
            boolean enabled,
            Map<String, Object> settings
    ) {
        public TransformationPipelineConfig {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Pipeline id cannot be null or blank");
            }
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Pipeline type cannot be null or blank");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("Pipeline priority cannot be negative");
            }
        }
    }
    
    /**
     * Configuration for an output connector.
     */
    public record OutputConnectorConfig(
            String type,
            String dataType,
            boolean enabled,
            Map<String, Object> settings
    ) {
        public OutputConnectorConfig {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("Connector type cannot be null or blank");
            }
            if (dataType == null || dataType.isBlank()) {
                throw new IllegalArgumentException("Data type cannot be null or blank");
            }
        }
    }
    
    public PipelineConfig {
        // Provide defaults for null values
        if (transformations == null) {
            transformations = List.of();
        }
        if (outputs == null) {
            outputs = List.of();
        }
        if (globalSettings == null) {
            globalSettings = Map.of();
        }
    }
}