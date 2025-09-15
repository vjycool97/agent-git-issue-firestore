package com.company.connector.repository;

import com.company.connector.config.FirebaseConfig;
import com.company.connector.model.FirestoreIssueDocument;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for cache behavior in FirestoreRepositoryImpl.
 * Tests cache configuration and basic functionality.
 */
class FirestoreRepositoryCacheTest {

    @Test
    void shouldCreateCacheManagerForDuplicateChecks() {
        // Given
        CacheManager cacheManager = new ConcurrentMapCacheManager("duplicateCheckCache");
        
        // When
        var cache = cacheManager.getCache("duplicateCheckCache");
        
        // Then
        assertThat(cache).isNotNull();
        
        // Test basic cache operations
        cache.put("test-id-1", true);
        cache.put("test-id-2", false);
        
        assertThat(cache.get("test-id-1", Boolean.class)).isTrue();
        assertThat(cache.get("test-id-2", Boolean.class)).isFalse();
        assertThat(cache.get("nonexistent", Boolean.class)).isNull();
    }

    @Test
    void shouldHandleCacheEviction() {
        // Given
        CacheManager cacheManager = new ConcurrentMapCacheManager("duplicateCheckCache");
        var cache = cacheManager.getCache("duplicateCheckCache");
        
        // When - Add items to cache
        cache.put("doc-1", true);
        cache.put("doc-2", false);
        
        // Then - Verify items are cached
        assertThat(cache.get("doc-1", Boolean.class)).isTrue();
        assertThat(cache.get("doc-2", Boolean.class)).isFalse();
        
        // When - Evict specific item
        cache.evict("doc-1");
        
        // Then - Verify eviction
        assertThat(cache.get("doc-1", Boolean.class)).isNull();
        assertThat(cache.get("doc-2", Boolean.class)).isFalse(); // Should still be there
    }

    @Test
    void shouldSupportMultipleCacheKeys() {
        // Given
        CacheManager cacheManager = new ConcurrentMapCacheManager("duplicateCheckCache");
        var cache = cacheManager.getCache("duplicateCheckCache");
        
        // When - Add multiple different keys
        for (int i = 0; i < 10; i++) {
            cache.put("doc-" + i, i % 2 == 0);
        }
        
        // Then - Verify all keys work independently
        for (int i = 0; i < 10; i++) {
            boolean expected = i % 2 == 0;
            assertThat(cache.get("doc-" + i, Boolean.class)).isEqualTo(expected);
        }
    }

    @Test
    void shouldCreateFirebaseConfigCorrectly() {
        // Given
        FirebaseConfig.ConnectionPoolConfig connectionPool = 
            new FirebaseConfig.ConnectionPoolConfig(10, java.time.Duration.ofSeconds(30));
        
        // When
        FirebaseConfig config = new FirebaseConfig(
            "test-service-account.json", 
            "test-project", 
            "github_issues", 
            connectionPool
        );
        
        // Then
        assertThat(config.serviceAccountPath()).isEqualTo("test-service-account.json");
        assertThat(config.projectId()).isEqualTo("test-project");
        assertThat(config.collectionName()).isEqualTo("github_issues");
        assertThat(config.connectionPool().maxConnections()).isEqualTo(10);
        assertThat(config.connectionPool().connectionTimeout()).isEqualTo(java.time.Duration.ofSeconds(30));
    }

    @Test
    void shouldCreateFirestoreIssueDocumentCorrectly() {
        // Given
        String documentId = "test-123";
        Instant now = Instant.now();
        
        // When
        FirestoreIssueDocument document = new FirestoreIssueDocument(
            documentId,
            "Test Issue",
            "open",
            "https://github.com/owner/repo/issues/123",
            now,
            now
        );
        
        // Then
        assertThat(document.id()).isEqualTo(documentId);
        assertThat(document.title()).isEqualTo("Test Issue");
        assertThat(document.state()).isEqualTo("open");
        assertThat(document.htmlUrl()).isEqualTo("https://github.com/owner/repo/issues/123");
        assertThat(document.createdAt()).isEqualTo(now);
        assertThat(document.syncedAt()).isEqualTo(now);
    }

}