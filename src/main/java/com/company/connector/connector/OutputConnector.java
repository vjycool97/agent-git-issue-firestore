package com.company.connector.connector;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for output connectors that can write data to various destinations.
 * Supports both single item and batch operations for efficiency.
 * 
 * @param <T> The type of data this connector can write
 */
public interface OutputConnector<T> {
    
    /**
     * Write a single data item to the output destination.
     * 
     * @param data The data to write
     * @return CompletableFuture that completes when write is successful
     */
    CompletableFuture<Void> write(T data);
    
    /**
     * Write a batch of data items to the output destination.
     * Implementations should optimize for batch operations when possible.
     * 
     * @param data List of data items to write
     * @return CompletableFuture that completes when all writes are successful
     */
    CompletableFuture<Void> writeBatch(List<T> data);
    
    /**
     * Check if a data item with the given identifier already exists.
     * 
     * @param id The unique identifier for the data item
     * @return CompletableFuture containing true if the item exists
     */
    CompletableFuture<Boolean> exists(String id);
    
    /**
     * Get the unique type identifier for this connector.
     * 
     * @return Connector type identifier (e.g., "firestore", "jira", "slack")
     */
    String getConnectorType();
    
    /**
     * Get the data type this connector supports.
     * 
     * @return The class of data type this connector can write
     */
    Class<T> getSupportedDataType();
    
    /**
     * Check if this connector is currently healthy and ready to accept writes.
     * 
     * @return CompletableFuture containing true if connector is healthy
     */
    CompletableFuture<Boolean> isHealthy();
    
    /**
     * Initialize the connector with any required setup.
     * Called once during application startup.
     * 
     * @return CompletableFuture that completes when initialization is done
     */
    default CompletableFuture<Void> initialize() {
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * Clean up resources when the connector is no longer needed.
     * Called during application shutdown.
     * 
     * @return CompletableFuture that completes when cleanup is done
     */
    default CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }
}