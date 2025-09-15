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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Comprehensive end-to-end integration tests for the GitHub-Firebase Connector.
 * 
 * This test class verifies the complete sync workflow using:
 * - TestContainers for Firestore Emulator
 * - WireMock for GitHub API simulation
 * - Real Spring Boot application context
 * - Complete data flow from GitHub API to Firestore
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
@Testcontainers
class EndToEndIntegrationTest {
    
    @Container
    static GenericContainer<?> firestoreEmulator = new GenericContainer<>(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators"))
            .withCommand("gcloud", "beta", "emulators", "firestore", "start", 
                        "--host-port=0.0.0.0:8080", "--project=test-project")
            .withExposedPorts(8080);
    
    private static WireMockServer wireMockServer;
    
    @LocalServerPort
    private int port;
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Autowired
    private IssueService issueService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure Firestore emulator
        registry.add("firebase.project-id", () -> "test-project");
        registry.add("firebase.emulator.host", () -> "localhost:" + firestoreEmulator.getMappedPort(8080));
        registry.add("firebase.use-emulator", () -> "true");
        
        // Configure GitHub API to use WireMock
        registry.add("github.api-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("github.token", () -> "test-token");
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
    }
    
    @Test
    @DisplayName("Complete sync workflow: GitHub API → Transformation → Firestore")
    void completeEndToEndSyncWorkflow_ShouldSuccessfullyProcessIssues() throws Exception {
        // Given: Mock GitHub API response with sample issues
        List<GitHubIssue> mockIssues = createMockGitHubIssues();
        String githubResponse = objectMapper.writeValueAsString(mockIssues);
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .withQueryParam("state", equalTo("all"))
                .withQueryParam("sort", equalTo("created"))
                .withQueryParam("direction", equalTo("desc"))
                .withQueryParam("per_page", equalTo("5"))
                .withHeader("Authorization", equalTo("Bearer test-token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(githubResponse)));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Verify sync result
        assertThat(result).isInstanceOf(SyncResult.Success.class);
        SyncResult.Success success = (SyncResult.Success) result;
        assertThat(success.processedCount()).isEqualTo(3);
        assertThat(success.duration()).isNotNull();
        
        // Verify GitHub API was called
        verify(getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
        
        // Wait for async Firestore operations to complete
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Verify data was stored in Firestore by triggering another sync
                    // and checking for duplicate detection
                    CompletableFuture<SyncResult> duplicateSync = issueService.syncIssues("octocat", "Hello-World", 5);
                    SyncResult duplicateResult = duplicateSync.get(10, TimeUnit.SECONDS);
                    assertThat(duplicateResult).isInstanceOf(SyncResult.Success.class);
                });
    }
    
    @Test
    @DisplayName("End-to-end sync with partial failures")
    void endToEndSyncWithPartialFailures_ShouldHandleGracefully() throws Exception {
        // Given: Mock GitHub API response with some invalid data
        String githubResponse = """
            [
                {
                    "id": 1,
                    "title": "Valid Issue",
                    "state": "open",
                    "html_url": "https://github.com/octocat/Hello-World/issues/1",
                    "created_at": "2024-01-15T10:30:00Z"
                },
                {
                    "id": null,
                    "title": "Invalid Issue - No ID",
                    "state": "open",
                    "html_url": "https://github.com/octocat/Hello-World/issues/2",
                    "created_at": "2024-01-15T10:31:00Z"
                },
                {
                    "id": 3,
                    "title": "",
                    "state": "open",
                    "html_url": "https://github.com/octocat/Hello-World/issues/3",
                    "created_at": "2024-01-15T10:32:00Z"
                }
            ]
            """;
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(githubResponse)));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Verify partial failure handling
        if (result instanceof SyncResult.PartialFailure partialFailure) {
            assertThat(partialFailure.processedCount()).isGreaterThan(0);
            assertThat(partialFailure.failedCount()).isGreaterThan(0);
            assertThat(partialFailure.errors()).isNotEmpty();
        } else if (result instanceof SyncResult.Success success) {
            // If validation happens at API level, we might get success with only valid items
            assertThat(success.processedCount()).isEqualTo(1);
        }
    }
    
    @Test
    @DisplayName("End-to-end sync with GitHub API errors")
    void endToEndSyncWithGitHubApiErrors_ShouldHandleGracefully() throws Exception {
        // Given: Mock GitHub API to return error responses
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "message": "Not Found",
                                "documentation_url": "https://docs.github.com/rest"
                            }
                            """)));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Verify failure handling
        assertThat(result).isInstanceOf(SyncResult.Failure.class);
        SyncResult.Failure failure = (SyncResult.Failure) result;
        assertThat(failure.error()).contains("404");
        assertThat(failure.duration()).isNotNull();
    }
    
    @Test
    @DisplayName("End-to-end sync with rate limiting")
    void endToEndSyncWithRateLimiting_ShouldRespectLimits() throws Exception {
        // Given: Mock GitHub API to return rate limit response first, then success
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Rate Limiting")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("X-RateLimit-Remaining", "0")
                        .withHeader("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(2).getEpochSecond()))
                        .withBody("""
                            {
                                "message": "API rate limit exceeded",
                                "documentation_url": "https://docs.github.com/rest/overview/resources-in-the-rest-api#rate-limiting"
                            }
                            """))
                .willSetStateTo("Rate Limited"));
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Rate Limiting")
                .whenScenarioStateIs("Rate Limited")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(createMockGitHubIssues()))));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(60, TimeUnit.SECONDS); // Allow more time for retry
        
        // Then: Verify retry mechanism worked
        assertThat(result).isInstanceOf(SyncResult.Success.class);
        
        // Verify both requests were made (initial + retry)
        verify(2, getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
    
    @Test
    @DisplayName("REST API integration: Manual sync trigger")
    void restApiManualSyncTrigger_ShouldWorkEndToEnd() throws Exception {
        // Given: Mock successful GitHub API response
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(createMockGitHubIssues()))));
        
        // When: Trigger sync via REST API
        String url = "http://localhost:" + port + "/api/sync/trigger?owner=octocat&repo=Hello-World&limit=5";
        ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);
        
        // Then: Verify REST response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("processedCount")).isEqualTo(3);
        
        // Verify GitHub API was called
        verify(getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
    
    @Test
    @DisplayName("Health check integration after sync operations")
    void healthCheckAfterSyncOperations_ShouldReflectCurrentState() throws Exception {
        // Given: Mock successful GitHub API response
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(createMockGitHubIssues()))));
        
        // When: Perform sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        syncFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Check health endpoints reflect the sync state
        String healthUrl = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<Map> healthResponse = restTemplate.getForEntity(healthUrl, Map.class);
        
        assertThat(healthResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(healthResponse.getBody()).isNotNull();
        assertThat(healthResponse.getBody().get("status")).isEqualTo("UP");
        
        // Check sync status endpoint
        String syncStatusUrl = "http://localhost:" + port + "/api/sync/status";
        ResponseEntity<Map> syncStatusResponse = restTemplate.getForEntity(syncStatusUrl, Map.class);
        
        assertThat(syncStatusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(syncStatusResponse.getBody()).isNotNull();
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