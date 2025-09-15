package com.company.connector.connector;

import com.company.connector.model.FirestoreIssueDocument;
import com.company.connector.repository.FirestoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirestoreOutputConnectorTest {
    
    @Mock
    private FirestoreRepository firestoreRepository;
    
    private FirestoreOutputConnector connector;
    
    @BeforeEach
    void setUp() {
        connector = new FirestoreOutputConnector(firestoreRepository);
    }
    
    @Test
    void shouldWriteSingleDocument() {
        // Given
        FirestoreIssueDocument document = new FirestoreIssueDocument(
                "123", "Test Issue", "open", "https://github.com/test/repo/issues/123",
                Instant.now(), Instant.now()
        );
        
        when(firestoreRepository.save(any(FirestoreIssueDocument.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        // When
        CompletableFuture<Void> result = connector.write(document);
        
        // Then
        assertDoesNotThrow(() -> result.join());
        verify(firestoreRepository).save(document);
    }
    
    @Test
    void shouldWriteBatchOfDocuments() {
        // Given
        List<FirestoreIssueDocument> documents = List.of(
                new FirestoreIssueDocument("123", "Issue 1", "open", "https://github.com/test/repo/issues/123", Instant.now(), Instant.now()),
                new FirestoreIssueDocument("124", "Issue 2", "closed", "https://github.com/test/repo/issues/124", Instant.now(), Instant.now())
        );
        
        when(firestoreRepository.saveBatch(anyList()))
                .thenReturn(CompletableFuture.completedFuture(null));
        
        // When
        CompletableFuture<Void> result = connector.writeBatch(documents);
        
        // Then
        assertDoesNotThrow(() -> result.join());
        verify(firestoreRepository).saveBatch(documents);
    }
    
    @Test
    void shouldCheckIfDocumentExists() {
        // Given
        String documentId = "123";
        when(firestoreRepository.exists(anyString()))
                .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        CompletableFuture<Boolean> result = connector.exists(documentId);
        
        // Then
        assertTrue(result.join());
        verify(firestoreRepository).exists(documentId);
    }
    
    @Test
    void shouldReturnCorrectConnectorType() {
        // When & Then
        assertEquals("firestore", connector.getConnectorType());
    }
    
    @Test
    void shouldReturnCorrectSupportedDataType() {
        // When & Then
        assertEquals(FirestoreIssueDocument.class, connector.getSupportedDataType());
    }
    
    @Test
    void shouldReportHealthyWhenRepositoryIsAvailable() {
        // When
        CompletableFuture<Boolean> result = connector.isHealthy();
        
        // Then
        assertTrue(result.join());
    }
    
    @Test
    void shouldInitializeSuccessfully() {
        // When
        CompletableFuture<Void> result = connector.initialize();
        
        // Then
        assertDoesNotThrow(() -> result.join());
    }
    
    @Test
    void shouldShutdownSuccessfully() {
        // When
        CompletableFuture<Void> result = connector.shutdown();
        
        // Then
        assertDoesNotThrow(() -> result.join());
    }
}