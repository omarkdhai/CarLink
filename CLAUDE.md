# CarLink Project

Anonymous QR-based contact with vehicle owners. Modular monolith (Spring Boot 3.2 / Java 17) + Angular 16.

## Verified environment (this machine)

- **Maven is NOT on PATH.** Use the cached distribution directly:
  ```bash
  export MAVEN_HOME="C:/Users/21628/.m2/wrapper/dists/apache-maven-3.9.12/59fe215c0ad6947fea90184bf7add084544567b927287592651fda3782e0e798"
  "$MAVEN_HOME/bin/mvn" <goal> ...
  ```
- **Java 17** is on PATH (`java -version` → 17.0.8).
- **Docker Desktop** must be running for Testcontainers and `docker compose`.
- **Docker Hub is unreachable** (registry-1.docker.io times out). Local images are pulled from a hubproxy; the ones needed already exist locally.
- **Testcontainers** integration tests must run with:
  ```bash
  TESTCONTAINERS_RYUK_DISABLED=true "$MAVEN_HOME/bin/mvn" test
  ```
  (Ryuk image can't be pulled; local `postgres:15-alpine` + `redis:7-alpine` are used.)
- The base test class is `src/test/java/com/carlink/AbstractIntegrationTest.java` — extend it for tests needing Postgres+Redis.
- **VS Code Java Language Server** runs as `java.exe` under `Code.exe` (parent PID ~24080). Do NOT `taskkill //F //IM java.exe` — that kills the IDE's language server, which then "respawns." Kill only the specific PID of an app you started.

## Run the app

```bash
# Infra (MailHog is behind an "email" profile — image not pullable offline)
docker compose up -d

# Dev profile (needs Postgres+Redis from compose)
cd backend && "$MAVEN_HOME/bin/mvn" spring-boot:run -Dspring-boot.run.profiles=dev

# Or run the packaged jar
java -jar target/carlink-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Health: `http://localhost:8080/actuator/health` → `{"status":"UP","groups":["liveness","readiness"]}`.
Swagger UI: `http://localhost:8080/swagger-ui.html`. MailHog UI: `http://localhost:8025`.

## Conventions

- Backend modules: `auth`, `user`, `vehicle`, `qr`, `contact`, `conversation`, `notification`, `admin`, `security`, `common`.
- Each module: Controller → Service → Repository, with DTO/Mapper/Entity/Exception layers. **Never expose JPA entities through REST.**
- Schema is Flyway-only (`ddl-auto: validate`). Migrations in `backend/src/main/resources/db/migration/V*.sql`.
- Owner phone numbers, QR tokens, passwords, and message content must NEVER be logged, returned by public/unauthenticated APIs, or embedded in URLs/QR/HTML. The one exception is the owner-facing conversation dashboard (`/api/v1/conversations`), which returns message content behind JWT authentication and vehicle ownership checks (404 on mismatch).
- QR encodes only `/c/{token}`; store `SHA-256(token)`.
- Configuration binds under `carlink.*` (`com.carlink.common.config.CarLinkProperties`).

## Implementation phases

1. ✅ Foundation (done: scaffolding, Docker, Flyway, health UP, tests green)
2. Auth + JWT → 3. Vehicles → 4. QR → 5. Public page → 6. WhatsApp/SMS → 7. Conversations → 8. Admin → 9. Hardening/tests → 10. CI/CD.

## Docs

Architecture: `docs/architecture.md` · Security model: `docs/security.md` · README at repo root.