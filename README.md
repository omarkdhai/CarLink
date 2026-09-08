# CarLink

**Anonymous QR-based communication with vehicle owners.**

CarLink lets vehicle owners generate a unique QR code sticker for their windshield.
Anyone who wants to reach the owner — without exposing the owner's phone number —
scans the code and sends a WhatsApp or SMS message through the platform.

## Product Overview

- A vehicle owner registers and adds vehicles.
- The platform generates a unique, unguessable QR token per vehicle.
- Scanners open a public, mobile-first page at `/c/{token}`.
- They pick a contact reason, choose **WhatsApp** or **SMS**, optionally write a message,
  and send — all without ever seeing the owner's phone number.

## Tech Stack

| Layer      | Technology                                                |
|------------|-----------------------------------------------------------|
| Backend    | Java 17, Spring Boot 3.2, Spring Security, Spring Data JPA |
| Database   | PostgreSQL 16 + Flyway migrations                          |
| Cache/Rate-limit | Redis 7                                             |
| QR         | ZXing                                                     |
| Auth       | JWT (access + refresh tokens)                             |
| Frontend   | Angular 16, TypeScript, RxJS                              |
| Styling    | Tailwind CSS (mobile-first)                               |
| Email (dev) | MailHog                                                  |
| Test       | JUnit 5, Mockito, Testcontainers                          |
| CI         | GitHub Actions                                            |

## Repository layout

```
.
├── backend/               # Spring Boot modular monolith
│   └── src/main/java/com/carlink/
│       ├── auth/          # registration, login, JWT, password reset
│       ├── user/          # profile management
│       ├── vehicle/       # vehicle CRUD
│       ├── qr/            # secure QR generation
│       ├── contact/       # contact channel abstraction (WhatsApp/SMS)
│       ├── conversation/  # conversations & messages
│       ├── notification/  # provider integrations
│       ├── admin/         # admin dashboard
│       ├── security/      # JWT filters, rate limiting, anti-spam
│       └── common/        # shared DTOs, exceptions, config
├── frontend/              # Angular 16 app
├── docker-compose.yml     # PostgreSQL + Redis + MailHog
├── .env.example           # env template (never commit real values)
└── .github/workflows/     # CI/CD
```

## Getting started (development)

1. **Copy environment template**

   ```bash
   cp .env.example .env
   ```

2. **Start infrastructure**

   ```bash
   docker compose up -d
   ```

3. **Run the backend**

   ```bash
   cd backend
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   The API is available at `http://localhost:8080`.
   Swagger UI: `http://localhost:8080/swagger-ui.html`
   MailHog UI: `http://localhost:8025`

4. **Run tests**

   ```bash
   cd backend
   mvn test
   ```

   Integration tests use Testcontainers and require **Docker Desktop** running.

## Security invariants

- The owner's phone number is **never** exposed via API, frontend, QR code,
  URLs, redirects or logs.
- QR codes encode only a random public token `/c/{token}`; only its SHA-256
  hash is stored.
- Rate limiting per IP and per QR token (Redis-backed).
- Passwords hashed with BCrypt (strength 12).
- JWT access tokens are short-lived; refresh tokens are stored hashed and revocable.

## Implementation status

- [x] **Phase 1 — Foundation** (project scaffolding, Docker, Postgres/Redis, Flyway)
- [ ] Phase 2 — Authentication + JWT
- [ ] Phase 3 — Vehicle management
- [ ] Phase 4 — Secure QR generation
- [ ] Phase 5 — Public QR contact page
- [ ] Phase 6 — WhatsApp + SMS channels
- [ ] Phase 7 — Conversations & owner dashboard
- [ ] Phase 8 — Admin + reports
- [ ] Phase 9 — Security hardening + tests
- [ ] Phase 10 — Production build + CI/CD + docs

## License

Proprietary. © 2026 CarLink.