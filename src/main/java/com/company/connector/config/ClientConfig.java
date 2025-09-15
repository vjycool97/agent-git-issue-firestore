package com.company.connector.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for client-related beans including caching and retry mechanisms.
 */
@Configuration
@EnableRetry
@EnableCaching
public class ClientConfig {
    
    /**
     * Configures Caffeine cache manager for GitHub API response caching.
     * 
     * @return configured CacheManager with Caffeine implementation
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .recordStats());
        return cacheManager;
    }
}