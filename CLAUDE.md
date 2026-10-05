# CarLink Project

Anonymous QR-based contact with vehicle owners. Modular monolith (Spring Boot 3.2 / Java 17) + React 19 (Vite + TypeScript).

## Verified environment (this machine)

- **Maven is NOT on PATH.** Use the cached distribution directly:
  ```bash
  export MAVEN_HOME="C:/Users/21628/.m2/wrapper/dists/apache-maven-3.9.12/59fe215c0ad6947fea90184bf7add084544567b927287592651fda3782e0e798"
  "$MAVEN_HOME/bin/mvn" <goal> ...
  ```
- **Java 17** is on PATH (`java -version` → 17.0.8).
- **Docker Desktop** must be running for Testcontainers and `docker compose`.
- **Testcontainers** integration tests must run with:
  ```bash
  TESTCONTAINERS_RYUK_DISABLED=true "$MAVEN_HOME/bin/mvn" test
  ```
  (Ryuk image can't be pulled; local `postgres:15-alpine` + `redis:7-alpine` are used.)
- The base test class is `src/test/java/com/carlink/AbstractIntegrationTest.java` — extend it for tests needing Postgres+Redis.

## Run the app with Docker Compose (Whole Project)

```bash
# Start full project stack: PostgreSQL + Redis + Spring Boot Backend + React Frontend
docker-compose up -d --build
```

Access points:
- Frontend UI: `http://localhost`
- Backend API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health` → `{"status":"UP","groups":["liveness","readiness"]}`.
- Swagger UI: `http://localhost:8080/swagger-ui.html` or `http://localhost/swagger-ui/`.

## Conventions

- Backend modules: `auth`, `user`, `vehicle`, `qr`, `contact`, `conversation`, `notification`, `admin`, `security`, `common`, `order`, `sticker`.
- Each module: Controller → Service → Repository, with DTO/Mapper/Entity/Exception layers. **Never expose JPA entities through REST.**
- Schema is Flyway-only (`ddl-auto: validate`). Migrations in `backend/src/main/resources/db/migration/V*.sql`.
- Owner phone numbers, QR tokens, passwords, and message content must NEVER be logged, returned by public/unauthenticated APIs, or embedded in URLs/QR/HTML. Exceptions: (1) the owner-facing conversation dashboard (`/api/v1/conversations`) returns message content behind JWT auth + ownership checks (404 on mismatch), and `GET /api/v1/admin/reports/{id}` returns the last message to `ROLE_ADMIN` for moderation; (2) `POST /api/v1/public/orders` returns raw sticker tokens **exactly once** in the response (never stored/logged/re-sent; same response includes QR `imageDataUri`). See `docs/security.md`.
- QR encodes only `/c/{token}`; sticker QRs encode the same pattern — store `SHA-256(token)`.
- Configuration binds under `carlink.*` (`com.carlink.common.config.CarLinkProperties`).

## Docs

Architecture: `docs/architecture.md` · Security model: `docs/security.md` · Deployment runbook: `docs/deployment.md` · Progress log: `docs/progress.md` · README at repo root.