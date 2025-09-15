# GitHub-Firebase Connector Makefile
# Provides common development and deployment tasks

.PHONY: help build test clean run docker-build docker-run docker-stop docker-clean integration-test performance-test setup-dev

# Default target
.DEFAULT_GOAL := help

# Variables
APP_NAME := github-firebase-connector
VERSION := $(shell grep "version = " build.gradle | cut -d "'" -f 2)
DOCKER_IMAGE := $(APP_NAME):$(VERSION)
DOCKER_IMAGE_LATEST := $(APP_NAME):latest

# Colors for output
RED := \033[0;31m
GREEN := \033[0;32m
YELLOW := \033[1;33m
BLUE := \033[0;34m
NC := \033[0m # No Color

help: ## Show this help message
	@echo "$(BLUE)GitHub-Firebase Connector - Development Commands$(NC)"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*##/ { printf "  $(GREEN)%-20s$(NC) %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@echo ""
	@echo "$(YELLOW)Environment Variables:$(NC)"
	@echo "  GITHUB_TOKEN          - GitHub API token (required)"
	@echo "  FIREBASE_PROJECT_ID   - Firebase project ID (required)"
	@echo "  SPRING_PROFILES_ACTIVE - Spring profiles (default: dev)"

# Development Tasks
setup-dev: ## Setup development environment
	@echo "$(BLUE)Setting up development environment...$(NC)"
	@if [ ! -f ".env" ]; then \
		echo "$(YELLOW)Creating .env file from template...$(NC)"; \
		cp .env.example .env; \
		echo "$(RED)Please edit .env file with your configuration$(NC)"; \
	fi
	@mkdir -p config logs monitoring/grafana/dashboards monitoring/grafana/datasources
	@echo "$(GREEN)Development environment setup complete!$(NC)"

build: ## Build the application
	@echo "$(BLUE)Building application...$(NC)"
	./gradlew build
	@echo "$(GREEN)Build complete!$(NC)"

clean: ## Clean build artifacts
	@echo "$(BLUE)Cleaning build artifacts...$(NC)"
	./gradlew clean
	@echo "$(GREEN)Clean complete!$(NC)"

compile: ## Compile the application without running tests
	@echo "$(BLUE)Compiling application...$(NC)"
	./gradlew compileJava compileTestJava
	@echo "$(GREEN)Compilation complete!$(NC)"

# Testing Tasks
test: ## Run unit tests
	@echo "$(BLUE)Running unit tests...$(NC)"
	./gradlew test
	@echo "$(GREEN)Unit tests complete!$(NC)"

test-unit: ## Run only unit tests (excluding integration tests)
	@echo "$(BLUE)Running unit tests only...$(NC)"
	./gradlew test --exclude-task integrationTest
	@echo "$(GREEN)Unit tests complete!$(NC)"

integration-test: ## Run integration tests
	@echo "$(BLUE)Running integration tests...$(NC)"
	@echo "$(YELLOW)Note: This requires Docker to be running$(NC)"
	./gradlew test --tests "com.company.connector.integration.*"
	@echo "$(GREEN)Integration tests complete!$(NC)"

performance-test: ## Run performance tests
	@echo "$(BLUE)Running performance tests...$(NC)"
	./gradlew test --tests "com.company.connector.integration.PerformanceIntegrationTest"
	@echo "$(GREEN)Performance tests complete!$(NC)"

test-all: ## Run all tests (unit + integration)
	@echo "$(BLUE)Running all tests...$(NC)"
	./gradlew test
	@echo "$(GREEN)All tests complete!$(NC)"

# Application Execution
run: ## Run the application locally
	@echo "$(BLUE)Starting application locally...$(NC)"
	@if [ -f ".env" ]; then \
		export $$(cat .env | xargs) && ./gradlew bootRun; \
	else \
		echo "$(RED)Error: .env file not found. Run 'make setup-dev' first.$(NC)"; \
		exit 1; \
	fi

run-dev: ## Run the application in development mode
	@echo "$(BLUE)Starting application in development mode...$(NC)"
	SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun

# Docker Tasks
docker-build: ## Build Docker image
	@echo "$(BLUE)Building Docker image...$(NC)"
	docker build -t $(DOCKER_IMAGE) -t $(DOCKER_IMAGE_LATEST) .
	@echo "$(GREEN)Docker image built: $(DOCKER_IMAGE)$(NC)"

docker-run: ## Run application in Docker
	@echo "$(BLUE)Starting application with Docker Compose...$(NC)"
	@if [ ! -f ".env" ]; then \
		echo "$(RED)Error: .env file not found. Run 'make setup-dev' first.$(NC)"; \
		exit 1; \
	fi
	docker compose --env-file .env up -d
	@echo "$(GREEN)Application started! Access at http://localhost:8080$(NC)"
	@echo "$(YELLOW)Firestore Emulator UI: http://localhost:4000$(NC)"

docker-run-with-monitoring: ## Run application with monitoring stack
	@echo "$(BLUE)Starting application with monitoring...$(NC)"
	docker compose --profile monitoring --env-file .env up -d
	@echo "$(GREEN)Application and monitoring started!$(NC)"
	@echo "$(YELLOW)Application: http://localhost:8080$(NC)"
	@echo "$(YELLOW)Prometheus: http://localhost:9090$(NC)"
	@echo "$(YELLOW)Grafana: http://localhost:3000 (admin/admin)$(NC)"

docker-stop: ## Stop Docker containers
	@echo "$(BLUE)Stopping Docker containers...$(NC)"
	docker compose down
	@echo "$(GREEN)Containers stopped!$(NC)"

docker-logs: ## Show Docker container logs
	@echo "$(BLUE)Showing container logs...$(NC)"
	docker compose logs -f github-firebase-connector

docker-clean: ## Clean Docker images and containers
	@echo "$(BLUE)Cleaning Docker resources...$(NC)"
	docker compose down -v --remove-orphans
	docker rmi $(DOCKER_IMAGE) $(DOCKER_IMAGE_LATEST) 2>/dev/null || true
	docker system prune -f
	@echo "$(GREEN)Docker cleanup complete!$(NC)"

# Database Tasks
firestore-start: ## Start Firestore emulator only
	@echo "$(BLUE)Starting Firestore emulator...$(NC)"
	docker compose up -d firestore-emulator
	@echo "$(GREEN)Firestore emulator started at http://localhost:8081$(NC)"

firestore-stop: ## Stop Firestore emulator
	@echo "$(BLUE)Stopping Firestore emulator...$(NC)"
	docker compose stop firestore-emulator
	@echo "$(GREEN)Firestore emulator stopped!$(NC)"

# Monitoring Tasks
monitoring-start: ## Start monitoring stack only
	@echo "$(BLUE)Starting monitoring stack...$(NC)"
	docker compose --profile monitoring up -d prometheus grafana
	@echo "$(GREEN)Monitoring stack started!$(NC)"
	@echo "$(YELLOW)Prometheus: http://localhost:9090$(NC)"
	@echo "$(YELLOW)Grafana: http://localhost:3000$(NC)"

monitoring-stop: ## Stop monitoring stack
	@echo "$(BLUE)Stopping monitoring stack...$(NC)"
	docker compose stop prometheus grafana
	@echo "$(GREEN)Monitoring stack stopped!$(NC)"

# Health and Status
health: ## Check application health
	@echo "$(BLUE)Checking application health...$(NC)"
	@curl -s http://localhost:8080/actuator/health | jq '.' || echo "$(RED)Application not responding$(NC)"

status: ## Show application status
	@echo "$(BLUE)Application Status:$(NC)"
	@echo "$(YELLOW)Docker Containers:$(NC)"
	@docker compose ps
	@echo ""
	@echo "$(YELLOW)Application Health:$(NC)"
	@curl -s http://localhost:8080/actuator/health 2>/dev/null | jq '.status' || echo "$(RED)Not running$(NC)"

# Utility Tasks
format: ## Format code
	@echo "$(BLUE)Formatting code...$(NC)"
	./gradlew spotlessApply
	@echo "$(GREEN)Code formatting complete!$(NC)"

lint: ## Run code linting
	@echo "$(BLUE)Running code linting...$(NC)"
	./gradlew spotlessCheck
	@echo "$(GREEN)Linting complete!$(NC)"

deps: ## Show dependency tree
	@echo "$(BLUE)Showing dependency tree...$(NC)"
	./gradlew dependencies

security-scan: ## Run security vulnerability scan
	@echo "$(BLUE)Running security scan...$(NC)"
	./gradlew dependencyCheckAnalyze
	@echo "$(GREEN)Security scan complete!$(NC)"

# Documentation
docs: ## Generate documentation
	@echo "$(BLUE)Generating documentation...$(NC)"
	./gradlew javadoc
	@echo "$(GREEN)Documentation generated in build/docs/javadoc/$(NC)"

# Release Tasks
package: ## Package application for distribution
	@echo "$(BLUE)Packaging application...$(NC)"
	./gradlew bootJar
	@echo "$(GREEN)Package created: build/libs/$(APP_NAME)-$(VERSION).jar$(NC)"

release-build: clean test package docker-build ## Complete release build
	@echo "$(GREEN)Release build complete!$(NC)"
	@echo "$(YELLOW)JAR: build/libs/$(APP_NAME)-$(VERSION).jar$(NC)"
	@echo "$(YELLOW)Docker: $(DOCKER_IMAGE)$(NC)"

# Environment Management
env-check: ## Check required environment variables
	@echo "$(BLUE)Checking environment variables...$(NC)"
	@if [ -z "$$GITHUB_TOKEN" ]; then echo "$(RED)GITHUB_TOKEN is not set$(NC)"; exit 1; fi
	@if [ -z "$$FIREBASE_PROJECT_ID" ]; then echo "$(RED)FIREBASE_PROJECT_ID is not set$(NC)"; exit 1; fi
	@echo "$(GREEN)Environment variables are set!$(NC)"

# Quick Development Workflow
dev: setup-dev build run-dev ## Quick development setup and run

# Production Deployment
deploy: env-check release-build ## Deploy to production (customize as needed)
	@echo "$(BLUE)Deploying to production...$(NC)"
	@echo "$(YELLOW)Customize this target for your deployment method$(NC)"
	@echo "$(GREEN)Deployment preparation complete!$(NC)"

# Cleanup Tasks
clean-all: clean docker-clean ## Clean everything
	@echo "$(GREEN)Complete cleanup finished!$(NC)"