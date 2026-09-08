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

## Phase 4 — Secure QR generation ✅

**Goal:** secure QR codes with a full token lifecycle. The QR encodes only the public page URL; the raw token is shown once and only its SHA-256 is stored; regenerating burns the previous QR (one ACTIVE per vehicle, DB-enforced).

### Files created

| Path | Purpose |
|------|---------|
| `qr/model/QrCode.java` | Entity → `qr_codes`; stores only `tokenHash`; `deactivate()` |
| `qr/repository/QrCodeRepository.java` | Active-by-vehicle lookups |
| `qr/dto/QrIssuedResponse.java` | Raw token + public URL + PNG data URI (returned **once**) |
| `qr/dto/QrStatusResponse.java` | Status/history payload — **no token ever** |
| `qr/service/QrImageGenerator.java` | ZXing PNG rendering as base64 data URI; UTF-8, M error correction |
| `qr/service/QrService.java` | Issue/regenerate/current/history/deactivate, ownership-scoped |
| `qr/controller/QrController.java` | `POST /vehicles/{id}/qr`, `GET .../qr`, `GET .../qr/history`, `POST .../qr/deactivate` |
| `db/migration/V2__add_updated_at_to_qr_codes.sql` | `qr_codes` gains `updated_at` to match `BaseEntity` (validate mode) |
| Tests | `QrServiceTest` (9), `QrFlowIntegrationTest` (3) |

### Design decisions

- **Token shown exactly once.** Generation returns the raw token, its public URL, and the image; every later read (`GET`, history) returns only record status — no token field exists.
- **Only `SHA-256(token)` at rest.** Verified live: DB rows hold 64-char hashes only.
- **One ACTIVE QR per vehicle**, guaranteed two ways: service deactivates the previous one on regenerate, and a partial unique index rejects any transient second-active row. A Hibernate flush is issued after the deactivation so the index never sees two active rows.
- **Ownership by 404** (same as vehicles): cross-owner QR access resolves to not-found.
- **QR payload is pure navigation** — `{publicUrl}/c/{token}`. Integration test decodes the PNG with ZXing and asserts the text equals the URL and contains no owner/vehicle info.
- **`V2` migration** added `updated_at` to `qr_codes` (V1 created it without the column while every other table maps `BaseEntity`).

### Verification results

- Full suite: **59 tests green** (9 QR unit + 3 QR integration, no regressions from 47).
- Live `curl` + `psql`: generate → status (no token leak) → regenerate (old burned, history=2) → deactivate (status 404) → cross-owner generate → 404; DB holds only hashes.

## Phase 5 — Public QR contact page ✅

**Goal:** the page a scanned QR opens. Mobile-first HTML at `/c/{token}`, a safe public JSON view, and an unauthenticated contact submission that persists a conversation + message — all rate-limited, never exposing a phone number, license plate, or the raw token itself.

### Files created

| Path | Purpose |
|------|---------|
| `common/exception/TooManyRequestsException.java` | 429 exception carrying `retryAfterSeconds` |
| `common/exception/GlobalExceptionHandler.java` | +429 handler with `Retry-After` header |
| `conversation/model/Channel.java`, `ConversationStatus.java`, `Conversation.java`, `Message.java` | Conversation + message entities (`createdAt` set manually, no `updated_at`) |
| `conversation/repository/ConversationRepository.java`, `MessageRepository.java` | Persistence |
| `conversation/service/ConversationService.java` | `open(...)` with expiry from `carlink.conversation.expiry-hours`, `appendMessage(...)` |
| `qr/dto/QrPublicView.java`, `VehicleSafe` | Safe public vehicle summary (nickname/brand/model/color) — **no phone, no plate** |
| `qr/dto/ContactSubmitRequest.java`, `ContactSubmitResponse.java` | Channel + message (≤500 chars); generic success |
| `qr/service/PublicQrService.java` | resolve + submit, both rate-limited **before** the SHA-256 token lookup; inner `RateLimits` record with hashed keys |
| `qr/controller/PublicContactController.java` | `GET /api/v1/public/qr/{token}`, `POST /api/v1/public/qr/{token}/contact` (unauthenticated) |
| `qr/controller/PublicPageController.java` | `GET /c/{token}` → mobile-first HTML; token derived from `location.pathname`, **never embedded** |
| `security/config/SecurityConfig.java` | +`/c/**`, `/api/v1/public/**` permitAll |
| Tests | `PublicQrServiceTest` (10), `PublicContactFlowIntegrationTest` (5) |

### Design decisions

- **The page opens end-to-end public, the data stays private.** `QrPublicView` simply has no phone/plate fields — nothing can leak them. The HTML renders a vehicle label assembled from nickname+brand/model/color and HTML-escapes it (a hostile nickname cannot inject markup).
- **Raw token never re-embedded.** The served HTML derives the token from `decodeURIComponent(location.pathname…)`; the substring of the URL someone already scanned is all it needs, so the server never echoes the token into the page body.
- **Hash-only lookup, hashed rate-limit keys.** `PublicQrService` resolves via `findByTokenHash(sha256(rawToken))`; IP and per-QR limiter keys are themselves SHA-256-hashed (`public-ip:<sha256(ip)>` / `public-qr:<sha256(token)>`) so Redis never holds a raw IP or raw token.
- **Rate limit first, existence check second.** Both resolve and submit call `requireRate(...)` before the QR lookup, so probing an unknown token is throttled just like a valid one (unknown token after a warm window → 404; during the window → 429 + `Retry-After`).
- **Generic success.** Submission replies `{conversationId, "Message sent to the owner."}` — no visitor-identifying echo at all.
- **Conversation expiry.** Each submission opens a conversation expiring `carlink.conversation.expiry-hours` later (default hours), ready for Phase 7's owner dashboard.

### Verification results

- Full suite: **73 tests green** (10 public unit + 5 public integration, no regressions from 59).
- Live `curl` + `psql` against the dev jar + compose stack: register → vehicle → QR `publicUrl` = `http://localhost:8080/c/{token}` → page 200 with form/WhatsApp/SMS/vehicle label and **0 occurrences** of the raw token, plate, or phone → JSON view returns only safe fields → contact POST persists `conversations` (WHATSAPP/SENT) + `messages` rows → unknown-token page 404 and API 429+`Retry-After` → `qr_codes` stores 64-char `token_hash` only.

### Test-infrastructure lesson

Integration test classes share one Postgres container, so email registration must be unique per class **and** per run: each class uses a distinct prefix (`pc`/`qr`/`owner`/`auth`) over a monotonic static counter. A per-test reset of that counter (as in an early draft of `PublicContactFlowIntegrationTest`) re-registers `pc1@example.com` in every test and collides with the DB the previous test populated.

## Phase 6 — WhatsApp + SMS delivery ✅

**Goal:** actually deliver a visitor's contact request to the vehicle owner over the chosen channel. Phase 5 only persisted a `conversation` + `message` and optimistically stamped `SENT`; Phase 6 introduces a channel-sender abstraction (mirroring the `EmailSender` pattern) so a submission is now relayed and the conversation records the real outcome (`SENT` / `FAILED`). Owner phone stays server-side — it is passed to the relay internally and never logged, serialized, or echoed.

### Files created / changed

| Path | Purpose |
|------|---------|
| `conversation/model/ConversationStatus.java` | +`PENDING` (awaiting relay) |
| `db/migration/V3__allow_pending_conversation_status.sql` | widen `conversations.status` CHECK to admit `PENDING` |
| `conversation/service/ConversationService.java` | +`updateStatus(id, status)` |
| `notification/contact/ContactDelivery.java` | internal record `(channel, ownerPhone, message)` — never a DTO |
| `notification/contact/ContactChannelSender.java` | `boolean send(ContactDelivery)` — the delivery seam |
| `notification/contact/LogContactChannelSender.java` | dev mock for both channels; logs `[CONTACT-DEV]` with the **SHA-256 of the phone**, never the raw number |
| `notification/contact/ContactDeliveryService.java` | orchestrates one send; records SENT/FAILED; swallows relay errors so a failed send doesn't roll back the persisted request |
| `qr/service/PublicQrService.java` | `submit` now opens with `PENDING`, then `contactDeliveryService.deliver(...)` (owner phone from the vehicle's owner) |
| Tests | `ContactDeliveryServiceTest` (3), `LogContactChannelSenderTest` (2); `PublicQrServiceTest` + `PublicContactFlowIntegrationTest` updated |

### Design decisions

- **Abstraction + mock, selectable by config.** The active sender is chosen by `carlink.contact.provider` (default `mock`) exactly like `carlink.email.provider` — a real WhatsApp/Twilio sender can be added behind `ContactChannelSender` without touching the flow (Phase 10).
- **Phone redaction is enforced by a test.** `LogContactChannelSenderTest` captures the sender's log and asserts the raw phone never appears (only its SHA-256). Whole-log live check also showed 0 occurrences of the raw phone.
- **`PENDING` → `SENT`/`FAILED`.** A conversation is created `PENDING` before the relay runs; `ContactDeliveryService` flips it to `SENT` (accepted) or `FAILED` (rejected/exception). `FAILED` is now set for real (it was enum-only before).
- **Delivery failures are swallowed, never propagate.** A relay exception records `FAILED` and returns — the persisted request survives, and the visitor still gets the generic success response (the owner sees the failure in the Phase 7 dashboard).

### Verification results

- Full suite: **78 tests green** (5 new, no regressions from 73).
- Live `curl` + `psql` + log: register → vehicle → QR → `POST .../contact` (SMS) → `conversations.status = SENT`; app log shows `[CONTACT-DEV] channel=SMS | owner=<sha256> | message=…`; `grep` of the raw phone across the whole log returned 0.

## Phase 7 — Conversations & owner dashboard ✅

**Goal:** give the vehicle owner a private dashboard of their conversations — list across all vehicles (or filtered per vehicle), drill into full message history, mark conversations as read, and an automated sweeper that flips stale conversations to `EXPIRED`.

### Files created / changed

| Path | Purpose |
|------|---------|
| `db/migration/V4__add_read_at_to_conversations.sql` | `conversations.read_at` column + `(vehicle_id, read_at)` index |
| `conversation/model/Conversation.java` | +`readAt` (`Null` = unread) |
| `conversation/repository/ConversationRepository.java` | owner- and vehicle-scoped list queries; `findByExpiresAtBeforeAndStatusIn` for the sweeper |
| `conversation/repository/MessageRepository.java` | `findAllByConversation_IdOrderByCreatedAtAsc` |
| `conversation/dto/ConversationSummaryResponse.java` | dashboard list item (unread flag, 80-char last-message preview) |
| `conversation/dto/ConversationMessageResponse.java` | one chat message (named to avoid colliding with `common.dto.MessageResponse`) |
| `conversation/dto/ConversationDetailResponse.java` | summary fields + full `messages[]` history |
| `conversation/service/ConversationService.java` | +`listForOwner`, `listForVehicle`, `getForOwner`, `markRead`, `sweepExpiredConversations` (`@Scheduled`) |
| `conversation/controller/ConversationController.java` | `GET /api/v1/conversations[?vehicleId=]`, `GET /{id}`, `POST /{id}/read` |
| `vehicle/service/VehicleService.java` | `getOwned(...)` made `public` so conversation ownership reuses the same 404-scoping primitive |
| `application.yml` | +`carlink.conversation.sweep-interval-ms` (default 1 h) |
| `scripts/verify-phase7.sh` | repeatable live-verification flow |
| Tests | `ConversationServiceTest` (12), `ConversationControllerTest` (5), `ConversationFlowIntegrationTest` (8) |

### Design decisions

- **Ownership by 404, same as vehicles.** The dashboard loads only conversations whose vehicle the caller owns; a cross-owner read or mark-read resolves to not-found, so existence is never revealed.
- **`read_at` = the read marker; `Null` means unread.** No separate unread counter column to drift — the list computes `unread` from `readAt == null`.
- **List is lightweight; detail is the only full-content view.** The dashboard list truncates the last message to 80 chars; the full visitor text appears only in the authenticated, ownership-scoped detail view. This is the one deliberate exception to "message content never returned by APIs" (CLAUDE.md updated to state it) — content stays off every public/unauthenticated surface.
- **Expiry sweeper is config-driven and idempotent.** `@Scheduled` (fixed delay from `sweep-interval-ms`) finds `PENDING`/`SENT` conversations past `expiresAt` and flips them to `EXPIRED`; running it on already-expired/final rows is a no-op.
- **Message DTO renamed.** `conversation/dto/MessageResponse` would have shadowed the pre-existing `common/dto/MessageResponse` used for action acknowledgments — renamed to `ConversationMessageResponse`.

### Verification results

- Full suite: **103 tests green** (25 new — 12 service + 5 controller + 8 integration, incl. the two Unit runs covering the sweeper against real Postgres; no regressions from 78).
- Live `curl` + `psql` against the dev jar + compose: two owners → vehicle + QR → two visitor contacts (WHATSAPP, SMS) → owner dashboard lists both newest-first, both unread, previews correct → detail returns history in order with **no licensePlate/phone leak** → mark-read flips `unread` to `false` (other conversation untouched) → cross-owner GET/mark-read `404`, unauthenticated `401`, other owner's dashboard empty → DB shows `read_at` populated and `qr_codes` holding hashes only; raw phone appears **0 times** in the app log.

### Test-hygiene fix along the way

`PublicContactFlowIntegrationTest` asserted **global** `messageRepository.findAll()` counts — correct only while it was the sole class writing messages to the shared Testcontainers DB. Scoped those assertions to the test's own conversation (and a before/after count) so the suite stays correct now that the conversation dashboard tests add more rows.

## Phase 8 — Admin + reports ⬜

## Phase 9 — Security hardening + tests ⬜

## Phase 10 — Production + CI/CD ⬜