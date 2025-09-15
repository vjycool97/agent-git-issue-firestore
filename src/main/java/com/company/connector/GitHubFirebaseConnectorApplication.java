package com.company.connector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for the GitHub-Firebase Connector.
 * 
 * This application synchronizes GitHub repository issues with Firebase Firestore,
 * providing a bridge between GitHub's issue tracking and Firebase's document database.
 */
@SpringBootApplication
@EnableCaching
@EnableRetry
@EnableScheduling
@ConfigurationPropertiesScan
public class GitHubFirebaseConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(GitHubFirebaseConnectorApplication.class, args);
    }
}