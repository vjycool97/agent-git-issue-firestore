package com.company.connector.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Main configuration class that enables all configuration properties.
 * This class registers all the configuration record classes with Spring Boot.
 */
@Configuration
@EnableConfigurationProperties({
    GitHubConfig.class,
    FirebaseConfig.class,
    SyncConfig.class
})
public class ApplicationConfig {
    // Configuration properties are automatically registered as beans
    // No additional configuration needed due to @EnableConfigurationProperties
}