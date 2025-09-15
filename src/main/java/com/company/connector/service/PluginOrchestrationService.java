package com.company.connector.service;

import com.company.connector.connector.OutputConnector;
import com.company.connector.pipeline.DataTransformationPipeline;
import com.company.connector.registry.ConnectorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Service that orchestrates data transformation and output using the plugin architecture.
 * Provides a high-level API for processing data through transformation pipelines
 * and writing to output connectors.
 */
@Service
public class PluginOrchestrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(PluginOrchestrationService.class);
    
    private final ConnectorRegistry registry;
    
    public PluginOrchestrationService(ConnectorRegistry registry) {
        this.registry = registry;
    }
    
    /**
     * Process data through a transformation pipeline and write to an output connector.
     * 
     * @param inputData The input data to process
     * @param inputType The input data type class
     * @param outputType The output data type class
     * @param connectorType The target connector type
     * @param <T> Input data type
     * @param <R> Output data type
     * @return CompletableFuture that completes when processing is done
     */
    public <T, R> CompletableFuture<Void> processData(
            T inputData, 
            Class<T> inputType, 
            Class<R> outputType, 
            String connectorType) {
        
        logger.debug("Processing single item: {} -> {} via {}", 
                inputType.getSimpleName(), outputType.getSimpleName(), connectorType);
        
        // Find the best transformation pipeline
        Optional<DataTransformationPipeline<T, R>> pipeline = 
                registry.findBestPipeline(inputType, outputType);
        
        if (pipeline.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No transformation pipeline found for " + 
                            inputType.getSimpleName() + " -> " + outputType.getSimpleName()));
        }
        
        // Find the output connector
        Optional<OutputConnector<R>> connector = registry.getConnector(connectorType, outputType);
        
        if (connector.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No output connector found for type: " + connectorType));
        }
        
        // Transform and write
        return pipeline.get().transform(inputData)
                .thenCompose(transformedData -> connector.get().write(transformedData))
                .thenRun(() -> logger.debug("Successfully processed single item"));
    }
    
    /**
     * Process a batch of data through a transformation pipeline and write to an output connector.
     * 
     * @param inputData The input data batch to process
     * @param inputType The input data type class
     * @param outputType The output data type class
     * @param connectorType The target connector type
     * @param <T> Input data type
     * @param <R> Output data type
     * @return CompletableFuture that completes when processing is done
     */
    public <T, R> CompletableFuture<Void> processBatch(
            List<T> inputData, 
            Class<T> inputType, 
            Class<R> outputType, 
            String connectorType) {
        
        logger.debug("Processing batch of {} items: {} -> {} via {}", 
                inputData.size(), inputType.getSimpleName(), outputType.getSimpleName(), connectorType);
        
        // Find the best transformation pipeline
        Optional<DataTransformationPipeline<T, R>> pipeline = 
                registry.findBestPipeline(inputType, outputType);
        
        if (pipeline.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No transformation pipeline found for " + 
                            inputType.getSimpleName() + " -> " + outputType.getSimpleName()));
        }
        
        // Find the output connector
        Optional<OutputConnector<R>> connector = registry.getConnector(connectorType, outputType);
        
        if (connector.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No output connector found for type: " + connectorType));
        }
        
        // Transform and write batch
        return pipeline.get().transformBatch(inputData)
                .thenCompose(transformedData -> connector.get().writeBatch(transformedData))
                .thenRun(() -> logger.debug("Successfully processed batch of {} items", inputData.size()));
    }
    
    /**
     * Check if data already exists in the target connector.
     * 
     * @param id The unique identifier for the data
     * @param connectorType The connector type to check
     * @param outputType The output data type class
     * @param <R> Output data type
     * @return CompletableFuture containing true if data exists
     */
    public <R> CompletableFuture<Boolean> dataExists(String id, String connectorType, Class<R> outputType) {
        Optional<OutputConnector<R>> connector = registry.getConnector(connectorType, outputType);
        
        if (connector.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No output connector found for type: " + connectorType));
        }
        
        return connector.get().exists(id);
    }
    
    /**
     * Get available transformation pipelines for the given input and output types.
     * 
     * @param inputType The input data type class
     * @param outputType The output data type class
     * @param <T> Input data type
     * @param <R> Output data type
     * @return List of available pipeline IDs
     */
    public <T, R> List<String> getAvailablePipelines(Class<T> inputType, Class<R> outputType) {
        return registry.getRegisteredPipelineIds().stream()
                .filter(pipelineId -> {
                    Optional<DataTransformationPipeline<T, R>> pipeline = 
                            registry.getPipeline(pipelineId, inputType, outputType);
                    return pipeline.isPresent();
                })
                .toList();
    }
    
    /**
     * Get available output connectors for the given data type.
     * 
     * @param dataType The data type class
     * @param <T> Data type
     * @return List of available connector types
     */
    public <T> List<String> getAvailableConnectors(Class<T> dataType) {
        return registry.getConnectorsByDataType(dataType).stream()
                .map(OutputConnector::getConnectorType)
                .toList();
    }
}