# Multi-stage Docker build for production-ready Spring Boot application
# Stage 1: Build stage with Maven
FROM maven:3.9.5-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy pom.xml and download dependencies (for better Docker layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage with optimized JRE
FROM eclipse-temurin:21-jre-alpine

# Install curl for health checks and other utilities
RUN apk add --no-cache curl dumb-init

# Create non-root user for security
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Create application directory and set ownership
RUN mkdir -p /app/logs && \
    chown -R appuser:appgroup /app

# Set working directory
WORKDIR /app

# Copy the built JAR file from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Create startup script for better JVM tuning
RUN echo '#!/bin/sh' > start.sh && \
    echo 'exec java \' >> start.sh && \
    echo '  -server \' >> start.sh && \
    echo '  -XX:+UseG1GC \' >> start.sh && \
    echo '  -XX:MaxGCPauseMillis=200 \' >> start.sh && \
    echo '  -XX:G1HeapRegionSize=32m \' >> start.sh && \
    echo '  -XX:+UseStringDeduplication \' >> start.sh && \
    echo '  -XX:+UseCompressedOops \' >> start.sh && \
    echo '  -XX:InitialRAMPercentage=50.0 \' >> start.sh && \
    echo '  -XX:MaxRAMPercentage=80.0 \' >> start.sh && \
    echo '  -XX:+ExitOnOutOfMemoryError \' >> start.sh && \
    echo '  -XX:+HeapDumpOnOutOfMemoryError \' >> start.sh && \
    echo '  -XX:HeapDumpPath=/app/logs/ \' >> start.sh && \
    echo '  -Djava.security.egd=file:/dev/./urandom \' >> start.sh && \
    echo '  -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-production} \' >> start.sh && \
    echo '  -Dlogging.file.name=/app/logs/document-search.log \' >> start.sh && \
    echo '  -Dmanagement.endpoints.jmx.exposure.exclude=* \' >> start.sh && \
    echo '  -jar app.jar "$@"' >> start.sh && \
    chmod +x start.sh && \
    chown appuser:appgroup start.sh

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Add health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Use dumb-init to handle signals properly and run the application
ENTRYPOINT ["dumb-init", "--"]
CMD ["./start.sh"]

# Metadata
LABEL maintainer="Enterprise Document Search Team"
LABEL version="1.0.0"
LABEL description="Production-ready Enterprise Document Search Application"
LABEL org.opencontainers.image.source="https://github.com/enterprise/document-search"