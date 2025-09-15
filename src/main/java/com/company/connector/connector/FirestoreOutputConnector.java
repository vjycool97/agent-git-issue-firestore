package com.company.connector.connector;

import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.repository.FirestoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Output connector for writing data to Firebase Firestore.
 * Delegates to the existing FirestoreRepository for actual operations.
 */
@Component
public class FirestoreOutputConnector implements OutputConnector<FirestoreIssueDocument> {
    
    private static final Logger logger = LoggerFactory.getLogger(FirestoreOutputConnector.class);
    private static final String CONNECTOR_TYPE = "firestore";
    
    private final FirestoreRepository firestoreRepository;
    
    public FirestoreOutputConnector(FirestoreRepository firestoreRepository) {
        this.firestoreRepository = firestoreRepository;
    }
    
    @Override
    public CompletableFuture<Void> write(FirestoreIssueDocument data) {
        logger.debug("Writing single document to Firestore: {}", data.id());
        return firestoreRepository.save(data)
                .thenRun(() -> logger.debug("Successfully wrote document: {}", data.id()));
    }
    
    @Override
    public CompletableFuture<Void> writeBatch(List<FirestoreIssueDocument> data) {
        logger.debug("Writing batch of {} documents to Firestore", data.size());
        return firestoreRepository.saveBatch(data)
                .thenRun(() -> logger.debug("Successfully wrote batch of {} documents", data.size()));
    }
    
    @Override
    public CompletableFuture<Boolean> exists(String id) {
        logger.debug("Checking if document exists in Firestore: {}", id);
        return firestoreRepository.exists(id);
    }
    
    @Override
    public String getConnectorType() {
        return CONNECTOR_TYPE;
    }
    
    @Override
    public Class<FirestoreIssueDocument> getSupportedDataType() {
        return FirestoreIssueDocument.class;
    }
    
    @Override
    public CompletableFuture<Boolean> isHealthy() {
        // Simple health check - try to perform a lightweight operation
        return CompletableFuture.supplyAsync(() -> {
            try {
                // We could add a simple ping operation to FirestoreRepository if needed
                // For now, assume healthy if repository is available
                return firestoreRepository != null;
            } catch (Exception e) {
                logger.warn("Firestore connector health check failed", e);
                return false;
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        logger.info("Initializing Firestore output connector");
        return CompletableFuture.runAsync(() -> {
            // Firestore repository initialization is handled by Spring
            logger.info("Firestore output connector initialized successfully");
        });
    }
    
    @Override
    public CompletableFuture<Void> shutdown() {
        logger.info("Shutting down Firestore output connector");
        return CompletableFuture.runAsync(() -> {
            // Firestore repository cleanup is handled by Spring
            logger.info("Firestore output connector shut down successfully");
        });
    }
}