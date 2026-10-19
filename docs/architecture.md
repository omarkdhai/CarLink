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
├── contact       → public "contact us" form (delivered to the business mailbox)
├── conversation  → Conversation / Message persistence & expiry
├── order         → guest sticker orders (QR purchase flow)
├── sticker       → sticker issuance + owner activation
├── notification  → delivery abstractions (email, contact channel) + adapters
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
- Public token = 32 bytes from a `SecureRandom`, rendered as 32 characters from
  a 64-symbol URL-safe alphabet (`TokenGenerator.generateUrlSafe`). The nominal
  66-symbol alphabet is masked down to 64 so the modulo is bias-free.
- Only `SHA-256(token)` is stored (hex digest). Raw tokens are returned once at
  generation.
- QR encodes `{baseUrl}/c/{token}` only.
- Regenerating deactivates the previous QR (partial unique index guarantees
  one active QR per vehicle).
- Sticker tokens follow the same scheme and the same one-time rule (see
  `docs/security.md`).

### Delivery channels
Two parallel abstractions, both in `notification`, so the submission flows never
depend on a transport:

```
notification/email/    EmailSender         → LogEmailSender | SmtpEmailSender
notification/contact/  ContactChannelSender → LogContactChannelSender | TelnyxContactChannelSender
notification/contact/  AnonymousCallSender  → LogAnonymousCallSender  | TelnyxAnonymousCallSender
notification/telnyx/   TelnyxClient bean (built only when carlink.contact.provider=telnyx)
```

`carlink.contact.provider` selects the implementation (`mock` default, `telnyx` live).

**Two providers exist.** The default `mock` provider logs and never reaches a
phone. With `carlink.contact.provider=telnyx`, `TelnyxContactChannelSender`
sends the visitor's message by **SMS** through the Telnyx Messages API, and
`TelnyxAnonymousCallSender` places the alert call on a Call Control connection,
playing a static clip (`TELNYX_VOICE_AUDIO_URL`). WhatsApp is intentionally not
implemented: Telnyx only allows free-text WhatsApp inside a 24-hour window the
owner must open first, which an unprompted QR scan cannot. Both providers receive
the owner phone but never log, persist, or echo it except as a SHA-256.

The advertised and accepted channels come from `carlink.contact.enabled-channels`
(default `SMS`); a channel that is not enabled is rejected with 400 by the submit
endpoint, so the page cannot offer what the API refuses.

### Security posture
- Stateless JWT (`SessionCreationPolicy.STATELESS`).
- BCrypt(12) password hashing.
- Refresh tokens stored **hashed** with rotation + revocation.
- Redis rate limiting on the public contact endpoint (per-IP and per-QR-token).
- The owner's phone is never disclosed to a visitor, a third party, or a log. It
  is used only to route the visitor's message to the owner, and is echoed back to
  the authenticated owner in their own profile so they can verify the relay
  number. Admin DTOs omit it. See `docs/security.md`.

## Data model (core relationships)

- `users 1—N vehicles`
- `vehicles 1—N qr_codes` (at most one ACTIVE per vehicle)
- `vehicles 1—N conversations`
- `conversations 1—N messages`
- `users 1—N refresh_tokens / password_reset_tokens / email_verification_tokens`

Schema is Flyway-only (`ddl-auto: validate`). `V1__init.sql` holds the base DDL
and `V2`–`V6` evolve it (timestamps, conversation status, read markers, the
orders/stickers tables, and the message `reason` column). Validation at startup
is what keeps entities in sync with the migrations.

## Deployment topology (production)

```
┌────────┐    HTTPS     ┌──────────────┐      ┌──────────┐
│ Phone/ │ ───────────► │ nginx/CDN    │ ───► │ backend  │ ──► PostgreSQL
│ owner  │              │ static+build │      │ (Spring) │ ──► Redis
└────────┘              └──────────────┘      └──────────┘ ──► SMTP
```

Running pieces:
- The backend serves the API and the public `/c/{token}` page.
- The frontend is a **React 19 + Vite** SPA served as static assets.
- Postgres + Redis are dedicated containers; MailHog is dev-only.
- Only the frontend's port is published in `docker-compose.prod.yml`. The
  backend binds to `127.0.0.1` because nginx reaches it over the internal
  network by service name.

Operational procedure (secrets, TLS, migrations, backup, rollback) lives in
`docs/deployment.md`; the security posture lives in `docs/security.md`.