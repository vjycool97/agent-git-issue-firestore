package com.company.connector.repository;

import com.company.connector.config.FirebaseConfig;
import com.company.connector.model.FirestoreIssueDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Basic tests for FirestoreRepository interface and configuration.
 */
class FirestoreRepositoryTest {
    
    @Test
    void firestoreRepository_Creation_Success() {
        // Arrange
        FirebaseConfig config = new FirebaseConfig(
            "test-service-account.json",
            "test-project",
            "test_issues",
            new FirebaseConfig.ConnectionPoolConfig(50, java.time.Duration.ofSeconds(10))
        );
        
        // Act & Assert - Just test that we can create the config
        assertThat(config.collectionName()).isEqualTo("test_issues");
        assertThat(config.projectId()).isEqualTo("test-project");
    }
    
    @Test
    void firestoreIssueDocument_Creation_Success() {
        // Arrange & Act
        FirestoreIssueDocument document = new FirestoreIssueDocument(
            "123",
            "Test Issue",
            "open",
            "https://github.com/owner/repo/issues/123",
            Instant.now(),
            Instant.now()
        );
        
        // Assert
        assertThat(document.id()).isEqualTo("123");
        assertThat(document.title()).isEqualTo("Test Issue");
        assertThat(document.state()).isEqualTo("open");
    }
}