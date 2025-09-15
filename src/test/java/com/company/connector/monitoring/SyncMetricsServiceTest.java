package com.company.connector.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SyncMetricsServiceTest {
    
    @Mock
    private SyncHealthIndicator healthIndicator;
    
    private MeterRegistry meterRegistry;
    private SyncMetricsService metricsService;
    
    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsService = new SyncMetricsService(meterRegistry, healthIndicator);
    }
    
    @Test
    void recordSyncSuccess_ShouldIncrementCountersAndRecordDuration() {
        // Given
        int issuesProcessed = 5;
        Duration duration = Duration.ofSeconds(10);
        
        // When
        metricsService.recordSyncSuccess(issuesProcessed, duration);
        
        // Then
        Counter successCounter = meterRegistry.counter("sync.operations.success");
        Counter issuesCounter = meterRegistry.counter("sync.issues.processed");
        Timer durationTimer = meterRegistry.timer("sync.duration");
        
        assertThat(successCounter.count()).isEqualTo(1.0);
        assertThat(issuesCounter.count()).isEqualTo(5.0);
        assertThat(durationTimer.count()).isEqualTo(1);
        assertThat(durationTimer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(10.0);
        
        verify(healthIndicator).recordSuccessfulSync();
    }
    
    @Test
    void recordSyncFailure_ShouldIncrementFailureCounterAndRecordDuration() {
        // Given
        String errorMessage = "Test error";
        Duration duration = Duration.ofSeconds(5);
        
        // When
        metricsService.recordSyncFailure(errorMessage, duration);
        
        // Then
        Counter failureCounter = meterRegistry.counter("sync.operations.failure");
        Timer durationTimer = meterRegistry.timer("sync.duration");
        
        assertThat(failureCounter.count()).isEqualTo(1.0);
        assertThat(durationTimer.count()).isEqualTo(1);
        assertThat(durationTimer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(5.0);
        
        verify(healthIndicator).recordFailedSync();
    }
    
    @Test
    void recordPartialSyncFailure_ShouldRecordAsSuccessWithProcessedCount() {
        // Given
        int successCount = 3;
        int failureCount = 2;
        Duration duration = Duration.ofSeconds(8);
        
        // When
        metricsService.recordPartialSyncFailure(successCount, failureCount, duration);
        
        // Then
        Counter successCounter = meterRegistry.counter("sync.operations.success");
        Counter issuesCounter = meterRegistry.counter("sync.issues.processed");
        Timer durationTimer = meterRegistry.timer("sync.duration");
        
        assertThat(successCounter.count()).isEqualTo(1.0);
        assertThat(issuesCounter.count()).isEqualTo(3.0);
        assertThat(durationTimer.count()).isEqualTo(1);
        assertThat(durationTimer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(8.0);
        
        verify(healthIndicator).recordSuccessfulSync();
    }
    
    @Test
    void multipleOperations_ShouldAccumulateMetrics() {
        // Given & When
        metricsService.recordSyncSuccess(2, Duration.ofSeconds(5));
        metricsService.recordSyncSuccess(3, Duration.ofSeconds(7));
        metricsService.recordSyncFailure("Error", Duration.ofSeconds(3));
        
        // Then
        Counter successCounter = meterRegistry.counter("sync.operations.success");
        Counter failureCounter = meterRegistry.counter("sync.operations.failure");
        Counter issuesCounter = meterRegistry.counter("sync.issues.processed");
        Timer durationTimer = meterRegistry.timer("sync.duration");
        
        assertThat(successCounter.count()).isEqualTo(2.0);
        assertThat(failureCounter.count()).isEqualTo(1.0);
        assertThat(issuesCounter.count()).isEqualTo(5.0); // 2 + 3
        assertThat(durationTimer.count()).isEqualTo(3);
        assertThat(durationTimer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(15.0); // 5 + 7 + 3
    }
}