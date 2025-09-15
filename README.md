# GitHub-Firebase Connector

A Spring Boot application that synchronizes GitHub repository issues with Firebase Firestore.

## Prerequisites

- Java 21 or higher (for local development)
- Docker and Docker Compose (for containerized deployment)
- GitHub Personal Access Token
- Firebase Service Account credentials

## Quick Start with Docker (Recommended)

1. **Setup Environment**
```bash
# Clone the repository
git clone <repository-url>
cd github-firebase-connector

# Setup development environment
make setup-dev

# Edit .env with your configuration
vim .env
```

2. **Run with Docker**
```bash
# Start the application
make docker-run

# Check health
curl http://localhost:8080/actuator/health
```

3. **Access Services**
   - Application: http://localhost:8080
   - Health Check: http://localhost:8080/actuator/health
   - Firestore Emulator UI: http://localhost:4000

## Local Development

### Configuration

Set the following environment variables or use `.env` file:

```bash
export GITHUB_TOKEN=your_github_token
export FIREBASE_SERVICE_ACCOUNT_PATH=/path/to/service-account.json
export FIREBASE_PROJECT_ID=your_firebase_project_id
```

### Running Locally

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Or use Makefile
make run
```

## Available Commands

### Development
```bash
make setup-dev          # Setup development environment
make build              # Build application
make test               # Run unit tests
make integration-test   # Run integration tests
make run                # Run locally
```

### Docker
```bash
make docker-build       # Build Docker image
make docker-run         # Run with Docker Compose
make docker-stop        # Stop containers
make docker-logs        # View logs
make docker-clean       # Clean up resources
```

### Monitoring
```bash
make monitoring-start   # Start monitoring stack
make health            # Check application health
make status            # Show status
```

## Project Structure

```
src/
├── main/
│   ├── java/com/company/connector/
│   │   └── GitHubFirebaseConnectorApplication.java
│   └── resources/
│       └── application.yml
└── test/
    └── java/
```

## Features

- GitHub API integration with retry logic
- Firebase Firestore integration
- Configurable synchronization intervals
- Comprehensive error handling
- Spring Boot Actuator endpoints for monitoring
- Caching with Caffeine
- Docker containerization with multi-stage builds
- Docker Compose for local development
- Monitoring stack with Prometheus and Grafana
- Comprehensive integration test suite

## Deployment Options

### Docker Compose (Development)
```bash
# Basic deployment
docker-compose up -d

# With monitoring
docker-compose --profile monitoring up -d
```

### Production Deployment
See [DOCKER.md](DOCKER.md) for detailed deployment instructions including:
- Kubernetes manifests
- Security considerations
- Scaling strategies
- Monitoring setup

## Documentation

- [Docker Deployment Guide](DOCKER.md) - Comprehensive Docker deployment instructions
- [Integration Tests](src/test/java/com/company/connector/integration/README.md) - Testing documentation
- [API Documentation](http://localhost:8080/swagger-ui.html) - Interactive API docs (when running)