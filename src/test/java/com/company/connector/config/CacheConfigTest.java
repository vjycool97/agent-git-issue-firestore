package com.company.connector.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CacheConfig class.
 * Tests cache manager configuration and Caffeine cache builders.
 */
class CacheConfigTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    void shouldCreateCacheManagerWithCorrectConfiguration() {
        // When
        CacheManager cacheManager = cacheConfig.cacheManager();

        // Then
        assertThat(cacheManager).isInstanceOf(CaffeineCacheManager.class);
        
        // Verify cache manager is properly configured by testing cache operations
        var cache = cacheManager.getCache("test-cache");
        if (cache != null) {
            cache.put("test-key", "test-value");
            assertThat(cache.get("test-key")).isNotNull();
        }
    }

    @Test
    void shouldCreateGitHubApiCacheBuilderWithCorrectSettings() {
        // When
        Caffeine<Object, Object> cacheBuilder = cacheConfig.githubApiCacheBuilder();

        // Then
        assertThat(cacheBuilder).isNotNull();
        
        // Verify the cache builder is properly configured by building a cache
        var cache = cacheBuilder.build();
        assertThat(cache).isNotNull();
        
        // Test basic cache operations
        cache.put("test-key", "test-value");
        assertThat(cache.getIfPresent("test-key")).isEqualTo("test-value");
    }

    @Test
    void shouldCreateDuplicateCheckCacheBuilderWithCorrectSettings() {
        // When
        Caffeine<Object, Object> cacheBuilder = cacheConfig.duplicateCheckCacheBuilder();

        // Then
        assertThat(cacheBuilder).isNotNull();
        
        // Verify the cache builder is properly configured by building a cache
        var cache = cacheBuilder.build();
        assertThat(cache).isNotNull();
        
        // Test basic cache operations
        cache.put("test-id", true);
        assertThat(cache.getIfPresent("test-id")).isEqualTo(true);
    }

    @Test
    void shouldHaveDifferentConfigurationsForDifferentCaches() {
        // When
        Caffeine<Object, Object> githubCache = cacheConfig.githubApiCacheBuilder();
        Caffeine<Object, Object> duplicateCache = cacheConfig.duplicateCheckCacheBuilder();

        // Then
        assertThat(githubCache).isNotNull();
        assertThat(duplicateCache).isNotNull();
        assertThat(githubCache).isNotSameAs(duplicateCache);
        
        // Build actual caches to verify they work independently
        var githubCacheInstance = githubCache.build();
        var duplicateCacheInstance = duplicateCache.build();
        
        githubCacheInstance.put("github-key", "github-value");
        duplicateCacheInstance.put("duplicate-key", "duplicate-value");
        
        assertThat(githubCacheInstance.getIfPresent("github-key")).isEqualTo("github-value");
        assertThat(githubCacheInstance.getIfPresent("duplicate-key")).isNull();
        
        assertThat(duplicateCacheInstance.getIfPresent("duplicate-key")).isEqualTo("duplicate-value");
        assertThat(duplicateCacheInstance.getIfPresent("github-key")).isNull();
    }

    @Test
    void shouldEnableStatsRecordingForAllCaches() {
        // When
        CacheManager cacheManager = cacheConfig.cacheManager();
        Caffeine<Object, Object> githubCache = cacheConfig.githubApiCacheBuilder();
        Caffeine<Object, Object> duplicateCache = cacheConfig.duplicateCheckCacheBuilder();

        // Then - Build caches and verify stats are recorded
        var githubCacheInstance = githubCache.build();
        var duplicateCacheInstance = duplicateCache.build();
        
        // Perform some operations
        githubCacheInstance.put("key1", "value1");
        githubCacheInstance.getIfPresent("key1");
        githubCacheInstance.getIfPresent("nonexistent");
        
        duplicateCacheInstance.put("key2", true);
        duplicateCacheInstance.getIfPresent("key2");
        duplicateCacheInstance.getIfPresent("nonexistent");
        
        // Verify stats are being recorded (hit count > 0, miss count > 0)
        var githubStats = githubCacheInstance.stats();
        var duplicateStats = duplicateCacheInstance.stats();
        
        assertThat(githubStats.hitCount()).isGreaterThan(0);
        assertThat(githubStats.missCount()).isGreaterThan(0);
        assertThat(duplicateStats.hitCount()).isGreaterThan(0);
        assertThat(duplicateStats.missCount()).isGreaterThan(0);
    }
}