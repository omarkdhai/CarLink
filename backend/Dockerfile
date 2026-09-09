# ============================================
# CarLink backend — multi-stage production image
# Build with Maven, runtime on plain JRE.
# ============================================

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first (cached unless pom.xml changes)
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Source + build (skip tests: they run in CI, not in the image)
COPY src ./src
RUN mvn -B package -DskipTests

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