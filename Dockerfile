# ===================================================================
# Aggarly — Multi-stage Dockerfile
# ===================================================================
# Build:   docker build -t aggarly .
# Run:     docker run -p 8080:8080 aggarly
# ===================================================================

# --------------------
# Stage 1: Build
# --------------------
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (layer caching for dependencies)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Download dependencies (cached unless pom.xml changes)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application (skip tests — they run in CI)
RUN ./mvnw clean package -DskipTests -B

# --------------------
# Stage 2: Runtime
# --------------------
FROM eclipse-temurin:17-jre-alpine AS runtime

WORKDIR /app

# Create non-root user for security
RUN addgroup -S aggarly && adduser -S aggarly -G aggarly

# Copy the built JAR
COPY --from=builder /app/target/*.jar app.jar

# Set ownership
RUN chown -R aggarly:aggarly /app

# Switch to non-root user
USER aggarly

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
