# ============================================================
# Stage 1: Build
# ============================================================
ARG GRAALVM_VERSION=21
FROM ghcr.io/graalvm/native-image-community:${GRAALVM_VERSION} AS build

RUN microdnf install -y maven && microdnf clean all

WORKDIR /workspace

# Copy POM first to cache dependency layer
COPY pom.xml .

# Download dependencies (cached unless pom.xml changes)
RUN mvn dependency:go-offline -B --no-transfer-progress

# Copy source code and build the fat JAR, skipping tests
COPY src src
RUN mvn package -DskipTests -B --no-transfer-progress

# ============================================================
# Stage 2: Runtime
# ============================================================
FROM ghcr.io/graalvm/jdk-community:${GRAALVM_VERSION} AS runtime

# Create a non-root user for security
RUN microdnf install -y shadow-utils wget && microdnf clean all \
  && groupadd -r appgroup \
  && useradd -r -g appgroup -d /app -s /sbin/nologin appuser

WORKDIR /app

# Copy the built JAR from the build stage
COPY --from=build --chown=appuser:appgroup /workspace/target/*.jar app.jar

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
