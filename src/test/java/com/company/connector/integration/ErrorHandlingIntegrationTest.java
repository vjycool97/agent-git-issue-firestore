package com.company.connector.integration;

import com.company.connector.model.SyncResult;
import com.company.connector.service.IssueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for comprehensive error handling scenarios.
 * 
 * These tests verify the system's resilience and error handling capabilities:
 * - Network failures and timeouts
 * - Invalid data handling
 * - Authentication errors
 * - Rate limiting scenarios
 * - Firestore connection issues
 * - Retry mechanisms
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("error-test")
@Testcontainers
class ErrorHandlingIntegrationTest {
    
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
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Configure Firestore emulator
        registry.add("firebase.project-id", () -> "test-project");
        registry.add("firebase.emulator.host", () -> "localhost:" + firestoreEmulator.getMappedPort(8080));
        registry.add("firebase.use-emulator", () -> "true");
        
        // Configure GitHub API to use WireMock
        registry.add("github.api-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("github.token", () -> "test-token");
        
        // Configure retry settings for testing
        registry.add("github.retry.max-attempts", () -> "3");
        registry.add("github.retry.backoff-delay", () -> "100ms");
        registry.add("github.timeout", () -> "5s");
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
    @DisplayName("Network timeout handling")
    void networkTimeout_ShouldRetryAndEventuallyFail() throws Exception {
        // Given: Mock GitHub API with timeout
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withFixedDelay(10000) // 10 second delay, longer than timeout
                        .withStatus(200)
                        .withBody("[]")));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(60, TimeUnit.SECONDS);
        
        // Then: Should fail due to timeout
        assertThat(result).isInstanceOf(SyncResult.Failure.class);
        SyncResult.Failure failure = (SyncResult.Failure) result;
        assertThat(failure.error()).containsIgnoringCase("timeout");
        
        // Verify retry attempts were made
        verify(moreThan(1), getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
    
    @Test
    @DisplayName("GitHub API authentication error handling")
    void githubAuthenticationError_ShouldFailGracefully() throws Exception {
        // Given: Mock GitHub API with authentication error
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "message": "Bad credentials",
                                "documentation_url": "https://docs.github.com/rest"
                            }
                            """)));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Should fail with authentication error
        assertThat(result).isInstanceOf(SyncResult.Failure.class);
        SyncResult.Failure failure = (SyncResult.Failure) result;
        assertThat(failure.error()).containsIgnoringCase("401");
        assertThat(failure.error()).containsIgnoringCase("credentials");
    }
    
    @Test
    @DisplayName("GitHub API rate limiting with retry")
    void githubRateLimiting_ShouldRetryAfterDelay() throws Exception {
        // Given: Mock rate limiting followed by success
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Rate Limiting")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(429)
                        .withHeader("X-RateLimit-Remaining", "0")
                        .withHeader("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(1).getEpochSecond()))
                        .withHeader("Retry-After", "1")
                        .withBody("""
                            {
                                "message": "API rate limit exceeded for user ID 1.",
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
                        .withBody("[]")));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(60, TimeUnit.SECONDS);
        
        // Then: Should eventually succeed after retry
        assertThat(result).isInstanceOf(SyncResult.Success.class);
        
        // Verify both requests were made
        verify(2, getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
    
    @Test
    @DisplayName("Invalid JSON response handling")
    void invalidJsonResponse_ShouldHandleGracefully() throws Exception {
        // Given: Mock GitHub API with invalid JSON
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{ invalid json response")));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Should fail with parsing error
        assertThat(result).isInstanceOf(SyncResult.Failure.class);
        SyncResult.Failure failure = (SyncResult.Failure) result;
        assertThat(failure.error()).containsIgnoringCase("json");
    }
    
    @Test
    @DisplayName("Malformed issue data handling")
    void malformedIssueData_ShouldHandlePartialFailures() throws Exception {
        // Given: Mock GitHub API with mixed valid/invalid data
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            [
                                {
                                    "id": 1,
                                    "title": "Valid Issue",
                                    "state": "open",
                                    "html_url": "https://github.com/octocat/Hello-World/issues/1",
                                    "created_at": "2024-01-15T10:30:00Z"
                                },
                                {
                                    "id": "invalid-id",
                                    "title": "Invalid Issue",
                                    "state": "open",
                                    "html_url": "https://github.com/octocat/Hello-World/issues/2",
                                    "created_at": "2024-01-15T10:31:00Z"
                                },
                                {
                                    "id": 3,
                                    "title": null,
                                    "state": "open",
                                    "html_url": "https://github.com/octocat/Hello-World/issues/3",
                                    "created_at": "2024-01-15T10:32:00Z"
                                }
                            ]
                            """)));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Should handle partial failures appropriately
        if (result instanceof SyncResult.PartialFailure partialFailure) {
            assertThat(partialFailure.processedCount()).isGreaterThan(0);
            assertThat(partialFailure.failedCount()).isGreaterThan(0);
            assertThat(partialFailure.errors()).isNotEmpty();
        } else if (result instanceof SyncResult.Success success) {
            // If validation filters out invalid data at API level
            assertThat(success.processedCount()).isEqualTo(1);
        }
    }
    
    @Test
    @DisplayName("Repository not found error handling")
    void repositoryNotFound_ShouldFailGracefully() throws Exception {
        // Given: Mock GitHub API with 404 error
        stubFor(get(urlPathEqualTo("/repos/nonexistent/repo/issues"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "message": "Not Found",
                                "documentation_url": "https://docs.github.com/rest/repos/repos#get-a-repository"
                            }
                            """)));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("nonexistent", "repo", 5);
        SyncResult result = syncFuture.get(30, TimeUnit.SECONDS);
        
        // Then: Should fail with not found error
        assertThat(result).isInstanceOf(SyncResult.Failure.class);
        SyncResult.Failure failure = (SyncResult.Failure) result;
        assertThat(failure.error()).containsIgnoringCase("404");
        assertThat(failure.error()).containsIgnoringCase("not found");
    }
    
    @Test
    @DisplayName("Server error with retry mechanism")
    void serverError_ShouldRetryAndEventuallySucceed() throws Exception {
        // Given: Mock server error followed by success
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Server Error")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error"))
                .willSetStateTo("Error Occurred"));
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Server Error")
                .whenScenarioStateIs("Error Occurred")
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error"))
                .willSetStateTo("Still Erroring"));
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .inScenario("Server Error")
                .whenScenarioStateIs("Still Erroring")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(60, TimeUnit.SECONDS);
        
        // Then: Should eventually succeed after retries
        assertThat(result).isInstanceOf(SyncResult.Success.class);
        
        // Verify retry attempts were made
        verify(3, getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
    
    @Test
    @DisplayName("Connection refused error handling")
    void connectionRefused_ShouldFailGracefully() throws Exception {
        // Given: Stop WireMock to simulate connection refused
        wireMockServer.stop();
        
        try {
            // When: Trigger sync operation
            CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
            SyncResult result = syncFuture.get(60, TimeUnit.SECONDS);
            
            // Then: Should fail with connection error
            assertThat(result).isInstanceOf(SyncResult.Failure.class);
            SyncResult.Failure failure = (SyncResult.Failure) result;
            assertThat(failure.error()).containsIgnoringCase("connection");
        } finally {
            // Restart WireMock for other tests
            wireMockServer.start();
            WireMock.configureFor("localhost", wireMockServer.port());
        }
    }
    
    @Test
    @DisplayName("Large response handling")
    void largeResponse_ShouldHandleEfficiently() throws Exception {
        // Given: Mock GitHub API with large response
        StringBuilder largeResponse = new StringBuilder("[");
        for (int i = 1; i <= 1000; i++) {
            if (i > 1) largeResponse.append(",");
            largeResponse.append(String.format("""
                {
                    "id": %d,
                    "title": "Issue %d - This is a very long title that simulates real-world issue titles which can be quite lengthy and contain detailed descriptions of the problem being reported",
                    "state": "open",
                    "html_url": "https://github.com/octocat/Hello-World/issues/%d",
                    "created_at": "2024-01-15T10:30:00Z"
                }
                """, i, i, i));
        }
        largeResponse.append("]");
        
        stubFor(get(urlPathEqualTo("/repos/octocat/Hello-World/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(largeResponse.toString())));
        
        // When: Trigger sync operation
        CompletableFuture<SyncResult> syncFuture = issueService.syncIssues("octocat", "Hello-World", 5);
        SyncResult result = syncFuture.get(60, TimeUnit.SECONDS);
        
        // Then: Should handle large response successfully
        assertThat(result).isInstanceOf(SyncResult.Success.class);
        SyncResult.Success success = (SyncResult.Success) result;
        assertThat(success.processedCount()).isEqualTo(5); // Should respect limit parameter
    }
    
    @Test
    @DisplayName("Concurrent error scenarios")
    void concurrentErrorScenarios_ShouldHandleIndependently() throws Exception {
        // Given: Mock different error responses for different repos
        stubFor(get(urlPathEqualTo("/repos/octocat/repo1/issues"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withBody("Not Found")));
        
        stubFor(get(urlPathEqualTo("/repos/octocat/repo2/issues"))
                .willReturn(aResponse()
                        .withStatus(429)
                        .withBody("Rate Limited")));
        
        stubFor(get(urlPathEqualTo("/repos/octocat/repo3/issues"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
        
        // When: Trigger concurrent sync operations
        CompletableFuture<SyncResult> sync1 = issueService.syncIssues("octocat", "repo1", 5);
        CompletableFuture<SyncResult> sync2 = issueService.syncIssues("octocat", "repo2", 5);
        CompletableFuture<SyncResult> sync3 = issueService.syncIssues("octocat", "repo3", 5);
        
        CompletableFuture.allOf(sync1, sync2, sync3).get(60, TimeUnit.SECONDS);
        
        // Then: Each should handle its error independently
        SyncResult result1 = sync1.get();
        SyncResult result2 = sync2.get();
        SyncResult result3 = sync3.get();
        
        assertThat(result1).isInstanceOf(SyncResult.Failure.class);
        assertThat(result2).isInstanceOf(SyncResult.Failure.class);
        assertThat(result3).isInstanceOf(SyncResult.Success.class);
        
        // Verify all requests were made
        verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/repo1/issues")));
        verify(moreThan(0), getRequestedFor(urlPathEqualTo("/repos/octocat/repo2/issues")));
        verify(1, getRequestedFor(urlPathEqualTo("/repos/octocat/repo3/issues")));
    }
}