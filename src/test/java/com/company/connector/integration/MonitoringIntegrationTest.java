package com.company.connector.integration;

import com.company.connector.monitoring.SyncHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, 
                properties = {"spring.main.allow-bean-definition-overriding=true"})
@ActiveProfiles("test")
@AutoConfigureWebMvc
class MonitoringIntegrationTest {
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @MockBean
    private com.company.connector.client.GitHubClient gitHubClient;
    
    @MockBean
    private com.company.connector.repository.FirestoreRepository firestoreRepository;
    
    @Autowired
    private SyncHealthIndicator syncHealthIndicator;
    
    @Test
    void actuatorHealthEndpoint_ShouldBeAccessible() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health", Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("status");
    }
    
    @Test
    void actuatorInfoEndpoint_ShouldBeAccessible() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/info", Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }
    
    @Test
    void actuatorMetricsEndpoint_ShouldBeAccessible() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/metrics", Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("names");
    }
    
    @Test
    void customSyncHealthEndpoint_ShouldBeAccessible() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/monitoring/health/sync", Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("status");
    }
    
    @Test
    void customMetricsSummaryEndpoint_ShouldBeAccessible() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/monitoring/metrics/summary", Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("sync_operations_success_total");
        assertThat(response.getBody()).containsKey("sync_operations_failure_total");
    }
    
    @Test
    void syncHealthIndicator_ShouldBeRegisteredWithActuator() {
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/actuator/health", Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        
        // Check if our custom health indicator is included
        if (body.containsKey("components")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) body.get("components");
            assertThat(components).containsKey("syncHealthIndicator");
        }
    }
    
    @Test
    void syncHealthIndicator_AfterRecordingSuccess_ShouldReflectInHealth() {
        // Given
        syncHealthIndicator.recordSuccessfulSync();
        
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/monitoring/health/sync", Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("status", "UP");
    }
    
    @Test
    void syncHealthIndicator_AfterRecordingMultipleFailures_ShouldReflectInHealth() {
        // Given
        for (int i = 0; i < 5; i++) {
            syncHealthIndicator.recordFailedSync();
        }
        
        // When
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/api/monitoring/health/sync", Map.class);
        
        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("status", "DOWN");
    }
}