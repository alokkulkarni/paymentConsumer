# Payment Consumer Docker Configuration

## Overview
This document describes the Docker configuration and environment variables for the Payment Consumer service.

## Dockerfile Changes

### Key Updates
1. **Port Configuration**: Changed from 8080 to 8082 to avoid conflicts with beneficiaries service
2. **curl Installation**: Added curl package for health check endpoint verification
3. **Environment Variables**: Added comprehensive environment variables for external service connections

### Environment Variables

#### External Service Connections
| Variable | Default Value | Description |
|----------|---------------|-------------|
| `BENEFICIARIES_SERVICE_URL` | `http://beneficiaries:8080` | URL for the beneficiaries service (uses Docker service name) |
| `PAYMENT_PROCESSOR_SERVICE_URL` | `http://paymentprocessor:8081` | URL for the payment processor service (uses Docker service name) |

#### Application Configuration
| Variable | Default Value | Description |
|----------|---------------|-------------|
| `SERVER_PORT` | `8082` | Port on which the application runs |
| `LOG_LEVEL_ROOT` | `INFO` | Root logging level |
| `LOG_LEVEL_APP` | `INFO` | Application-specific logging level |
| `HEALTH_DETAILS` | `always` | Health check detail level |
| `TIMEZONE` | `UTC` | Application timezone |

#### JVM Options
```bash
JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
```
- Uses container-aware memory settings
- Limits heap to 75% of container memory
- Exits on OutOfMemoryError for container restart

## Docker Compose Configuration

### Service Definition
```yaml
paymentconsumer:
  image: ghcr.io/alokkulkarni/paymentconsumer:latest
  container_name: paymentconsumer-app
  ports:
    - "${PAYMENTCONSUMER_PORT:-8082}:8082"
  depends_on:
    beneficiaries:
      condition: service_healthy
    paymentprocessor:
      condition: service_healthy
```

### Service Dependencies
The paymentConsumer service depends on:
1. **beneficiaries** (port 8080) - For retrieving beneficiary information
2. **paymentprocessor** (port 8081) - For processing payments

Both dependencies must be healthy before paymentConsumer starts.

### Network Configuration
- **Network**: `payment-network` (bridge mode)
- **Internal Communication**: Services communicate using Docker service names (e.g., `http://beneficiaries:8080`)
- **External Access**: Port 8082 is exposed to host

## Building the Docker Image

### Local Build
```bash
cd paymentConsumer
docker build -t paymentconsumer:latest .
```

### Multi-architecture Build (Optional)
```bash
docker buildx build --platform linux/amd64,linux/arm64 -t paymentconsumer:latest .
```

### Tag for GitHub Container Registry
```bash
docker tag paymentconsumer:latest ghcr.io/alokkulkarni/paymentconsumer:latest
docker push ghcr.io/alokkulkarni/paymentconsumer:latest
```

## Running with Docker Compose

### Start All Services
```bash
cd sit-test-repo
docker-compose up -d
```

### Start with Custom Environment Variables
```bash
# Create .env file
cat > .env << EOF
PAYMENTCONSUMER_PORT=8082
BENEFICIARIES_PORT=8080
PAYMENTPROCESSOR_PORT=8081
BENEFICIARIES_DB_PORT=5432
PAYMENTPROCESSOR_DB_PORT=5433
REDIS_PORT=6379
EOF

# Start services
docker-compose up -d
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f paymentconsumer

# With tail
docker-compose logs -f --tail=100 paymentconsumer
```

### Check Service Health
```bash
# Check container status
docker-compose ps

# Check health endpoint
curl http://localhost:8082/actuator/health

# Check all actuator endpoints
curl http://localhost:8082/actuator
```

## Running Standalone Container

### With Default Configuration
```bash
docker run -d \
  --name paymentconsumer \
  -p 8082:8082 \
  --network payment-network \
  ghcr.io/alokkulkarni/paymentconsumer:latest
```

### With Custom Service URLs
```bash
docker run -d \
  --name paymentconsumer \
  -p 8082:8082 \
  -e BENEFICIARIES_SERVICE_URL=http://beneficiaries:8080 \
  -e PAYMENT_PROCESSOR_SERVICE_URL=http://paymentprocessor:8081 \
  -e LOG_LEVEL_APP=DEBUG \
  --network payment-network \
  ghcr.io/alokkulkarni/paymentconsumer:latest
```

### With External Services (outside Docker network)
```bash
docker run -d \
  --name paymentconsumer \
  -p 8082:8082 \
  -e BENEFICIARIES_SERVICE_URL=http://host.docker.internal:8080 \
  -e PAYMENT_PROCESSOR_SERVICE_URL=http://host.docker.internal:8081 \
  ghcr.io/alokkulkarni/paymentconsumer:latest
```

## Troubleshooting

### Connection Issues

#### Cannot Connect to Beneficiaries Service
```bash
# Check if beneficiaries service is running
docker-compose ps beneficiaries

# Check beneficiaries logs
docker-compose logs beneficiaries

# Test connection from paymentconsumer container
docker exec paymentconsumer-app curl http://beneficiaries:8080/actuator/health

# Verify network
docker network inspect payment-network
```

#### Cannot Connect to Payment Processor Service
```bash
# Check if payment processor is running
docker-compose ps paymentprocessor

# Check payment processor logs
docker-compose logs paymentprocessor

# Test connection
docker exec paymentconsumer-app curl http://paymentprocessor:8081/actuator/health
```

### Service Startup Issues

#### Service Not Healthy
```bash
# Check container logs
docker logs paymentconsumer-app

# Check health endpoint directly
docker exec paymentconsumer-app curl -v http://localhost:8082/actuator/health

# Check environment variables
docker exec paymentconsumer-app env | grep -E '(BENEFICIARIES|PAYMENT_PROCESSOR|SERVER)'
```

#### Port Conflicts
```bash
# Check if port 8082 is already in use
lsof -i :8082

# Use different port
docker-compose up -d -e PAYMENTCONSUMER_PORT=8092
```

### Memory Issues
```bash
# Check container memory usage
docker stats paymentconsumer-app

# Adjust JVM options
docker run -d \
  -e JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError" \
  ghcr.io/alokkulkarni/paymentconsumer:latest
```

## Health Check Details

### Endpoint
```
GET http://localhost:8082/actuator/health
```

### Health Check Configuration
- **Interval**: 30 seconds
- **Timeout**: 10 seconds
- **Retries**: 3 attempts
- **Start Period**: 40 seconds (grace period for startup)

### Expected Response (Healthy)
```json
{
  "status": "UP",
  "groups": ["liveness", "readiness"]
}
```

### Circuit Breaker Status
```bash
# Check circuit breaker metrics
curl http://localhost:8082/actuator/health | jq '.components.circuitBreakers'
```

## Service Communication Flow

```
┌──────────────────┐
│   paymentConsumer │
│    (port 8082)    │
└────────┬──────────┘
         │
         ├──────────────────────────┐
         │                          │
         ▼                          ▼
┌────────────────────┐    ┌────────────────────┐
│   beneficiaries    │    │  paymentprocessor  │
│    (port 8080)     │    │    (port 8081)     │
└─────────┬──────────┘    └─────────┬──────────┘
          │                          │
          ▼                          ▼
┌────────────────────┐    ┌────────────────────┐
│  beneficiaries-db  │    │paymentprocessor-db │
│    (port 5432)     │    │    (port 5432)     │
└────────────────────┘    └────────────────────┘
          │
          ▼
┌────────────────────┐
│       redis        │
│    (port 6379)     │
└────────────────────┘
```

## Environment Variable Override Priority

1. **Docker run command** (-e flag): Highest priority
2. **docker-compose.yml environment section**: Medium priority
3. **Dockerfile ENV statements**: Default values (lowest priority)

Example:
```bash
# Dockerfile default
ENV BENEFICIARIES_SERVICE_URL="http://beneficiaries:8080"

# docker-compose.yml overrides to
environment:
  BENEFICIARIES_SERVICE_URL: http://beneficiaries:8080

# docker run -e overrides everything
docker run -e BENEFICIARIES_SERVICE_URL=http://custom-host:9090 ...
```

## Security Considerations

1. **Non-root User**: Application runs as `spring:spring` user (not root)
2. **No Secrets in Environment**: Use Docker secrets or external secret management for sensitive data
3. **Network Isolation**: Services communicate on isolated Docker network
4. **Health Checks**: Automatic container restart on health check failures

## Performance Tuning

### JVM Heap Size
```bash
# For containers with 1GB RAM
JAVA_OPTS="-XX:MaxRAMPercentage=75.0"  # Uses 750MB

# For containers with 2GB RAM
JAVA_OPTS="-XX:MaxRAMPercentage=70.0"  # Uses 1.4GB
```

### Connection Pool Tuning
Add to environment variables:
```yaml
environment:
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 10
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: 5
  SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT: 30000
```

### Circuit Breaker Tuning
Modify in application.yaml or via environment:
```yaml
environment:
  RESILIENCE4J_CIRCUITBREAKER_CONFIGS_DEFAULT_SLIDINGWINDOWSIZE: 20
  RESILIENCE4J_CIRCUITBREAKER_CONFIGS_DEFAULT_FAILURERATETHRESHOLD: 40
```

## Monitoring

### Prometheus Metrics
```bash
curl http://localhost:8082/actuator/prometheus
```

### Application Metrics
```bash
curl http://localhost:8082/actuator/metrics
curl http://localhost:8082/actuator/metrics/resilience4j.circuitbreaker.calls
```

### Logs
```bash
# Follow logs in real-time
docker logs -f paymentconsumer-app

# Export logs to file
docker logs paymentconsumer-app > paymentconsumer.log 2>&1
```

## Production Checklist

- [ ] Build optimized image with appropriate JVM settings
- [ ] Configure external secret management (not environment variables)
- [ ] Set up proper logging aggregation
- [ ] Configure monitoring and alerting (Prometheus + Grafana)
- [ ] Set resource limits (CPU/Memory) in docker-compose or Kubernetes
- [ ] Use image tags (not `latest`) for production deployments
- [ ] Configure proper health checks
- [ ] Set up backup strategy for volumes (if any)
- [ ] Configure proper network security rules
- [ ] Test circuit breaker behavior under load
- [ ] Validate graceful shutdown behavior
- [ ] Set up CI/CD pipeline for automated builds
