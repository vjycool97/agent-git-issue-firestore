package com.company.connector.service;

import com.company.connector.client.GitHubClient;
import com.company.connector.exception.SyncException;
import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.model.GitHubIssue;
import com.company.connector.model.SyncResult;
import com.company.connector.monitoring.SyncMetricsService;
import com.company.connector.repository.FirestoreRepository;
import com.company.connector.transformer.IssueTransformer;
import com.company.connector.transformer.TransformationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Implementation of IssueService that orchestrates the complete sync process.
 * 
 * This service coordinates between GitHub API client, data transformation,
 * and Firestore repository to synchronize GitHub issues. It uses Virtual Threads
 * for concurrent processing and implements comprehensive error handling with
 * retry mechanisms and graceful error recovery.
 */
@Service
public class IssueServiceImpl implements IssueService {
    
    private static final Logger logger = LoggerFactory.getLogger(IssueServiceImpl.class);
    
    private final GitHubClient gitHubClient;
    private final IssueTransformer issueTransformer;
    private final FirestoreRepository firestoreRepository;
    private final RetryTemplate retryTemplate;
    private final ErrorHandler errorHandler;
    private final SyncMetricsService metricsService;
    
    public IssueServiceImpl(
            GitHubClient gitHubClient,
            IssueTransformer issueTransformer,
            FirestoreRepository firestoreRepository,
            @Qualifier("syncRetryTemplate") RetryTemplate retryTemplate,
            ErrorHandler errorHandler,
            SyncMetricsService metricsService) {
        this.gitHubClient = gitHubClient;
        this.issueTransformer = issueTransformer;
        this.firestoreRepository = firestoreRepository;
        this.retryTemplate = retryTemplate;
        this.errorHandler = errorHandler;
        this.metricsService = metricsService;
    }
    
    @Override
    public CompletableFuture<SyncResult> syncIssues(String owner, String repo) {
        return syncIssues(owner, repo, 5);
    }
    
    @Override
    public CompletableFuture<SyncResult> syncIssues(String owner, String repo, int limit) {
        validateInputs(owner, repo, limit);
        
        // Set up correlation ID for tracking
        String correlationId = java.util.UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        
        Instant startTime = Instant.now();
        logger.info("Starting sync operation for repository {}/{} with limit {} [correlationId={}]", 
                   owner, repo, limit, correlationId);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Ensure MDC is available in the async context
                MDC.put("correlationId", correlationId);
                
                SyncResult result = retryTemplate.execute(context -> {
                    logger.debug("Attempting sync operation (attempt {}) [correlationId={}]", 
                               context.getRetryCount() + 1, correlationId);
                    
                    // Fetch issues from GitHub
                    List<GitHubIssue> issues = gitHubClient.fetchRecentIssues(owner, repo, limit)
                        .join(); // Block here since we're in a retry template
                    
                    logger.debug("Fetched {} issues from GitHub [correlationId={}]", issues.size(), correlationId);
                    
                    // Process the sync operation
                    return processSyncOperationSync(issues, startTime);
                });
                
                // Record metrics based on result
                recordSyncMetrics(result);
                
                return result;
            } catch (Exception e) {
                Duration duration = Duration.between(startTime, Instant.now());
                String context = String.format("syncing issues from %s/%s", owner, repo);
                
                SyncException syncException = errorHandler.handleSyncError(e, context);
                errorHandler.logStructuredError(syncException, "syncIssues", context);
                errorHandler.logTroubleshootingGuidance(syncException, "syncIssues");
                
                String errorMessage = "Sync operation failed: " + syncException.getMessage();
                logger.error("Sync operation failed [correlationId={}]: {}", correlationId, errorMessage, syncException);
                
                SyncResult.Failure failure = new SyncResult.Failure(errorMessage, duration);
                metricsService.recordSyncFailure(errorMessage, duration);
                
                return failure;
            } finally {
                MDC.clear();
            }
        });
    }
    
    /**
     * Processes the complete sync operation for the fetched issues (synchronous version for retry template).
     */
    private SyncResult processSyncOperationSync(List<GitHubIssue> issues, Instant startTime) {
        if (issues.isEmpty()) {
            Duration duration = Duration.between(startTime, Instant.now());
            logger.info("No issues to sync");
            return new SyncResult.Success(0, duration);
        }
        
        // Transform issues to Firestore documents with error handling
        List<FirestoreIssueDocument> documents;
        try {
            documents = issueTransformer.transformBatch(issues);
            logger.debug("Transformed {} issues to Firestore documents", documents.size());
        } catch (TransformationException e) {
            Duration duration = Duration.between(startTime, Instant.now());
            String errorMessage = "Failed to transform issues: " + e.getMessage();
            logger.error(errorMessage, e);
            
            // Log structured error for transformation failures
            SyncException syncException = new SyncException(errorMessage, e);
            errorHandler.logStructuredError(syncException, "transformBatch", "issue transformation");
            
            return new SyncResult.Failure(errorMessage, duration);
        }
        
        // Process documents concurrently using Virtual Threads
        return processDocumentsConcurrentlySync(documents, startTime);
    }
    
    /**
     * Processes the complete sync operation for the fetched issues (asynchronous version).
     */
    private CompletableFuture<SyncResult> processSyncOperation(List<GitHubIssue> issues, Instant startTime) {
        if (issues.isEmpty()) {
            Duration duration = Duration.between(startTime, Instant.now());
            logger.info("No issues to sync");
            return CompletableFuture.completedFuture(new SyncResult.Success(0, duration));
        }
        
        // Transform issues to Firestore documents
        List<FirestoreIssueDocument> documents;
        try {
            documents = issueTransformer.transformBatch(issues);
            logger.debug("Transformed {} issues to Firestore documents", documents.size());
        } catch (TransformationException e) {
            Duration duration = Duration.between(startTime, Instant.now());
            String errorMessage = "Failed to transform issues: " + e.getMessage();
            logger.error(errorMessage, e);
            return CompletableFuture.completedFuture(new SyncResult.Failure(errorMessage, duration));
        }
        
        // Process documents concurrently using Virtual Threads
        return processDocumentsConcurrently(documents, startTime);
    }
    
    /**
     * Processes documents concurrently using Virtual Threads for duplicate detection and saving (synchronous version).
     */
    private SyncResult processDocumentsConcurrentlySync(
            List<FirestoreIssueDocument> documents, Instant startTime) {
        
        List<CompletableFuture<ProcessResult>> futures = documents.stream()
            .map(this::processDocumentWithErrorHandling)
            .toList();
        
        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        List<ProcessResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();
        
        return createSyncResult(results, startTime);
    }
    
    /**
     * Processes documents concurrently using Virtual Threads for duplicate detection and saving.
     */
    private CompletableFuture<SyncResult> processDocumentsConcurrently(
            List<FirestoreIssueDocument> documents, Instant startTime) {
        
        List<CompletableFuture<ProcessResult>> futures = documents.stream()
            .map(this::processDocumentWithErrorHandling)
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(ignored -> {
                List<ProcessResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
                
                return createSyncResult(results, startTime);
            });
    }
    
    /**
     * Processes a single document with comprehensive error handling: checks for duplicates and saves if needed.
     */
    private CompletableFuture<ProcessResult> processDocumentWithErrorHandling(FirestoreIssueDocument document) {
        return firestoreRepository.exists(document.id())
            .thenCompose(exists -> {
                if (exists) {
                    logger.debug("Issue {} already exists, updating", document.id());
                    return firestoreRepository.save(document)
                        .thenApply(ignored -> ProcessResult.updated(document.id()));
                } else {
                    logger.debug("Issue {} is new, creating", document.id());
                    return firestoreRepository.save(document)
                        .thenApply(ignored -> ProcessResult.created(document.id()));
                }
            })
            .exceptionally(throwable -> {
                String context = String.format("processing document %s", document.id());
                String errorMessage = "Failed to process document " + document.id() + ": " + throwable.getMessage();
                
                // Log structured error for document processing failures
                try {
                    SyncException syncException = errorHandler.handleSyncError(throwable, context);
                    errorHandler.logStructuredError(syncException, "processDocument", context);
                } catch (Exception e) {
                    logger.error("Error while handling document processing error: {}", e.getMessage(), e);
                }
                
                logger.error(errorMessage, throwable);
                return ProcessResult.failed(document.id(), errorMessage);
            });
    }
    
    /**
     * Processes a single document: checks for duplicates and saves if needed (legacy method for backward compatibility).
     */
    private CompletableFuture<ProcessResult> processDocument(FirestoreIssueDocument document) {
        return processDocumentWithErrorHandling(document);
    }
    
    /**
     * Creates the final SyncResult based on individual processing results.
     */
    private SyncResult createSyncResult(List<ProcessResult> results, Instant startTime) {
        Duration duration = Duration.between(startTime, Instant.now());
        
        List<ProcessResult> successful = results.stream()
            .filter(ProcessResult::isSuccess)
            .toList();
        
        List<ProcessResult> failed = results.stream()
            .filter(result -> !result.isSuccess())
            .toList();
        
        int successCount = successful.size();
        int failureCount = failed.size();
        
        String correlationId = MDC.get("correlationId");
        
        if (failureCount == 0) {
            logger.info("SYNC_SUCCESS: {} issues processed in {} [correlationId={}]", 
                       successCount, duration, correlationId);
            return new SyncResult.Success(successCount, duration);
        } else if (successCount > 0) {
            List<String> errors = failed.stream()
                .map(ProcessResult::errorMessage)
                .toList();
            logger.warn("SYNC_PARTIAL_FAILURE: {} successful, {} failed in {} [correlationId={}]", 
                       successCount, failureCount, duration, correlationId);
            return new SyncResult.PartialFailure(successCount, failureCount, errors, duration);
        } else {
            String errorMessage = "All sync operations failed";
            logger.error("SYNC_COMPLETE_FAILURE: {} failures in {} [correlationId={}]", 
                        failureCount, duration, correlationId);
            return new SyncResult.Failure(errorMessage, duration);
        }
    }
    
    /**
     * Records metrics based on sync result.
     */
    private void recordSyncMetrics(SyncResult result) {
        switch (result) {
            case SyncResult.Success success -> 
                metricsService.recordSyncSuccess(success.processedCount(), success.duration());
            case SyncResult.PartialFailure partialFailure -> 
                metricsService.recordPartialSyncFailure(
                    partialFailure.processedCount(), 
                    partialFailure.failedCount(), 
                    partialFailure.duration()
                );
            case SyncResult.Failure failure -> 
                metricsService.recordSyncFailure(failure.error(), failure.duration());
        }
    }
    
    /**
     * Validates input parameters for sync operations.
     */
    private void validateInputs(String owner, String repo, int limit) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("Owner cannot be null or blank");
        }
        if (repo == null || repo.isBlank()) {
            throw new IllegalArgumentException("Repository name cannot be null or blank");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
    }
    
    /**
     * Internal record to track the result of processing individual documents.
     */
    private record ProcessResult(
        String documentId,
        boolean isSuccess,
        String errorMessage,
        ProcessType type
    ) {
        
        enum ProcessType {
            CREATED, UPDATED, FAILED
        }
        
        static ProcessResult created(String documentId) {
            return new ProcessResult(documentId, true, null, ProcessType.CREATED);
        }
        
        static ProcessResult updated(String documentId) {
            return new ProcessResult(documentId, true, null, ProcessType.UPDATED);
        }
        
        static ProcessResult failed(String documentId, String errorMessage) {
            return new ProcessResult(documentId, false, errorMessage, ProcessType.FAILED);
        }
    }
}