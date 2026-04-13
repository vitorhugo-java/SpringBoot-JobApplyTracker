# ============================================================
# Stage 1: Build
# ============================================================
FROM eclipse-temurin:21-jdk-alpine AS build

# Install Maven
RUN apk add --no-cache maven

WORKDIR /workspace

# Copy POM first to cache dependency layer
COPY backend/pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source code and build the fat JAR, skipping tests
COPY backend/src src
RUN mvn package -DskipTests -B --no-transfer-progress

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build /workspace/target/*.jar app.jar

# Ensure the app directory is owned by the non-root user
RUN chown -R appuser:appgroup /app

USER appuser

# Expose application port and management port
EXPOSE 8080
EXPOSE 8081

# Health check – relies on Spring Actuator management port
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1

# JVM tuning flags for containers (respects cgroup memory limits)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
