# Docker Deployment Guide

This guide covers Docker-based deployment options for the GitHub-Firebase Connector.

## Quick Start

### Prerequisites

- Docker and Docker Compose installed
- GitHub API token
- Firebase project with service account key

### 1. Setup Environment

```bash
# Copy environment template
cp .env.example .env

# Edit .env with your configuration
vim .env
```

### 2. Run with Docker Compose

```bash
# Start the application
make docker-run

# Or manually
docker-compose up -d
```

### 3. Access the Application

- Application: http://localhost:8080
- Health Check: http://localhost:8080/actuator/health
- Firestore Emulator UI: http://localhost:4000

## Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `GITHUB_TOKEN` | GitHub API token | - | Yes |
| `FIREBASE_PROJECT_ID` | Firebase project ID | - | Yes |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | Path to service account JSON | `/app/config/service-account.json` | Yes |
| `FIREBASE_USE_EMULATOR` | Use Firestore emulator | `false` | No |
| `SYNC_DEFAULT_ISSUE_LIMIT` | Default sync limit | `10` | No |
| `SYNC_BATCH_SIZE` | Batch processing size | `20` | No |
| `LOG_LEVEL` | Application log level | `INFO` | No |

### Service Account Configuration

Place your Firebase service account JSON file in the `config/` directory:

```bash
mkdir -p config
cp path/to/your/service-account.json config/service-account.json
```

## Docker Images

### Building the Image

```bash
# Build with Makefile
make docker-build

# Or manually
docker build -t github-firebase-connector:latest .
```

### Multi-stage Build

The Dockerfile uses a multi-stage build:

1. **Builder stage**: Compiles the application using Gradle
2. **Runtime stage**: Creates minimal runtime image with OpenJDK

### Image Optimization

- Uses OpenJDK 21 slim base image
- Non-root user for security
- Optimized JVM settings for containers
- Health check included

## Deployment Options

### 1. Docker Compose (Recommended for Development)

```bash
# Basic deployment
docker-compose up -d

# With monitoring stack
docker-compose --profile monitoring up -d
```

### 2. Docker Swarm

```bash
# Deploy to swarm
docker stack deploy -c docker-compose.yml github-connector
```

### 3. Kubernetes

See `k8s/` directory for Kubernetes manifests (if available).

### 4. Standalone Docker

```bash
# Run standalone container
docker run -d \
  --name github-firebase-connector \
  -p 8080:8080 \
  -e GITHUB_TOKEN=your_token \
  -e FIREBASE_PROJECT_ID=your_project \
  -v $(pwd)/config:/app/config:ro \
  github-firebase-connector:latest
```

## Services

### Main Application

- **Image**: Custom built from Dockerfile
- **Port**: 8080
- **Health Check**: `/actuator/health`
- **Volumes**: 
  - `./config:/app/config:ro` (configuration)
  - `./logs:/app/logs` (logs)

### Firestore Emulator

- **Image**: `gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators`
- **Ports**: 
  - 8081 (Firestore API)
  - 4000 (Web UI)
- **Use**: Development and testing

### Monitoring Stack (Optional)

#### Prometheus
- **Image**: `prom/prometheus:latest`
- **Port**: 9090
- **Config**: `monitoring/prometheus.yml`

#### Grafana
- **Image**: `grafana/grafana:latest`
- **Port**: 3000
- **Credentials**: admin/admin

## Makefile Commands

### Development

```bash
make setup-dev          # Setup development environment
make build              # Build application
make run                # Run locally
make docker-build       # Build Docker image
make docker-run         # Run with Docker Compose
```

### Testing

```bash
make test               # Run unit tests
make integration-test   # Run integration tests
make performance-test   # Run performance tests
```

### Docker Management

```bash
make docker-stop        # Stop containers
make docker-logs        # View logs
make docker-clean       # Clean up Docker resources
```

### Monitoring

```bash
make monitoring-start   # Start monitoring stack
make health            # Check application health
make status            # Show status
```

## Production Deployment

### Security Considerations

1. **Use secrets management** for sensitive data
2. **Enable HTTPS** with reverse proxy
3. **Limit container resources**
4. **Use read-only filesystems** where possible
5. **Regular security updates**

### Resource Limits

```yaml
# Example resource limits for production
services:
  github-firebase-connector:
    deploy:
      resources:
        limits:
          cpus: '1.0'
          memory: 1G
        reservations:
          cpus: '0.5'
          memory: 512M
```

### Scaling

```bash
# Scale with Docker Compose
docker-compose up -d --scale github-firebase-connector=3

# Scale with Docker Swarm
docker service scale github-connector_github-firebase-connector=3
```

## Monitoring and Observability

### Health Checks

- **Application**: `http://localhost:8080/actuator/health`
- **Detailed**: `http://localhost:8080/actuator/health/sync`
- **Metrics**: `http://localhost:8080/actuator/metrics`
- **Prometheus**: `http://localhost:8080/actuator/prometheus`

### Logs

```bash
# View application logs
docker-compose logs -f github-firebase-connector

# View all logs
docker-compose logs -f
```

### Metrics

Access Prometheus metrics at:
- Application metrics: http://localhost:8080/actuator/prometheus
- Prometheus UI: http://localhost:9090
- Grafana dashboards: http://localhost:3000

## Troubleshooting

### Common Issues

1. **Port conflicts**
   ```bash
   # Check port usage
   netstat -tulpn | grep :8080
   
   # Use different ports
   docker-compose up -d -p 8081:8080
   ```

2. **Permission issues**
   ```bash
   # Fix file permissions
   sudo chown -R $USER:$USER config/ logs/
   ```

3. **Memory issues**
   ```bash
   # Increase Docker memory limit
   # Docker Desktop: Settings > Resources > Memory
   
   # Or reduce JVM heap
   export JAVA_OPTS="-Xmx512m"
   ```

4. **Service account not found**
   ```bash
   # Verify file exists and is readable
   ls -la config/service-account.json
   
   # Check container mount
   docker exec github-firebase-connector ls -la /app/config/
   ```

### Debug Mode

```bash
# Run with debug logging
LOG_LEVEL=DEBUG docker-compose up -d

# Access container shell
docker exec -it github-firebase-connector sh
```

### Health Check Failures

```bash
# Check application status
curl http://localhost:8080/actuator/health

# Check container logs
docker-compose logs github-firebase-connector

# Restart service
docker-compose restart github-firebase-connector
```

## Backup and Recovery

### Configuration Backup

```bash
# Backup configuration
tar -czf config-backup.tar.gz config/

# Restore configuration
tar -xzf config-backup.tar.gz
```

### Data Backup

For production Firestore data, use Firebase backup tools:

```bash
# Export Firestore data
gcloud firestore export gs://your-backup-bucket/backup-folder
```

## Updates and Maintenance

### Application Updates

```bash
# Pull latest changes
git pull

# Rebuild and restart
make docker-build
docker-compose up -d --force-recreate
```

### System Maintenance

```bash
# Clean up unused resources
make docker-clean

# Update base images
docker-compose pull
docker-compose up -d --force-recreate
```