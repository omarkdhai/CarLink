# CarLink Architecture

## Overview

CarLink is a **modular monolith** — a single deployable Spring Boot service
structured into cohesive module packages, kept deliberately separate so they
can later be extracted into services if needed.

## Why a modular monolith?

- Simpler operations (one JVM, one deployable).
- Transactional integrity across modules without distributed-consistency pain.
- Clear package boundaries preserve the discipline of service decomposition.

## Module map

```
com.carlink
├── auth          → registration, login, JWT issuance/refresh, password reset
├── user          → profile management
├── vehicle       → vehicle CRUD (owned resources)
├── qr            → secure token + QR image generation/lifecycle
├── contact       → ContactChannel abstraction (WHATSAPP | SMS)
├── conversation  → Conversation / Message persistence & expiry
├── notification  → provider adapters (mock, Twilio, WhatsApp Business API)
├── admin         → admin dashboard endpoints & stats
├── security      → JWT filter, rate limiting, anti-spam, CORS
└── common        → shared config, exceptions, DTOs, base entities
```

Each module follows the layering:

```
Controller → Service → Repository
              │
              ├── Mapper (entity ⇄ DTO)
              ├── Entity
              └── Exception
```

## Key design decisions

### QR tokens
- Public token = 32 random bytes → URL-safe base64 (~43 chars) / hex (64 chars).
- Only `SHA-256(token)` is stored. Raw tokens are returned once at generation.
- QR encodes `{baseUrl}/c/{token}` only.
- Regenerating deactivates the previous QR (partial unique index guarantees
  one active QR per vehicle).

### Contact channels
`ContactService` selects a `ContactChannel` by conversation type:

```
interface ContactChannel {
    Channel type();
    SendResult send(ContactRequest request);
}
```

Implementations:
- `MockContactChannel` — dev/test only, logs to a marker, never real numbers.
- `WhatsAppContactChannel` / `SmsContactChannel` — provider-backed (Twilio),
  selected only when `carlink.contact.provider` is configured.

Business logic depends only on the abstraction.

### Security posture
- Stateless JWT (`SessionCreationPolicy.STATELESS`).
- BCrypt(12) password hashing.
- Refresh tokens stored **hashed** with rotation + revocation.
- Redis rate limiting on the public contact endpoint (per-IP and per-QR-token).
- No owner phone ever leaves the server.

## Data model (core relationships)

- `users 1—N vehicles`
- `vehicles 1—N qr_codes` (at most one ACTIVE per vehicle)
- `vehicles 1—N conversations`
- `conversations 1—N messages`
- `users 1—N refresh_tokens / password_reset_tokens / email_verification_tokens`

Full DDL lives in `backend/src/main/resources/db/migration/V1__init.sql`
(migrated by Flyway; `ddl-auto: validate` ensures entities stay in sync).

## Deployment topology (production)

```
┌────────┐    HTTPS     ┌──────────────┐      ┌──────────┐
│ Phone/ │ ───────────► │ nginx/CDN    │ ───► │ backend  │ ──► PostgreSQL
│ owner  │              │ static+AOT  │      │ (Spring) │ ──► Redis
└────────┘              └──────────────┘      └──────────┘ ──► SMTP
```

Running pieces:
- The backend serves the API and the public `/c/{token}` page.
- The Angular frontend is served as static assets (or through a CDN).
- Postgres + Redis are dedicated containers; MailHog is dev-only.