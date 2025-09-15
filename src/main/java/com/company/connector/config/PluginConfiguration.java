package com.company.connector.config;

import com.company.connector.connector.OutputConnector;
import com.company.connector.pipeline.DataTransformationPipeline;
import com.company.connector.registry.ConnectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;

/**
 * Configuration class that automatically discovers and registers
 * transformation pipelines and output connectors from the Spring context.
 */
@Configuration
@EnableConfigurationProperties(PipelineConfig.class)
public class PluginConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginConfiguration.class);
    
    private final ConnectorRegistry registry;
    private final PipelineConfig pipelineConfig;
    private final ApplicationContext applicationContext;
    
    public PluginConfiguration(ConnectorRegistry registry, 
                             PipelineConfig pipelineConfig,
                             ApplicationContext applicationContext) {
        this.registry = registry;
        this.pipelineConfig = pipelineConfig;
        this.applicationContext = applicationContext;
    }
    
    /**
     * Auto-discover and register all transformation pipelines and output connectors
     * when the Spring context is fully initialized.
     */
    @EventListener(ContextRefreshedEvent.class)
    public void registerPlugins() {
        logger.info("Auto-discovering and registering plugins...");
        
        registerTransformationPipelines();
        registerOutputConnectors();
        
        logger.info("Plugin registration completed. Registered {} pipelines and {} connectors",
                registry.getRegisteredPipelineIds().size(),
                registry.getRegisteredConnectorTypes().size());
    }
    
    /**
     * Discover and register all DataTransformationPipeline beans.
     */
    private void registerTransformationPipelines() {
        Map<String, DataTransformationPipeline> pipelines = 
                applicationContext.getBeansOfType(DataTransformationPipeline.class);
        
        for (DataTransformationPipeline<?, ?> pipeline : pipelines.values()) {
            if (isPipelineEnabled(pipeline.getPipelineId())) {
                registry.registerPipeline(pipeline);
                logger.debug("Registered transformation pipeline: {}", pipeline.getPipelineId());
            } else {
                logger.info("Skipping disabled transformation pipeline: {}", pipeline.getPipelineId());
            }
        }
        
        logger.info("Registered {} transformation pipelines", pipelines.size());
    }
    
    /**
     * Discover and register all OutputConnector beans.
     */
    private void registerOutputConnectors() {
        Map<String, OutputConnector> connectors = 
                applicationContext.getBeansOfType(OutputConnector.class);
        
        for (OutputConnector<?> connector : connectors.values()) {
            if (isConnectorEnabled(connector.getConnectorType())) {
                registry.registerConnector(connector);
                logger.debug("Registered output connector: {}", connector.getConnectorType());
            } else {
                logger.info("Skipping disabled output connector: {}", connector.getConnectorType());
            }
        }
        
        logger.info("Registered {} output connectors", connectors.size());
    }
    
    /**
     * Check if a transformation pipeline is enabled in configuration.
     */
    private boolean isPipelineEnabled(String pipelineId) {
        return pipelineConfig.transformations().stream()
                .filter(config -> config.id().equals(pipelineId))
                .findFirst()
                .map(PipelineConfig.TransformationPipelineConfig::enabled)
                .orElse(true); // Default to enabled if not explicitly configured
    }
    
    /**
     * Check if an output connector is enabled in configuration.
     */
    private boolean isConnectorEnabled(String connectorType) {
        return pipelineConfig.outputs().stream()
                .filter(config -> config.type().equals(connectorType))
                .findFirst()
                .map(PipelineConfig.OutputConnectorConfig::enabled)
                .orElse(true); // Default to enabled if not explicitly configured
    }
}