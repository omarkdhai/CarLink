# ============================================
# CarLink backend — multi-stage production image
# Build with Maven, runtime on plain JRE.
# ============================================

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Optional mirror for Maven Central, e.g.
#   --build-arg MAVEN_MIRROR_URL=https://maven-central.storage-download.googleapis.com/maven2
# Empty by default, so artifacts still come from Maven Central. Useful when
# Central rate-limits your IP (HTTP 429). settings.xml lives outside ~/.m2 so
# the cache mount below never shadows it.
ARG MAVEN_MIRROR_URL=""
RUN set -eu; \
    mkdir -p /opt/mvn; \
    { \
      echo '<settings>'; \
      echo '  <mirrors>'; \
      if [ -n "$MAVEN_MIRROR_URL" ]; then \
        echo '    <mirror>'; \
        echo '      <id>build-mirror</id>'; \
        echo '      <mirrorOf>central</mirrorOf>'; \
        echo "      <url>${MAVEN_MIRROR_URL}</url>"; \
        echo '    </mirror>'; \
      fi; \
      echo '  </mirrors>'; \
      echo '</settings>'; \
    } > /opt/mvn/settings.xml

# Dependencies first, so this step is only invalidated when pom.xml changes.
#
# The cache mount is the important part: ~/.m2 survives between builds, so the
# first build downloads the dependencies and every later build reuses them.
# Without it each cold build re-downloads ~3 GB and hammers Central hard enough
# to earn a 429. Both this step and the package step below must mount the same
# cache, otherwise package starts from an empty repository.
#
# Resolution is also throttled and retried: maven.artifact.threads caps the
# concurrent requests and the loop backs off and tries again if Central is
# still throttling.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    set -eu; \
    for attempt in 1 2 3; do \
      if mvn -B -ntp -s /opt/mvn/settings.xml dependency:go-offline \
           -Dmaven.artifact.threads=2 \
           -Dmaven.wagon.http.retryHandler.count=5; then \
        exit 0; \
      fi; \
      echo "dependency resolution attempt ${attempt} failed, backing off"; \
      sleep $((attempt * 30)); \
    done; \
    exit 1

# Source + build (skip tests: they run in CI, not in the image)
COPY src ./src
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp -s /opt/mvn/settings.xml package -DskipTests \
        -Dmaven.artifact.threads=2 \
        -Dmaven.wagon.http.retryHandler.count=5

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Non-root user for production safety; curl for the compose healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r carlink && useradd -r -g carlink carlink

COPY --from=build /build/target/carlink-backend-0.0.1-SNAPSHOT.jar app.jar

USER carlink
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]