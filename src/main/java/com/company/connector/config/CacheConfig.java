package com.company.connector.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration class for Caffeine cache manager.
 * Provides caching for GitHub API responses and Firestore duplicate checks.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configures Caffeine cache manager with optimized settings for the connector.
     * 
     * Cache specifications:
     * - Maximum size: 1000 entries to prevent memory issues
     * - Expire after write: 15 minutes to balance freshness and performance
     * - Record stats: Enable for monitoring cache hit rates
     * 
     * @return configured CacheManager instance
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .recordStats());
        return cacheManager;
    }

    /**
     * Separate cache configuration for GitHub API responses.
     * Shorter TTL due to dynamic nature of GitHub issues.
     * 
     * @return Caffeine cache builder for GitHub API
     */
    @Bean("githubApiCache")
    public Caffeine<Object, Object> githubApiCacheBuilder() {
        return Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats();
    }

    /**
     * Cache configuration for Firestore duplicate checks.
     * Longer TTL since issue existence doesn't change frequently.
     * 
     * @return Caffeine cache builder for duplicate checks
     */
    @Bean("duplicateCheckCache")
    public Caffeine<Object, Object> duplicateCheckCacheBuilder() {
        return Caffeine.newBuilder()
            .maximumSize(2000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats();
    }
}