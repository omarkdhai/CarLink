# CarLink Development Progress

Status legend: ✅ done & verified · ⏳ in progress · ⬜ pending

## Phase 1 — Foundation ✅

**Goal:** project scaffolding, Docker infra (Postgres/Redis/MailHog), Flyway, baseline configuration.

### Files created

| Path | Purpose |
|------|---------|
| `backend/pom.xml` | Spring Boot 3.2.5, Java 17, all deps (JPA, Security, Redis, Flyway, ZXing, JWT, MapStruct, Testcontainers) |
| `backend/src/main/java/com/carlink/CarLinkApplication.java` | Entry point; excludes `UserDetailsServiceAutoConfiguration` |
| `backend/src/main/java/com/carlink/common/config/CarLinkProperties.java` | `carlink.*` config binding (jwt, qr, ratelimit, conversation, contact, security) |
| `backend/src/main/java/com/carlink/common/config/AppConfig.java` | Forwarded-header filter, config props registration |
| `backend/src/main/java/com/carlink/common/entity/BaseEntity.java` | `createdAt`/`updatedAt` auditing |
| `backend/src/main/java/com/carlink/common/exception/*` | `ApiException` hierarchy + `GlobalExceptionHandler` |
| `backend/src/main/java/com/carlink/common/dto/ApiError.java` | Standard error payload |
| `backend/src/main/java/com/carlink/security/config/SecurityConfig.java` | Stateless, CORS; **placeholder permit-all** (Phase 2 adds JWT) |
| `backend/src/main/resources/application.yml` (+ `-dev`, `-prod` profiles) | Configuration |
| `backend/src/main/resources/db/migration/V1__init.sql` | Full schema: users, vehicles, qr_codes, conversations, messages, refresh_tokens, password_reset_tokens, email_verification_tokens, reports, audit_logs |
| `docker-compose.yml` | postgres:15-alpine, redis:7-alpine, mailhog (behind `email` profile) |
| `.env.example`, `.gitignore`, `.dockerignore` | Environment + hygiene |
| `docs/architecture.md`, `docs/security.md` | Architecture & security model |
| `README.md`, `CLAUDE.md` | Docs / env notes |

### Verification results

- `mvn clean package` ✅ BUILD SUCCESS (test green).
- `ContextSmokeTest` (Testcontainers postgres:15-alpine + redis:7-alpine) ✅ passed — Flyway V1 applied, schema validated.
- Live stack (`docker compose up -d`) — postgres + redis healthy.
- Packaged jar boot (`--spring.profiles.active=dev`):
  - Health `{"status":"UP","groups":["liveness","readiness"]}`
  - Flyway validated, schema up to date (11 tables present)
  - Custom stateless security chain active (confirmed no CSRF / no form-login)
  - Redis `PONG` confirmed

### Notes / constraints discovered

- Docker Hub unreachable ⟹ (a) Ryuk disabled for tests, (b) `postgres:15-alpine`/`redis:7-alpine` used instead of `16-alpine`, (c) MailHog needs `docker compose --profile email up`.
- `flyway-database-postgresql` module **removed** — Spring Boot 3.2.5 pins Flyway 9.22.3 where Postgres support is in `flyway-core`. Adding it with no managed version would break the build.
- `org.testcontainers:redis` **does not exist** on Maven Central (only `com.redis:testcontainers-redis`); Redis containers use `GenericContainer`.

## Phase 2 — Authentication + JWT ✅

**Goal:** full auth flow — register, email verification, login with throttling, JWT access tokens, hashed + rotated refresh tokens, logout revocation, password reset, and an owner profile endpoint that never leaks the private phone.

### Files created

| Path | Purpose |
|------|---------|
| `common/security/TokenGenerator.java` | Secure-random opaque tokens + SHA-256 hashing |
| `auth/model/RefreshToken.java`, `AbstractOneTimeToken.java`, `PasswordResetToken.java`, `EmailVerificationToken.java` | Token entities (raw value never stored) |
| `auth/repository/*` | Token repositories |
| `auth/service/JwtService.java` | Issue/parse short-lived access tokens |
| `auth/service/AuthService.java` | register → verify → login → refresh (rotation) → logout (revocation) → password reset |
| `auth/service/CustomUserDetailsService.java`, `LoginAttemptService.java` | Principal loading; Redis-backed brute-force lockout (5 failures / 15 min) |
| `auth/controller/AuthController.java` | `/api/v1/auth/**` endpoints |
| `security/filter/JwtAuthenticationFilter.java` | Bearer-token validation, sets `UserPrincipal` |
| `security/config/SecurityConfig.java` | Stateless chain; route rules; 401/403 JSON errors |
| `user/*` | `UserResponse` (no phone), `UserController` (`/me` GET+PATCH), `UserService` |
| `notification/email/*` | `EmailSender` abstraction + `mock` (logs) / `smtp` adapters |
| Tests | `AuthFlowIntegrationTest` (4), `AuthServiceTest` (17), `JwtServiceTest` (3), `TokenGeneratorTest` (4) |

### Security properties verified

- Access token: JWT, 15 min. Refresh token: opaque, SHA-256 hashed at rest, 7 days.
- Refresh rotates the token and revokes the old one; reuse of a revoked token revokes the whole family.
- Login throttled after 5 failures (even the correct password is rejected while locked).
- Password reset invalidates all sessions; email/password-reset tokens are single-use.
- `/users/me` returns profile **without** `phone` — phone is accepted on register/profile edit but never serialized.

### Verification results

- Full suite: **29 tests green** (`mvn package`), incl. 4 live Testcontainers integration tests.
- Live `curl` of the running jar against the Docker Compose stack confirmed register → verify → login → `/me` (no phone) → refresh rotation → old-token rejection → logout revocation → password reset.

## Phase 3 — Vehicles ✅

**Goal:** owner-scoped vehicle CRUD with strict ownership validation — no user can read, modify, or delete another user's vehicle, and every response is phone-free.

### Files created

| Path | Purpose |
|------|---------|
| `vehicle/model/Vehicle.java`, `VehicleStatus.java` | Vehicle entity (ACTIVE / ARCHIVED) |
| `vehicle/repository/VehicleRepository.java` | Persistence, owner+status scoped queries |
| `vehicle/dto/CreateVehicleRequest.java`, `UpdateVehicleRequest.java`, `VehicleResponse.java` | Request/response DTOs (no owner data in responses) |
| `vehicle/service/VehicleService.java` | CRUD + ownership checks; soft 50-vehicle cap; unknown-status → 400 |
| `vehicle/controller/VehicleController.java` | `/api/v1/vehicles/**` |
| Tests | `VehicleServiceTest` (15), `VehicleFlowIntegrationTest` (3) |

### Design decisions

- **Ownership by 404, not 403:** a vehicle not owned by the caller resolves to "not found", so a cross-owner probe leaks nothing about whether a vehicle exists.
- **Default list = active fleet:** `GET /vehicles` returns ACTIVE vehicles only; archived ones are shown via `?status=ARCHIVED`. Archiving keeps history and removes the vehicle from the active list.
- **PATCH semantics:** only provided fields are updated; an empty license plate is rejected.
- **Validation:** a soft cap of 50 vehicles/owner returns 400; unknown status filter returns 400.

### Verification results

- Full suite: **47 tests green** (15 vehicle unit + 3 vehicle integration, no regressions to the 29 prior).
- Live `curl`: create → list → get → PATCH → archive → filtered list → delete; cross-owner get/delete → 404; missing plate → 400; bad status → 400; unauthenticated → 401; no phone field anywhere.

### Test-infrastructure fix (important)

Integration test classes now share a **single long-lived Testcontainers instance** (static holder in `AbstractIntegrationTest`) instead of per-class `@Container`. With Spring's cached `ApplicationContext`, the second integration class previously reused a context pointing at the first class's already-stopped database. Sharing containers keeps every cached context valid for the whole JVM.

## Phase 4 — Secure QR generation ⬜

## Phase 5 — Public QR page ⬜

## Phase 6 — WhatsApp + SMS ⬜

## Phase 7 — Conversations & owner dashboard ⬜

## Phase 8 — Admin + reports ⬜

## Phase 9 — Security hardening + tests ⬜

## Phase 10 — Production + CI/CD ⬜