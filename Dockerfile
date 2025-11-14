# Multi-stage build for paymentConsumer service
# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first for better layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies (this layer will be cached if pom.xml doesn't change)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application (skip tests for faster builds, run tests in CI/CD)
RUN ./mvnw clean package -DskipTests -B

# Extract layers for optimized runtime
RUN mkdir -p target/dependency && (cd target/dependency; jar -xf ../*.jar)

# Stage 2: Runtime image
FROM eclipse-temurin:21-jre-jammy

# Add metadata
LABEL maintainer="alok.kulkarni"
LABEL service="paymentConsumer"
LABEL version="0.0.1-SNAPSHOT"

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

# Set working directory
WORKDIR /app

# Copy dependencies and application from builder stage
COPY --from=builder /app/target/dependency/BOOT-INF/lib ./lib
COPY --from=builder /app/target/dependency/META-INF ./META-INF
COPY --from=builder /app/target/dependency/BOOT-INF/classes .

# Change ownership to non-root user
RUN chown -R spring:spring /app

# Switch to non-root user
USER spring:spring

# Expose the application port (default Spring Boot port)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Set JVM options for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -cp .:./lib/* com.alok.payment.paymentConsumer.PaymentConsumerApplication"]
