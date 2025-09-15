package com.company.connector.repository;

import com.company.connector.model.FirestoreIssueDocument;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Repository interface for Firestore operations.
 */
public interface FirestoreRepository {
    
    /**
     * Saves a single issue document to Firestore.
     */
    CompletableFuture<Void> save(FirestoreIssueDocument document);
    
    /**
     * Finds an issue document by ID.
     */
    CompletableFuture<Optional<FirestoreIssueDocument>> findById(String id);
    
    /**
     * Checks if a document exists by ID.
     */
    CompletableFuture<Boolean> exists(String id);
    
    /**
     * Saves multiple documents in a batch operation.
     */
    CompletableFuture<Void> saveBatch(List<FirestoreIssueDocument> documents);
}