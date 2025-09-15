package com.company.connector.monitoring;

import com.company.connector.config.FirebaseConfig;
import com.company.connector.config.GitHubConfig;
import com.company.connector.config.SyncConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StartupConfigurationValidatorTest {
    
    @Mock
    private GitHubConfig gitHubConfig;
    
    @Mock
    private FirebaseConfig firebaseConfig;
    
    @Mock
    private SyncConfig syncConfig;
    
    @Mock
    private ApplicationReadyEvent applicationReadyEvent;
    
    private StartupConfigurationValidator validator;
    
    @BeforeEach
    void setUp() {
        validator = new StartupConfigurationValidator(gitHubConfig, firebaseConfig, syncConfig);
    }
    
    @Test
    void validateConfigurationOnStartup_WithValidConfiguration_ShouldPass() {
        // Given
        setupValidConfiguration();
        
        // When & Then - should not throw exception
        validator.validateConfigurationOnStartup();
    }
    
    @Test
    void validateConfigurationOnStartup_WithMissingGitHubToken_ShouldThrowException() {
        // Given
        setupValidConfiguration();
        when(gitHubConfig.token()).thenReturn(null);
        
        // When & Then
        assertThatThrownBy(() -> validator.validateConfigurationOnStartup())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid configuration detected during startup");
    }
    
    @Test
    void validateConfigurationOnStartup_WithEmptyGitHubToken_ShouldThrowException() {
        // Given
        setupValidConfiguration();
        when(gitHubConfig.token()).thenReturn("   ");
        
        // When & Then
        assertThatThrownBy(() -> validator.validateConfigurationOnStartup())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid configuration detected during startup");
    }
    
    @Test
    void validateConfigurationOnStartup_WithInvalidGitHubApiUrl_ShouldThrowException() {
        // Given
        setupValidConfiguration();
        when(gitHubConfig.apiUrl()).thenReturn("http://insecure-url.com");
        
        // When & Then
        assertThatThrownBy(() -> validator.validateConfigurationOnStartup())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid configuration detected during startup");
    }
    
    @Test
    void validateConfigurationOnStartup_WithMissingFirebaseProjectId_ShouldThrowException() {
        // Given
        setupValidConfiguration();
        when(firebaseConfig.projectId()).thenReturn("");
        
        // When & Then
        assertThatThrownBy(() -> validator.validateConfigurationOnStartup())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid configuration detected during startup");
    }
    
    @Test
    void validateConfigurationOnStartup_WithMissingFirebaseServiceAccountPath_ShouldThrowException() {
        // Given
        setupValidConfiguration();
        when(firebaseConfig.serviceAccountPath()).thenReturn(null);
        
        // When & Then
        assertThatThrownBy(() -> validator.validateConfigurationOnStartup())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid configuration detected during startup");
    }
    
    @Test
    void validateConfigurationOnStartup_WithInvalidSyncLimit_ShouldThrowException() {
        // Given
        setupValidConfiguration();
        when(syncConfig.defaultIssueLimit()).thenReturn(0);
        
        // When & Then
        assertThatThrownBy(() -> validator.validateConfigurationOnStartup())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid configuration detected during startup");
    }
    
    @Test
    void validateConfigurationOnStartup_WithInvalidBatchSize_ShouldThrowException() {
        // Given
        setupValidConfiguration();
        when(syncConfig.batchSize()).thenReturn(-1);
        
        // When & Then
        assertThatThrownBy(() -> validator.validateConfigurationOnStartup())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid configuration detected during startup");
    }
    
    @Test
    void validateConfigurationOnStartup_WithInvalidRetryAttempts_ShouldThrowException() {
        // Given
        setupValidConfiguration();
        GitHubConfig.RetryConfig invalidRetryConfig = new GitHubConfig.RetryConfig(0, Duration.ofSeconds(1));
        when(gitHubConfig.retry()).thenReturn(invalidRetryConfig);
        
        // When & Then
        assertThatThrownBy(() -> validator.validateConfigurationOnStartup())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid configuration detected during startup");
    }
    
    private void setupValidConfiguration() {
        // GitHub config
        GitHubConfig.RetryConfig retryConfig = new GitHubConfig.RetryConfig(3, Duration.ofSeconds(1));
        when(gitHubConfig.token()).thenReturn("valid-token");
        when(gitHubConfig.apiUrl()).thenReturn("https://api.github.com");
        when(gitHubConfig.timeout()).thenReturn(Duration.ofSeconds(30));
        when(gitHubConfig.retry()).thenReturn(retryConfig);
        
        // Firebase config
        FirebaseConfig.ConnectionPoolConfig connectionPool = new FirebaseConfig.ConnectionPoolConfig(50, Duration.ofSeconds(10));
        when(firebaseConfig.projectId()).thenReturn("test-project");
        when(firebaseConfig.serviceAccountPath()).thenReturn("/path/to/service-account.json");
        when(firebaseConfig.collectionName()).thenReturn("github_issues");
        when(firebaseConfig.connectionPool()).thenReturn(connectionPool);
        
        // Sync config
        when(syncConfig.defaultIssueLimit()).thenReturn(5);
        when(syncConfig.batchSize()).thenReturn(10);
        when(syncConfig.schedule()).thenReturn("0 */15 * * * *");
    }
}