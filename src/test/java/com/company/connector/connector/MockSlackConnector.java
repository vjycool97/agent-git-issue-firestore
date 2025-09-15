package com.company.connector.connector;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock Slack output connector for testing extensibility.
 * Simulates sending messages to Slack channels.
 */
public class MockSlackConnector implements OutputConnector<String> {
    
    private static final String CONNECTOR_TYPE = "slack";
    
    private final Set<String> sentMessages = ConcurrentHashMap.newKeySet();
    private boolean healthy = true;
    
    @Override
    public CompletableFuture<Void> write(String data) {
        return CompletableFuture.runAsync(() -> {
            if (!healthy) {
                throw new RuntimeException("Slack connector is not healthy");
            }
            
            // Simulate sending message to Slack
            sentMessages.add(data);
        });
    }
    
    @Override
    public CompletableFuture<Void> writeBatch(List<String> data) {
        return CompletableFuture.runAsync(() -> {
            if (!healthy) {
                throw new RuntimeException("Slack connector is not healthy");
            }
            
            // Simulate batch sending messages to Slack
            sentMessages.addAll(data);
        });
    }
    
    @Override
    public CompletableFuture<Boolean> exists(String id) {
        return CompletableFuture.supplyAsync(() -> {
            // For Slack, we can't really check if a message exists by ID
            // This is just for testing purposes
            return sentMessages.contains(id);
        });
    }
    
    @Override
    public String getConnectorType() {
        return CONNECTOR_TYPE;
    }
    
    @Override
    public Class<String> getSupportedDataType() {
        return String.class;
    }
    
    @Override
    public CompletableFuture<Boolean> isHealthy() {
        return CompletableFuture.completedFuture(healthy);
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            // Simulate Slack API initialization
            healthy = true;
        });
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            // Simulate cleanup
            sentMessages.clear();
            healthy = false;
        });
    }
    
    // Test helper methods
    public Set<String> getSentMessages() {
        return sentMessages;
    }
    
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
    
    public void clearMessages() {
        sentMessages.clear();
    }
}