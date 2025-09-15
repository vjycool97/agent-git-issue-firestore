package com.company.connector.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SyncHealthIndicatorTest {
    
    private SyncHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new SyncHealthIndicator();
    }
    
    @Test
    void health_WhenNoSyncYet_ShouldReturnDown() {
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "No successful sync yet");
        assertThat(health.getDetails()).containsEntry("totalSyncs", 0L);
        assertThat(health.getDetails()).containsEntry("totalFailures", 0L);
        assertThat(health.getDetails()).containsEntry("consecutiveFailures", 0L);
        assertThat(health.getDetails()).containsEntry("successRate", "N/A");
    }
    
    @Test
    void health_WhenRecentSuccessfulSync_ShouldReturnUp() {
        // Given
        healthIndicator.recordSuccessfulSync();
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "Healthy");
        assertThat(health.getDetails()).containsEntry("totalSyncs", 1L);
        assertThat(health.getDetails()).containsEntry("totalFailures", 0L);
        assertThat(health.getDetails()).containsEntry("consecutiveFailures", 0L);
        assertThat(health.getDetails()).containsEntry("successRate", "100.00%");
    }
    
    @Test
    void health_WhenTooManyConsecutiveFailures_ShouldReturnDown() {
        // Given
        for (int i = 0; i < 5; i++) {
            healthIndicator.recordFailedSync();
        }
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Too many consecutive failures");
        assertThat(health.getDetails()).containsEntry("consecutiveFailures", 5L);
        assertThat(health.getDetails()).containsEntry("totalSyncs", 5L);
        assertThat(health.getDetails()).containsEntry("totalFailures", 5L);
        assertThat(health.getDetails()).containsEntry("successRate", "0.00%");
    }
    
    @Test
    void health_WhenMixedResults_ShouldCalculateCorrectSuccessRate() {
        // Given
        healthIndicator.recordSuccessfulSync();
        healthIndicator.recordSuccessfulSync();
        healthIndicator.recordFailedSync();
        healthIndicator.recordSuccessfulSync(); // This should reset consecutive failures
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "Healthy");
        assertThat(health.getDetails()).containsEntry("totalSyncs", 4L);
        assertThat(health.getDetails()).containsEntry("totalFailures", 1L);
        assertThat(health.getDetails()).containsEntry("consecutiveFailures", 0L);
        assertThat(health.getDetails()).containsEntry("successRate", "75.00%");
    }
    
    @Test
    void recordSuccessfulSync_ShouldResetConsecutiveFailures() {
        // Given
        healthIndicator.recordFailedSync();
        healthIndicator.recordFailedSync();
        
        // When
        healthIndicator.recordSuccessfulSync();
        
        // Then
        Health health = healthIndicator.health();
        assertThat(health.getDetails()).containsEntry("consecutiveFailures", 0L);
    }
    
    @Test
    void recordFailedSync_ShouldIncrementCounters() {
        // Given
        healthIndicator.recordSuccessfulSync(); // Start with a success
        
        // When
        healthIndicator.recordFailedSync();
        healthIndicator.recordFailedSync();
        
        // Then
        Health health = healthIndicator.health();
        assertThat(health.getDetails()).containsEntry("totalSyncs", 3L);
        assertThat(health.getDetails()).containsEntry("totalFailures", 2L);
        assertThat(health.getDetails()).containsEntry("consecutiveFailures", 2L);
    }
}