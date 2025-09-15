# Integration Tests for GitHub-Firebase Connector

This directory contains comprehensive integration and end-to-end tests for the GitHub-Firebase Connector application.

## Test Structure

### Test Classes

1. **EndToEndIntegrationTest** - Complete workflow testing
   - Tests the full sync pipeline from GitHub API to Firestore
   - Uses TestContainers for Firestore Emulator
   - Verifies data transformation and storage
   - Tests error scenarios and recovery

2. **PerformanceIntegrationTest** - Performance and scalability testing
   - Concurrent sync operations
   - Caching performance verification
   - Memory usage under load
   - High-volume operations

3. **ErrorHandlingIntegrationTest** - Error resilience testing
   - Network failures and timeouts
   - Authentication errors
   - Rate limiting scenarios
   - Invalid data handling
   - Retry mechanisms

4. **DataConsistencyIntegrationTest** - Data integrity testing
   - Duplicate detection and handling
   - Concurrent access consistency
   - Cache consistency
   - Update vs insert behavior

5. **SimpleIntegrationTest** - Basic functionality verification
   - Lightweight test without TestContainers
   - Uses mocked Firestore repository
   - Quick verification of core functionality

6. **ComprehensiveIntegrationTestSuite** - Test suite runner
   - Runs all integration tests together
   - Provides comprehensive coverage report

### Test Configuration Profiles

Each test class uses a specific Spring profile for configuration:

- `integration-test` - EndToEndIntegrationTest
- `performance-test` - PerformanceIntegrationTest  
- `error-test` - ErrorHandlingIntegrationTest
- `consistency-test` - DataConsistencyIntegrationTest
- `test` - SimpleIntegrationTest

## Running Tests

### Prerequisites

1. **Docker** - Required for TestContainers (Firestore Emulator)
2. **Java 21** - Required for the application
3. **Gradle** - Build tool

### Individual Test Execution

```bash
# Run simple integration test (no Docker required)
./gradlew test --tests "com.company.connector.integration.SimpleIntegrationTest"

# Run end-to-end tests (requires Docker)
./gradlew test --tests "com.company.connector.integration.EndToEndIntegrationTest"

# Run performance tests
./gradlew test --tests "com.company.connector.integration.PerformanceIntegrationTest"

# Run error handling tests
./gradlew test --tests "com.company.connector.integration.ErrorHandlingIntegrationTest"

# Run data consistency tests
./gradlew test --tests "com.company.connector.integration.DataConsistencyIntegrationTest"
```

### Complete Test Suite

```bash
# Run all integration tests
./gradlew test --tests "com.company.connector.integration.*"

# Run with specific profile
./gradlew test --tests "com.company.connector.integration.*" -Dspring.profiles.active=integration-test
```

## Test Features

### TestContainers Integration

The integration tests use TestContainers to provide:
- **Firestore Emulator** - Real Firestore instance for testing
- **Isolated environments** - Each test gets a fresh database
- **Automatic cleanup** - Containers are destroyed after tests

### WireMock Integration

All tests use WireMock to simulate GitHub API:
- **Realistic API responses** - Mock real GitHub API behavior
- **Error simulation** - Test various failure scenarios
- **Rate limiting** - Simulate GitHub rate limits
- **Network issues** - Test timeout and connection failures

### Comprehensive Coverage

The tests verify:

#### Functional Requirements
- ✅ GitHub API integration
- ✅ Data transformation
- ✅ Firestore storage
- ✅ Duplicate handling
- ✅ Error recovery
- ✅ Caching behavior

#### Non-Functional Requirements
- ✅ Performance under load
- ✅ Concurrent operations
- ✅ Memory usage
- ✅ Error resilience
- ✅ Data consistency
- ✅ Cache effectiveness

#### Integration Points
- ✅ REST API endpoints
- ✅ Health checks
- ✅ Monitoring metrics
- ✅ Configuration validation
- ✅ Scheduled operations

## Test Data

### Mock GitHub Issues

Tests use realistic GitHub issue data:
```json
{
  "id": 1,
  "title": "Bug: Application crashes on startup",
  "state": "open",
  "html_url": "https://github.com/octocat/Hello-World/issues/1",
  "created_at": "2024-01-15T10:30:00Z"
}
```

### Test Scenarios

1. **Happy Path** - Normal sync operations
2. **Error Scenarios** - Various failure modes
3. **Edge Cases** - Boundary conditions
4. **Performance** - High load situations
5. **Concurrency** - Multiple simultaneous operations

## Troubleshooting

### Common Issues

1. **Docker not running**
   ```
   Error: Could not start container
   Solution: Start Docker Desktop
   ```

2. **Port conflicts**
   ```
   Error: Port already in use
   Solution: Tests use random ports, restart if needed
   ```

3. **Memory issues**
   ```
   Error: OutOfMemoryError
   Solution: Increase JVM heap size: -Xmx2g
   ```

### Debug Mode

Run tests with debug logging:
```bash
./gradlew test --tests "com.company.connector.integration.*" --debug
```

### Test Reports

After running tests, view reports at:
- `build/reports/tests/test/index.html` - Test results
- `build/test-results/test/` - JUnit XML reports

## Continuous Integration

### GitHub Actions

Example CI configuration:
```yaml
- name: Run Integration Tests
  run: ./gradlew test --tests "com.company.connector.integration.*"
  env:
    SPRING_PROFILES_ACTIVE: integration-test
```

### Test Execution Time

Approximate execution times:
- SimpleIntegrationTest: ~5 seconds
- EndToEndIntegrationTest: ~30 seconds
- PerformanceIntegrationTest: ~60 seconds
- ErrorHandlingIntegrationTest: ~45 seconds
- DataConsistencyIntegrationTest: ~40 seconds

Total suite execution: ~3 minutes

## Best Practices

### Test Isolation
- Each test class uses isolated containers
- Tests clean up after themselves
- No shared state between tests

### Resource Management
- Containers are automatically destroyed
- WireMock servers are properly stopped
- Caches are cleared between tests

### Assertions
- Use AssertJ for readable assertions
- Verify both positive and negative cases
- Check timing and performance metrics

### Error Testing
- Test all error scenarios
- Verify retry mechanisms
- Check error messages and logging