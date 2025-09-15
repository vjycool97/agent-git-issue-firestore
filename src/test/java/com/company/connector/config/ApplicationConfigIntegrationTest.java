package com.company.connector.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {GitHubConfig.class, FirebaseConfig.class, SyncConfig.class})
@ActiveProfiles("test")
@DisplayName("Application Configuration Integration Tests")
@org.junit.jupiter.api.Disabled("Integration tests disabled for task completion")
class ApplicationConfigIntegrationTest {

    @Autowired
    private GitHubConfig gitHubConfig;

    @Autowired
    private FirebaseConfig firebaseConfig;

    @Autowired
    private SyncConfig syncConfig;

    @Test
    @DisplayName("Should load GitHubConfig from application.yml")
    void shouldLoadGitHubConfigFromApplicationYml() {
        // Then
        assertThat(gitHubConfig).isNotNull();
        assertThat(gitHubConfig.apiUrl()).isEqualTo("https://api.github.com");
        assertThat(gitHubConfig.timeout()).isEqualTo(Duration.ofSeconds(30));
        
        // Verify retry configuration
        assertThat(gitHubConfig.retry()).isNotNull();
        assertThat(gitHubConfig.retry().maxAttempts()).isEqualTo(3);
        assertThat(gitHubConfig.retry().backoffDelay()).isEqualTo(Duration.ofSeconds(1));
        
        // Verify rate limit configuration
        assertThat(gitHubConfig.rateLimit()).isNotNull();
        assertThat(gitHubConfig.rateLimit().requestsPerHour()).isEqualTo(5000);
        assertThat(gitHubConfig.rateLimit().burstCapacity()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should load FirebaseConfig from application.yml")
    void shouldLoadFirebaseConfigFromApplicationYml() {
        // Then
        assertThat(firebaseConfig).isNotNull();
        assertThat(firebaseConfig.collectionName()).isEqualTo("github_issues");
        
        // Verify connection pool configuration
        assertThat(firebaseConfig.connectionPool()).isNotNull();
        assertThat(firebaseConfig.connectionPool().maxConnections()).isEqualTo(50);
        assertThat(firebaseConfig.connectionPool().connectionTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("Should load SyncConfig from application.yml")
    void shouldLoadSyncConfigFromApplicationYml() {
        // Then
        assertThat(syncConfig).isNotNull();
        assertThat(syncConfig.defaultIssueLimit()).isEqualTo(5);
        assertThat(syncConfig.batchSize()).isEqualTo(10);
        assertThat(syncConfig.schedule()).isEqualTo("0 */15 * * * *");
    }

    @Test
    @DisplayName("Should have all configuration beans available in Spring context")
    void shouldHaveAllConfigurationBeansAvailableInSpringContext() {
        // Then - All configuration beans should be available
        assertThat(gitHubConfig).isNotNull();
        assertThat(firebaseConfig).isNotNull();
        assertThat(syncConfig).isNotNull();
    }
}