package com.company.connector.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test to verify that the application starts successfully with monitoring components.
 */
@SpringBootTest(properties = {
    "spring.main.allow-bean-definition-overriding=true",
    "github.token=test-token",
    "firebase.project-id=test-project",
    "firebase.service-account-path=/tmp/test.json",
    "firebase.collection-name=test-collection"
})
@ActiveProfiles("test")
class MonitoringApplicationTest {
    
    @MockBean
    private com.company.connector.client.GitHubClient gitHubClient;
    
    @MockBean
    private com.company.connector.repository.FirestoreRepository firestoreRepository;
    
    @Test
    void contextLoads() {
        // This test verifies that the Spring context loads successfully
        // with all monitoring components configured
    }
}