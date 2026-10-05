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

COPY pom.xml .

# Resolve dependencies before the source is copied, so this layer survives
# source-only changes.
#
# -U is essential, not a nicety. Maven records a failed resolution as a
# *.lastUpdated marker in the local repository and refuses to re-check while
# that marker is present. /root/.m2 is a persistent cache mount, so the marker
# outlives both this retry loop and future builds: a single transient failure
# on attempt 1 made attempts 2 and 3 fail instantly with "was not found during
# a previous attempt", which is a replay of the cache rather than a retry.
# That is why a shared CI egress IP hitting a Central hiccup could not recover.
# Forcing updates and dropping the markers makes the backoff actually work.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    set -eu; \
    for attempt in 1 2 3 4 5; do \
      find /root/.m2 -name '*.lastUpdated' -delete 2>/dev/null || true; \
      if mvn -B -ntp -U -s /opt/mvn/settings.xml \
           -Dmaven.repo.local=/root/.m2/repository \
           -Dmaven.artifact.threads=2 \
           -Dmaven.wagon.http.retryHandler.count=5 \
           dependency:go-offline; then \
        exit 0; \
      fi; \
      echo "dependency resolution attempt ${attempt}/5 failed, backing off"; \
      if [ "$attempt" -lt 5 ]; then sleep $((attempt * 20)); fi; \
    done; \
    echo "dependency resolution failed after 5 attempts"; \
    exit 1

COPY src ./src

# Source + build (skip tests: they run in CI, not in the image).
# -U again for the same reason as above: this step shares the /root/.m2 cache
# mount with dependency resolution and would otherwise honour a stale negative
# marker left by an earlier failed build.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    find /root/.m2 -name '*.lastUpdated' -delete 2>/dev/null; \
    mvn -B -ntp -U -s /opt/mvn/settings.xml \
        -Dmaven.repo.local=/root/.m2/repository \
        -Dmaven.artifact.threads=2 \
        -Dmaven.wagon.http.retryHandler.count=5 \
        package -DskipTests \
    && mv "$(ls -1 target/*.jar | head -n 1)" /build/app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Non-root user for production safety; curl for the compose healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r carlink && useradd -r -g carlink carlink

# Not target/carlink-backend-0.0.1-SNAPSHOT.jar: a version bump broke this COPY
# at build time. The build stage normalises the artifact name instead, so the
# image does not have to be edited when the project version changes.
COPY --from=build /build/app.jar app.jar

USER carlink
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]