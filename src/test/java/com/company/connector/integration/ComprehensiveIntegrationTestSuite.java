package com.company.connector.integration;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive integration test suite that runs all integration tests.
 * 
 * This suite includes:
 * - End-to-end workflow tests
 * - Performance and concurrency tests
 * - Error handling and resilience tests
 * - Data consistency and duplicate handling tests
 * - Monitoring and health check integration tests
 * 
 * Usage:
 * - Run this suite to execute all integration tests
 * - Individual test classes can be run separately for focused testing
 * - Use different profiles for different test scenarios
 */
@Suite
@SuiteDisplayName("GitHub-Firebase Connector - Comprehensive Integration Test Suite")
@SelectPackages("com.company.connector.integration")
@IncludeClassNamePatterns({
    ".*IntegrationTest",
    ".*IntegrationTestSuite"
})
public class ComprehensiveIntegrationTestSuite {
    
    // This class serves as a test suite runner
    // Individual test classes are automatically discovered and executed
    
    /*
     * Test Execution Order and Dependencies:
     * 
     * 1. EndToEndIntegrationTest - Basic workflow verification
     * 2. DataConsistencyIntegrationTest - Data integrity verification
     * 3. ErrorHandlingIntegrationTest - Error resilience verification
     * 4. PerformanceIntegrationTest - Performance and scalability verification
     * 5. MonitoringIntegrationTest - Monitoring and observability verification
     * 
     * Each test class is independent and can be run separately.
     * TestContainers ensure isolated test environments.
     */
}