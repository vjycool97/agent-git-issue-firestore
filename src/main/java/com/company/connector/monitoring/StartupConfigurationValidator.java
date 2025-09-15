package com.company.connector.monitoring;

import com.company.connector.config.FirebaseConfig;
import com.company.connector.config.GitHubConfig;
import com.company.connector.config.SyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates application configuration on startup and logs the results.
 */
@Component
public class StartupConfigurationValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(StartupConfigurationValidator.class);
    
    private final GitHubConfig gitHubConfig;
    private final FirebaseConfig firebaseConfig;
    private final SyncConfig syncConfig;
    
    public StartupConfigurationValidator(GitHubConfig gitHubConfig, 
                                       FirebaseConfig firebaseConfig, 
                                       SyncConfig syncConfig) {
        this.gitHubConfig = gitHubConfig;
        this.firebaseConfig = firebaseConfig;
        this.syncConfig = syncConfig;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfigurationOnStartup() {
        logger.info("=== GitHub Firebase Connector - Configuration Validation ===");
        
        List<String> validationErrors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate GitHub configuration
        validateGitHubConfig(validationErrors, warnings);
        
        // Validate Firebase configuration
        validateFirebaseConfig(validationErrors, warnings);
        
        // Validate Sync configuration
        validateSyncConfig(validationErrors, warnings);
        
        // Log results
        logValidationResults(validationErrors, warnings);
        
        if (!validationErrors.isEmpty()) {
            logger.error("Application startup failed due to configuration errors. Please fix the above issues.");
            throw new IllegalStateException("Invalid configuration detected during startup");
        }
        
        logger.info("=== Configuration validation completed successfully ===");
    }
    
    private void validateGitHubConfig(List<String> errors, List<String> warnings) {
        logger.info("Validating GitHub configuration...");
        
        if (gitHubConfig.token() == null || gitHubConfig.token().trim().isEmpty()) {
            errors.add("GitHub token is not configured. Set GITHUB_TOKEN environment variable.");
        } else {
            logger.info("✓ GitHub token is configured");
        }
        
        if (gitHubConfig.apiUrl() == null || !gitHubConfig.apiUrl().startsWith("https://")) {
            errors.add("GitHub API URL must be a valid HTTPS URL");
        } else {
            logger.info("✓ GitHub API URL: {}", gitHubConfig.apiUrl());
        }
        
        if (gitHubConfig.timeout().toSeconds() < 5) {
            warnings.add("GitHub API timeout is very low (" + gitHubConfig.timeout().toSeconds() + "s). Consider increasing it.");
        } else {
            logger.info("✓ GitHub API timeout: {}s", gitHubConfig.timeout().toSeconds());
        }
        
        if (gitHubConfig.retry().maxAttempts() < 1) {
            errors.add("GitHub retry max attempts must be at least 1");
        } else {
            logger.info("✓ GitHub retry configuration: {} attempts with {} backoff", 
                       gitHubConfig.retry().maxAttempts(), 
                       gitHubConfig.retry().backoffDelay());
        }
    }
    
    private void validateFirebaseConfig(List<String> errors, List<String> warnings) {
        logger.info("Validating Firebase configuration...");
        
        if (firebaseConfig.projectId() == null || firebaseConfig.projectId().trim().isEmpty()) {
            errors.add("Firebase project ID is not configured. Set FIREBASE_PROJECT_ID environment variable.");
        } else {
            logger.info("✓ Firebase project ID: {}", firebaseConfig.projectId());
        }
        
        if (firebaseConfig.serviceAccountPath() == null || firebaseConfig.serviceAccountPath().trim().isEmpty()) {
            errors.add("Firebase service account path is not configured. Set FIREBASE_SERVICE_ACCOUNT_PATH environment variable.");
        } else {
            logger.info("✓ Firebase service account path: {}", firebaseConfig.serviceAccountPath());
        }
        
        if (firebaseConfig.collectionName() == null || firebaseConfig.collectionName().trim().isEmpty()) {
            errors.add("Firebase collection name cannot be empty");
        } else {
            logger.info("✓ Firebase collection name: {}", firebaseConfig.collectionName());
        }
        
        if (firebaseConfig.connectionPool().maxConnections() < 1) {
            errors.add("Firebase connection pool max connections must be at least 1");
        } else {
            logger.info("✓ Firebase connection pool: {} max connections, {} timeout", 
                       firebaseConfig.connectionPool().maxConnections(),
                       firebaseConfig.connectionPool().connectionTimeout());
        }
    }
    
    private void validateSyncConfig(List<String> errors, List<String> warnings) {
        logger.info("Validating Sync configuration...");
        
        if (syncConfig.defaultIssueLimit() < 1) {
            errors.add("Sync default issue limit must be at least 1");
        } else if (syncConfig.defaultIssueLimit() > 100) {
            warnings.add("Sync default issue limit is very high (" + syncConfig.defaultIssueLimit() + "). This may impact performance.");
        } else {
            logger.info("✓ Sync default issue limit: {}", syncConfig.defaultIssueLimit());
        }
        
        if (syncConfig.batchSize() < 1) {
            errors.add("Sync batch size must be at least 1");
        } else if (syncConfig.batchSize() > 500) {
            warnings.add("Sync batch size is very high (" + syncConfig.batchSize() + "). This may impact performance.");
        } else {
            logger.info("✓ Sync batch size: {}", syncConfig.batchSize());
        }
        
        if (syncConfig.schedule() != null && !syncConfig.schedule().trim().isEmpty()) {
            logger.info("✓ Sync schedule: {}", syncConfig.schedule());
        } else {
            warnings.add("Sync schedule is not configured. Automatic syncing will be disabled.");
        }
    }
    
    private void logValidationResults(List<String> errors, List<String> warnings) {
        if (!errors.isEmpty()) {
            logger.error("Configuration validation found {} error(s):", errors.size());
            for (int i = 0; i < errors.size(); i++) {
                logger.error("  {}. {}", i + 1, errors.get(i));
            }
        }
        
        if (!warnings.isEmpty()) {
            logger.warn("Configuration validation found {} warning(s):", warnings.size());
            for (int i = 0; i < warnings.size(); i++) {
                logger.warn("  {}. {}", i + 1, warnings.get(i));
            }
        }
        
        if (errors.isEmpty() && warnings.isEmpty()) {
            logger.info("✓ All configuration validation checks passed");
        }
    }
}