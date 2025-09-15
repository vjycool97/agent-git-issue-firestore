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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Simple integration test to verify basic functionality without TestContainers.
 * This test uses mocked dependencies to verify the core sync workflow.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SimpleIntegrationTest {
    
    private static WireMockServer wireMockServer;
    
    @Autowired
    private IssueService issueService;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private com.company.connector.repository.FirestoreRepository firestoreRepository;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
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
    @DisplayName("Simple sync workflow test")
    void simpleSyncWorkflow_ShouldSucceed() throws Exception {
        // Given: Mock GitHub API response
        List<GitHubIssue> mockIssues = List.of(
                new GitHubIssue(
                        1L,
                        "Test Issue",
                        "open",
                        "https://github.com/octocat/Hello-World/issues/1",
                        Instant.parse("2024-01-15T10:30:00Z")
                )
        );
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
        assertThat(success.processedCount()).isEqualTo(1);
        
        // Verify GitHub API was called
        verify(getRequestedFor(urlPathEqualTo("/repos/octocat/Hello-World/issues")));
    }
}