package com.company.connector.registry;

import com.company.connector.connector.OutputConnector;
import com.company.connector.pipeline.DataTransformationPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for managing output connectors and transformation pipelines.
 * Provides discovery, registration, and lifecycle management for plugins.
 */
@Component
public class ConnectorRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectorRegistry.class);
    
    private final Map<String, OutputConnector<?>> connectors = new ConcurrentHashMap<>();
    private final Map<String, DataTransformationPipeline<?, ?>> pipelines = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<DataTransformationPipeline<?, ?>>> pipelinesByInputType = new ConcurrentHashMap<>();
    
    /**
     * Register an output connector.
     * 
     * @param connector The connector to register
     * @param <T> The data type the connector supports
     */
    public <T> void registerConnector(OutputConnector<T> connector) {
        String type = connector.getConnectorType();
        if (connectors.containsKey(type)) {
            logger.warn("Connector with type '{}' already registered, replacing", type);
        }
        
        connectors.put(type, connector);
        logger.info("Registered output connector: {}", type);
    }
    
    /**
     * Register a data transformation pipeline.
     * 
     * @param pipeline The pipeline to register
     * @param <T> Input data type
     * @param <R> Output data type
     */
    public <T, R> void registerPipeline(DataTransformationPipeline<T, R> pipeline) {
        String id = pipeline.getPipelineId();
        if (pipelines.containsKey(id)) {
            logger.warn("Pipeline with id '{}' already registered, replacing", id);
        }
        
        pipelines.put(id, pipeline);
        logger.info("Registered transformation pipeline: {}", id);
    }
    
    /**
     * Get an output connector by type.
     * 
     * @param connectorType The connector type identifier
     * @param dataType The expected data type class
     * @param <T> The data type
     * @return Optional containing the connector if found and type matches
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<OutputConnector<T>> getConnector(String connectorType, Class<T> dataType) {
        OutputConnector<?> connector = connectors.get(connectorType);
        if (connector != null && connector.getSupportedDataType().isAssignableFrom(dataType)) {
            return Optional.of((OutputConnector<T>) connector);
        }
        return Optional.empty();
    }
    
    /**
     * Get all registered connectors that support the given data type.
     * 
     * @param dataType The data type class
     * @param <T> The data type
     * @return List of connectors supporting the data type
     */
    @SuppressWarnings("unchecked")
    public <T> List<OutputConnector<T>> getConnectorsByDataType(Class<T> dataType) {
        return connectors.values().stream()
                .filter(connector -> connector.getSupportedDataType().isAssignableFrom(dataType))
                .map(connector -> (OutputConnector<T>) connector)
                .collect(Collectors.toList());
    }
    
    /**
     * Get a transformation pipeline by ID.
     * 
     * @param pipelineId The pipeline identifier
     * @param inputType The input data type class
     * @param outputType The output data type class
     * @param <T> Input data type
     * @param <R> Output data type
     * @return Optional containing the pipeline if found and types match
     */
    @SuppressWarnings("unchecked")
    public <T, R> Optional<DataTransformationPipeline<T, R>> getPipeline(
            String pipelineId, Class<T> inputType, Class<R> outputType) {
        DataTransformationPipeline<?, ?> pipeline = pipelines.get(pipelineId);
        if (pipeline != null) {
            // Use raw types for the supports check to avoid generic type issues
            DataTransformationPipeline rawPipeline = pipeline;
            if (rawPipeline.supports(inputType, outputType)) {
                return Optional.of((DataTransformationPipeline<T, R>) pipeline);
            }
        }
        return Optional.empty();
    }
    
    /**
     * Find the best transformation pipeline for the given input and output types.
     * Returns the pipeline with the highest priority that supports the transformation.
     * 
     * @param inputType The input data type class
     * @param outputType The output data type class
     * @param <T> Input data type
     * @param <R> Output data type
     * @return Optional containing the best pipeline if found
     */
    @SuppressWarnings("unchecked")
    public <T, R> Optional<DataTransformationPipeline<T, R>> findBestPipeline(
            Class<T> inputType, Class<R> outputType) {
        return pipelines.values().stream()
                .filter(pipeline -> {
                    // Use raw types for the supports check to avoid generic type issues
                    DataTransformationPipeline rawPipeline = pipeline;
                    return rawPipeline.supports(inputType, outputType);
                })
                .max(Comparator.comparingInt(DataTransformationPipeline::getPriority))
                .map(pipeline -> (DataTransformationPipeline<T, R>) pipeline);
    }
    
    /**
     * Get all registered connector types.
     * 
     * @return Set of connector type identifiers
     */
    public Set<String> getRegisteredConnectorTypes() {
        return new HashSet<>(connectors.keySet());
    }
    
    /**
     * Get all registered pipeline IDs.
     * 
     * @return Set of pipeline identifiers
     */
    public Set<String> getRegisteredPipelineIds() {
        return new HashSet<>(pipelines.keySet());
    }
    
    /**
     * Initialize all registered connectors.
     * 
     * @return CompletableFuture that completes when all connectors are initialized
     */
    public CompletableFuture<Void> initializeAll() {
        logger.info("Initializing {} connectors", connectors.size());
        
        List<CompletableFuture<Void>> futures = connectors.values().stream()
                .map(connector -> {
                    logger.debug("Initializing connector: {}", connector.getConnectorType());
                    return connector.initialize()
                            .exceptionally(throwable -> {
                                logger.error("Failed to initialize connector: {}", 
                                        connector.getConnectorType(), throwable);
                                return null;
                            });
                })
                .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> logger.info("All connectors initialized"));
    }
    
    /**
     * Shutdown all registered connectors.
     * 
     * @return CompletableFuture that completes when all connectors are shut down
     */
    public CompletableFuture<Void> shutdownAll() {
        logger.info("Shutting down {} connectors", connectors.size());
        
        List<CompletableFuture<Void>> futures = connectors.values().stream()
                .map(connector -> {
                    logger.debug("Shutting down connector: {}", connector.getConnectorType());
                    return connector.shutdown()
                            .exceptionally(throwable -> {
                                logger.error("Failed to shutdown connector: {}", 
                                        connector.getConnectorType(), throwable);
                                return null;
                            });
                })
                .collect(Collectors.toList());
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> logger.info("All connectors shut down"));
    }
    
    /**
     * Check health of all registered connectors.
     * 
     * @return CompletableFuture containing map of connector type to health status
     */
    public CompletableFuture<Map<String, Boolean>> checkHealth() {
        Map<String, CompletableFuture<Boolean>> healthChecks = connectors.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().isHealthy()
                                .exceptionally(throwable -> {
                                    logger.warn("Health check failed for connector: {}", 
                                            entry.getKey(), throwable);
                                    return false;
                                })
                ));
        
        return CompletableFuture.allOf(
                healthChecks.values().toArray(new CompletableFuture[0])
        ).thenApply(v -> {
            Map<String, Boolean> results = new HashMap<>();
            healthChecks.forEach((type, future) -> {
                try {
                    results.put(type, future.join());
                } catch (Exception e) {
                    logger.error("Error getting health status for connector: {}", type, e);
                    results.put(type, false);
                }
            });
            return results;
        });
    }
}