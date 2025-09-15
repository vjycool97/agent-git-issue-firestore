package com.company.connector.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration properties for Firebase Firestore integration.
 * Uses Java Records for immutable configuration with validation.
 */
@ConfigurationProperties(prefix = "firebase")
@Validated
public record FirebaseConfig(
        @NotBlank(message = "Firebase service account path is required")
        String serviceAccountPath,
        
        @NotBlank(message = "Firebase project ID is required")
        String projectId,
        
        @NotBlank(message = "Firestore collection name is required")
        String collectionName,
        
        @Valid
        @NotNull(message = "Connection pool configuration is required")
        ConnectionPoolConfig connectionPool
) {
    
    /**
     * Connection pool configuration for Firestore connections.
     */
    public record ConnectionPoolConfig(
            @Min(value = 1, message = "Max connections must be at least 1")
            @Max(value = 200, message = "Max connections cannot exceed 200")
            int maxConnections,
            
            @NotNull(message = "Connection timeout is required")
            Duration connectionTimeout
    ) {}
}