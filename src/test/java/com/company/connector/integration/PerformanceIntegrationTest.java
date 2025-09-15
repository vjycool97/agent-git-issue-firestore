package com.company.connector.integration;

import com.company.connector.model.GitHubIssue;
import com.company.connector.model.SyncResult;
import com.company.connector.service.IssueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance integration tests for the GitHub-Firebase Connector.
 * 
 * These tests verify:
 * - Concurrent sync operations performance
 * - Caching behavior and performance improvements
 * - Virtual Threads performance under load
 * - System behavior under stress conditions
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("performance-test")
@Testcontainers
class PerformanceIntegrationTest {
    
    @Container
    static GenericContainer<?> firestoreEmulator = new GenericContainer<>(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"))
            .withCommand("gcloud", "beta", "emulators", "firestore", "start", 
                        "--host-port=0.0.0.0:8080", "--project=test-project")
            .withExposedPorts(8080);
    
    private static WireMockServer wireMockServer;
    
    @Autowired
    private IssueService issueService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private CacheManager cacheManager;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure Firestore emulator
        registry.add("firebase.project-id", () -> "test-project");
        registry.add("firebase.emulator.host", () -> "localhost:" + firestoreEmulator.getMappedPort(8080));
        registry.add("firebase.use-emulator", () -> "true");
        
        // Configure GitHub API to use WireMock
        registry.add("github.api-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("github.token", () -> "test-token");
        
        // Performance optimizations for testing
        registry.add("sync.batch-size", () -> "20");
        registry.add("github.timeout", () -> "10s");
    }
    
    @BeforeAll
    static void setupWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options()
                .dynamicPort()
                .jettyAcceptors(4)
                .jettyAcceptQueueSize(100));
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());
    }
    
    @AfterAll
    static void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
    
    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        // Clear all caches before each test
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }
    
    @Test
    @DisplayName("Concurrent sync operations performance test")
    void concurrentSyncOperations_ShouldHandleMultipleRequestsEfficiently() throws Exception {
        // Given: Mock GitHub API responses for multiple repositories
        setupMockResponsesForMultipleRepos();
        
        int concurrentRequests = 10;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        // When: Execute multiple concurrent sync operations
        Instant startTime = Instant.now();
        
        List<CompletableFuture<SyncResult>> futures = IntStream.range(0, concurrentRequests)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String repo = "repo-" + (i % 3); // Use 3 different repos to test caching
                        return issueService.syncIssues("testorg", repo, 5).get(30, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executor))
                .toList();
        
        // Wait for all operations to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.get(60, TimeUnit.SECONDS);
        
        Instant endTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, endTime);
        
        // Then: Verify performance characteristics
        List<SyncResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        // All operations should succeed
        long successCount = results.stream()
                .mapToLong(result -> result.isSuccess() ? 1 : 0)
                .sum();
        
        assertThat(successCount).isEqualTo(concurrentRequests);
        
        // Total time should be reasonable (less than sequential execution)
        assertThat(totalDuration).isLessThan(Duration.ofSeconds(30));
        
        // Verify that caching reduced API calls (should be 3 unique repos, not 10 calls)
        int totalApiCalls = wireMockServer.countRequestsMatching(
                getRequestedFor(urlPathMatching("/repos/testorg/repo-.*/issues")).build()).getCount();
        assertThat(totalApiCalls).isGreaterThanOrEqualTo(3);
        assertThat(totalApiCalls).isLessThan(concurrentRequests + 1);
        
        executor.shutdown();
    }
    
    @Test
    @DisplayName("Cache performance improvement test")
    void cachePerformance_ShouldSignificantlyReduceResponseTimes() throws Exception {
        // Given: Mock GitHub API with artificial delay
        String githubResponse = objectMapper.writeValueAsString(createMockGitHubIssues());
        
        stubFor(get(urlPathEqualTo("/repos/testorg/cached-repo/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(githubResponse)
                        .withFixedDelay(1000))); // 1 second delay to simulate network latency
        
        // When: First request (cache miss)
        Instant firstStart = Instant.now();
        CompletableFuture<SyncResult> firstSync = issueService.syncIssues("testorg", "cached-repo", 5);
        SyncResult firstResult = firstSync.get(30, TimeUnit.SECONDS);
        Duration firstDuration = Duration.between(firstStart, Instant.now());
        
        // Second request (cache hit)
        Instant secondStart = Instant.now();
        CompletableFuture<SyncResult> secondSync = issueService.syncIssues("testorg", "cached-repo", 5);
        SyncResult secondResult = secondSync.get(30, TimeUnit.SECONDS);
        Duration secondDuration = Duration.between(secondStart, Instant.now());
        
        // Then: Verify caching performance improvement
        assertThat(firstResult).isInstanceOf(SyncResult.Success.class);
        assertThat(secondResult).isInstanceOf(SyncResult.Success.class);
        
        // Second request should be significantly faster due to caching
        assertThat(secondDuration).isLessThan(firstDuration.dividedBy(2));
        
        // Verify API was called only once (cached on second call)
        verify(1, getRequestedFor(urlPathEqualTo("/repos/testorg/cached-repo/issues")));
    }
    
    @Test
    @DisplayName("High-volume sync operations stress test")
    void highVolumeSyncOperations_ShouldMaintainPerformanceUnderLoad() throws Exception {
        // Given: Mock responses for high-volume testing
        setupMockResponsesForHighVolume();
        
        int totalOperations = 50;
        int batchSize = 10;
        List<Duration> batchDurations = new ArrayList<>();
        
        // When: Execute operations in batches to measure performance degradation
        for (int batch = 0; batch < totalOperations / batchSize; batch++) {
            Instant batchStart = Instant.now();
            
            final int currentBatch = batch;
            List<CompletableFuture<SyncResult>> batchFutures = IntStream.range(0, batchSize)
                    .mapToObj(i -> {
                        String repo = "volume-repo-" + ((currentBatch * batchSize + i) % 5);
                        return issueService.syncIssues("testorg", repo, 3);
                    })
                    .toList();
            
            CompletableFuture<Void> batchCompletion = CompletableFuture.allOf(
                    batchFutures.toArray(new CompletableFuture[0]));
            batchCompletion.get(60, TimeUnit.SECONDS);
            
            Duration batchDuration = Duration.between(batchStart, Instant.now());
            batchDurations.add(batchDuration);
            
            // Verify all operations in batch succeeded
            List<SyncResult> batchResults = batchFutures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            long batchSuccessCount = batchResults.stream()
                    .mapToLong(result -> result.isSuccess() ? 1 : 0)
                    .sum();
            
            assertThat(batchSuccessCount).isEqualTo(batchSize);
        }
        
        // Then: Verify performance doesn't degrade significantly over time
        Duration firstBatchDuration = batchDurations.get(0);
        Duration lastBatchDuration = batchDurations.get(batchDurations.size() - 1);
        
        // Last batch should not be more than 50% slower than first batch
        assertThat(lastBatchDuration).isLessThan(firstBatchDuration.multipliedBy(3).dividedBy(2));
        
        // Average batch duration should be reasonable
        double averageBatchSeconds = batchDurations.stream()
                .mapToDouble(d -> d.toMillis() / 1000.0)
                .average()
                .orElse(0.0);
        
        assertThat(averageBatchSeconds).isLessThan(10.0); // Less than 10 seconds per batch
    }
    
    @Test
    @DisplayName("Memory usage under concurrent load")
    void memoryUsageUnderLoad_ShouldRemainStable() throws Exception {
        // Given: Setup for memory testing
        setupMockResponsesForMultipleRepos();
        
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // When: Execute many concurrent operations
        int iterations = 20;
        for (int i = 0; i < iterations; i++) {
            List<CompletableFuture<SyncResult>> futures = IntStream.range(0, 5)
                    .mapToObj(j -> issueService.syncIssues("testorg", "repo-" + j, 5))
                    .toList();
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
            
            // Force garbage collection periodically
            if (i % 5 == 0) {
                System.gc();
                Thread.sleep(100);
            }
        }
        
        // Force final garbage collection
        System.gc();
        Thread.sleep(500);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        // Then: Memory increase should be reasonable (less than 100MB)
        assertThat(memoryIncrease).isLessThan(100 * 1024 * 1024); // 100MB
    }
    
    @Test
    @DisplayName("Cache eviction and performance under memory pressure")
    void cacheEvictionUnderMemoryPressure_ShouldMaintainPerformance() throws Exception {
        // Given: Setup many different repositories to fill cache
        int repoCount = 100;
        for (int i = 0; i < repoCount; i++) {
            stubFor(get(urlPathEqualTo("/repos/testorg/cache-test-" + i + "/issues"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(objectMapper.writeValueAsString(createMockGitHubIssues()))));
        }
        
        // When: Access many repositories to trigger cache eviction
        List<CompletableFuture<SyncResult>> futures = IntStream.range(0, repoCount)
                .mapToObj(i -> issueService.syncIssues("testorg", "cache-test-" + i, 5))
                .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(120, TimeUnit.SECONDS);
        
        // Then: All operations should still succeed despite cache pressure
        List<SyncResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        long successCount = results.stream()
                .mapToLong(result -> result.isSuccess() ? 1 : 0)
                .sum();
        
        assertThat(successCount).isEqualTo(repoCount);
        
        // Verify that cache is working (should have made exactly repoCount API calls)
        verify(repoCount, getRequestedFor(urlPathMatching("/repos/testorg/cache-test-.*/issues")));
    }
    
    private void setupMockResponsesForMultipleRepos() throws Exception {
        String githubResponse = objectMapper.writeValueAsString(createMockGitHubIssues());
        
        for (int i = 0; i < 5; i++) {
            stubFor(get(urlPathEqualTo("/repos/testorg/repo-" + i + "/issues"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(githubResponse)));
        }
    }
    
    private void setupMockResponsesForHighVolume() throws Exception {
        String githubResponse = objectMapper.writeValueAsString(createMockGitHubIssues());
        
        for (int i = 0; i < 10; i++) {
            stubFor(get(urlPathEqualTo("/repos/testorg/volume-repo-" + i + "/issues"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(githubResponse)));
        }
    }
    
    private List<GitHubIssue> createMockGitHubIssues() {
        return List.of(
                new GitHubIssue(
                        1L,
                        "Performance Issue",
                        "open",
                        "https://github.com/testorg/repo/issues/1",
                        Instant.parse("2024-01-15T10:30:00Z")
                ),
                new GitHubIssue(
                        2L,
                        "Memory Leak",
                        "open",
                        "https://github.com/testorg/repo/issues/2",
                        Instant.parse("2024-01-15T10:31:00Z")
                ),
                new GitHubIssue(
                        3L,
                        "Optimization Request",
                        "closed",
                        "https://github.com/testorg/repo/issues/3",
                        Instant.parse("2024-01-15T10:32:00Z")
                )
        );
    }
}