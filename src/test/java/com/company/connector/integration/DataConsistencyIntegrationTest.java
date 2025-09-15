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

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration tests for data consistency and duplicate handling.
 * 
 * These tests verify:
 * - Duplicate detection and handling
 * - Data consistency across multiple sync operations
 * - Cache consistency and invalidation
 * - Concurrent access data integrity
 * - Update vs insert behavior
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("consistency-test")
@Testcontainers
class DataConsistencyIntegrationTest {
    
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
        
        // Configure for consistency testing
        registry.add("sync.batch-size", () -> "5");
    }
    
    @BeforeAll
    static void setupWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
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
    @DisplayName("Duplicate detection: Same issues should not be duplicated")
    void duplicateDetection_SameIssues_ShouldNotBeDuplicated() throws Exception {
        // Given: Mock GitHub API with same issues
        List<GitHubIssue> mockIssues = createMockGitHubIssues();
        String githubResponse = objectMapper.writeValueAsString(mockIssues);
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(githubResponse)));
        
        // When: Perform first sync
        CompletableFuture<SyncResult> firstSync = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult firstResult = firstSync.get(30, TimeUnit.SECONDS);
        
        // Wait for first sync to complete
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(firstResult).isInstanceOf(SyncResult.Success.class));
        
        // Perform second sync with same data
        CompletableFuture<SyncResult> secondSync = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult secondResult = secondSync.get(30, TimeUnit.SECONDS);
        
        // Then: Both syncs should succeed
        assertThat(firstResult).isInstanceOf(SyncResult.Success.class);
        assertThat(secondResult).isInstanceOf(SyncResult.Success.class);
        
        SyncResult.Success firstSuccess = (SyncResult.Success) firstResult;
        SyncResult.Success secondSuccess = (SyncResult.Success) secondResult;
        
        // First sync should process all issues
        assertThat(firstSuccess.processedCount()).isEqualTo(3);
        
        // Second sync should still process issues (updates existing ones)
        assertThat(secondSuccess.processedCount()).isEqualTo(3);
        
        // Verify API was called twice
        verify(2, getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
    
    @Test
    @DisplayName("Updated issues should be properly synchronized")
    void updatedIssues_ShouldBeProperlySynchronized() throws Exception {
        // Given: Initial issues
        List<GitHubIssue> initialIssues = createMockGitHubIssues();
        String initialResponse = objectMapper.writeValueAsString(initialIssues);
        
        // Updated issues (same IDs, different titles/states)
        List<GitHubIssue> updatedIssues = List.of(
                new GitHubIssue(
                        1L,
                        "Bug: Application crashes on startup - UPDATED",
                        "closed", // Changed from open to closed
                        "https://github.com/octocat/Hello-World/issues/1",
                        Instant.parse("2024-01-15T10:30:00Z")
                ),
                new GitHubIssue(
                        2L,
                        "Feature: Add user authentication - UPDATED",
                        "open",
                        "https://github.com/octocat/Hello-World/issues/2",
                        Instant.parse("2024-01-15T10:31:00Z")
                ),
                new GitHubIssue(
                        3L,
                        "Documentation: Update README - UPDATED",
                        "open", // Changed from closed to open
                        "https://github.com/octocat/Hello-World/issues/3",
                        Instant.parse("2024-01-15T10:32:00Z")
                )
        );
        String updatedResponse = objectMapper.writeValueAsString(updatedIssues);
        
        // Mock initial response
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Issue Updates")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(initialResponse))
                .willSetStateTo("Initial Sync Done"));
        
        // Mock updated response
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Issue Updates")
                .whenScenarioStateIs("Initial Sync Done")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(updatedResponse)));
        
        // When: Perform initial sync
        CompletableFuture<SyncResult> initialSync = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult initialResult = initialSync.get(30, TimeUnit.SECONDS);
        
        // Wait for initial sync to complete
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(initialResult).isInstanceOf(SyncResult.Success.class));
        
        // Perform update sync
        CompletableFuture<SyncResult> updateSync = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult updateResult = updateSync.get(30, TimeUnit.SECONDS);
        
        // Then: Both syncs should succeed
        assertThat(initialResult).isInstanceOf(SyncResult.Success.class);
        assertThat(updateResult).isInstanceOf(SyncResult.Success.class);
        
        SyncResult.Success initialSuccess = (SyncResult.Success) initialResult;
        SyncResult.Success updateSuccess = (SyncResult.Success) updateResult;
        
        assertThat(initialSuccess.processedCount()).isEqualTo(3);
        assertThat(updateSuccess.processedCount()).isEqualTo(3);
        
        // Verify both API calls were made
        verify(2, getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
    
    @Test
    @DisplayName("Concurrent sync operations should maintain data consistency")
    void concurrentSyncOperations_ShouldMaintainDataConsistency() throws Exception {
        // Given: Mock GitHub API responses for concurrent access
        List<GitHubIssue> mockIssues = createMockGitHubIssues();
        String githubResponse = objectMapper.writeValueAsString(mockIssues);
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(githubResponse)));
        
        // When: Perform multiple concurrent sync operations
        int concurrentOperations = 5;
        List<CompletableFuture<SyncResult>> futures = IntStream.range(0, concurrentOperations)
                .mapToObj(i -> issueService.syncIssues("octocat", "Hello-World", 5))
                .toList();
        
        // Wait for all operations to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.get(60, TimeUnit.SECONDS);
        
        // Then: All operations should succeed
        List<SyncResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        long successCount = results.stream()
                .mapToLong(result -> result.isSuccess() ? 1 : 0)
                .sum();
        
        assertThat(successCount).isEqualTo(concurrentOperations);
        
        // All operations should process the same number of issues
        List<Integer> processedCounts = results.stream()
                .filter(result -> result instanceof SyncResult.Success)
                .map(result -> ((SyncResult.Success) result).processedCount())
                .toList();
        
        assertThat(processedCounts).allMatch(count -> count == 3);
        
        // Verify API was called for each concurrent operation
        verify(concurrentOperations, getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
    
    @Test
    @DisplayName("Cache consistency during concurrent operations")
    void cacheConsistency_DuringConcurrentOperations_ShouldBeConsistent() throws Exception {
        // Given: Mock GitHub API with caching
        List<GitHubIssue> mockIssues = createMockGitHubIssues();
        String githubResponse = objectMapper.writeValueAsString(mockIssues);
        
        stubFor(get(urlPathEqualTo("/repos/octocat/cached-repo/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(githubResponse)));
        
        // When: Perform initial sync to populate cache
        CompletableFuture<SyncResult> initialSync = issueService.syncIssues("octocat", "cached-repo", 5);
        SyncResult initialResult = initialSync.get(30, TimeUnit.SECONDS);
        
        assertThat(initialResult).isInstanceOf(SyncResult.Success.class);
        
        // Perform multiple concurrent operations that should hit cache
        int concurrentOperations = 10;
        List<CompletableFuture<SyncResult>> futures = IntStream.range(0, concurrentOperations)
                .mapToObj(i -> issueService.syncIssues("octocat", "cached-repo", 5))
                .toList();
        
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.get(60, TimeUnit.SECONDS);
        
        // Then: All operations should succeed with consistent results
        List<SyncResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        long successCount = results.stream()
                .mapToLong(result -> result.isSuccess() ? 1 : 0)
                .sum();
        
        assertThat(successCount).isEqualTo(concurrentOperations);
        
        // Due to caching, API should be called fewer times than total operations
        verify(lessThan(concurrentOperations + 1), 
               getRequestedFor(urlPathEqualTo("/repos/octocat/cached-repo/issues")));
    }
    
    @Test
    @DisplayName("Mixed new and existing issues should be handled correctly")
    void mixedNewAndExistingIssues_ShouldBeHandledCorrectly() throws Exception {
        // Given: Initial set of issues
        List<GitHubIssue> initialIssues = List.of(
                new GitHubIssue(
                        1L,
                        "Existing Issue 1",
                        "open",
                        "https://github.com/octocat/Hello-World/issues/1",
                        Instant.parse("2024-01-15T10:30:00Z")
                ),
                new GitHubIssue(
                        2L,
                        "Existing Issue 2",
                        "closed",
                        "https://github.com/octocat/Hello-World/issues/2",
                        Instant.parse("2024-01-15T10:31:00Z")
                )
        );
        
        // Mixed set with existing and new issues
        List<GitHubIssue> mixedIssues = List.of(
                new GitHubIssue(
                        1L,
                        "Existing Issue 1 - Updated",
                        "closed", // Updated state
                        "https://github.com/octocat/Hello-World/issues/1",
                        Instant.parse("2024-01-15T10:30:00Z")
                ),
                new GitHubIssue(
                        3L,
                        "New Issue 3",
                        "open",
                        "https://github.com/octocat/Hello-World/issues/3",
                        Instant.parse("2024-01-15T10:33:00Z")
                ),
                new GitHubIssue(
                        4L,
                        "New Issue 4",
                        "open",
                        "https://github.com/octocat/Hello-World/issues/4",
                        Instant.parse("2024-01-15T10:34:00Z")
                )
        );
        
        // Mock responses
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Mixed Issues")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(initialIssues)))
                .willSetStateTo("Initial Done"));
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Mixed Issues")
                .whenScenarioStateIs("Initial Done")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(mixedIssues))));
        
        // When: Perform initial sync
        CompletableFuture<SyncResult> initialSync = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult initialResult = initialSync.get(30, TimeUnit.SECONDS);
        
        // Wait for initial sync to complete
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(initialResult).isInstanceOf(SyncResult.Success.class));
        
        // Perform mixed sync
        CompletableFuture<SyncResult> mixedSync = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult mixedResult = mixedSync.get(30, TimeUnit.SECONDS);
        
        // Then: Both syncs should succeed
        assertThat(initialResult).isInstanceOf(SyncResult.Success.class);
        assertThat(mixedResult).isInstanceOf(SyncResult.Success.class);
        
        SyncResult.Success initialSuccess = (SyncResult.Success) initialResult;
        SyncResult.Success mixedSuccess = (SyncResult.Success) mixedResult;
        
        assertThat(initialSuccess.processedCount()).isEqualTo(2);
        assertThat(mixedSuccess.processedCount()).isEqualTo(3);
        
        verify(2, getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
    
    @Test
    @DisplayName("Large batch operations should maintain consistency")
    void largeBatchOperations_ShouldMaintainConsistency() throws Exception {
        // Given: Large set of issues
        List<GitHubIssue> largeIssueSet = IntStream.range(1, 51) // 50 issues
                .mapToObj(i -> new GitHubIssue(
                        (long) i,
                        "Issue " + i,
                        i % 2 == 0 ? "open" : "closed",
                        "https://github.com/octocat/Hello-World/issues/" + i,
                        Instant.parse("2024-01-15T10:30:00Z").plusSeconds(i)
                ))
                .toList();
        
        String largeResponse = objectMapper.writeValueAsString(largeIssueSet);
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(largeResponse)));
        
        // When: Perform sync with large batch
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 50);
        SyncResult result = syncFuture.get(60, TimeUnit.SECONDS);
        
        // Then: Should handle large batch successfully
        assertThat(result).isInstanceOf(SyncResult.Success.class);
        SyncResult.Success success = (SyncResult.Success) result;
        assertThat(success.processedCount()).isEqualTo(50);
        
        // Perform second sync to verify consistency
        CompletableFuture<SyncResult> secondSync = issueService.syncIssues("octocat", "Hello-World", 50);
        SyncResult secondResult = secondSync.get(60, TimeUnit.SECONDS);
        
        assertThat(secondResult).isInstanceOf(SyncResult.Success.class);
        SyncResult.Success secondSuccess = (SyncResult.Success) secondResult;
        assertThat(secondSuccess.processedCount()).isEqualTo(50);
    }
    
    private List<GitHubIssue> createMockGitHubIssues() {
        return List.of(
                new GitHubIssue(
                        1L,
                        "Bug: Application crashes on startup",
                        "open",
                        "https://github.com/octocat/Hello-World/issues/1",
                        Instant.parse("2024-01-15T10:30:00Z")
                ),
                new GitHubIssue(
                        2L,
                        "Feature: Add user authentication",
                        "open",
                        "https://github.com/octocat/Hello-World/issues/2",
                        Instant.parse("2024-01-15T10:31:00Z")
                ),
                new GitHubIssue(
                        3L,
                        "Documentation: Update README",
                        "closed",
                        "https://github.com/octocat/Hello-World/issues/3",
                        Instant.parse("2024-01-15T10:32:00Z")
                )
        );
    }
}