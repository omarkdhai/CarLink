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
| Frontend   | React 19, TypeScript, Vite, React Router 7                |
| Styling    | Tailwind CSS (mobile-first)                               |
| Email (dev) | MailHog                                                  |
| Test       | JUnit 5, Mockito, Testcontainers, Vitest                  |
| CI         | GitHub Actions                                            |

## Repository layout

```
.
├── backend/               # Spring Boot modular monolith
│   ├── Dockerfile         # multi-stage production image
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
├── frontend/              # React 19 + Vite web application
│   ├── Dockerfile         # multi-stage build (Node.js -> Nginx)
│   └── nginx.conf         # Nginx SPA router + API proxy configuration
├── docker-compose.yml     # Full stack (Frontend + Backend + Postgres + Redis + MailHog)
├── docker-compose.prod.yml # Production stack (Frontend + Backend + Postgres + Redis)
├── .env.example           # env template (never commit real values)
└── .github/workflows/     # CI/CD
```

## Quick Start (Run Whole Project with Docker)

To start the entire application (Frontend, Backend API, PostgreSQL database, and Redis cache) with a single command:

1. **Copy environment template**

   ```bash
   cp .env.example .env
   ```

2. **Run the full stack**

   ```bash
   docker-compose up -d --build
   ```

   - **Frontend App**: `http://localhost`
   - **Backend API**: `http://localhost:8080`
   - **Swagger UI**: `http://localhost:8080/swagger-ui.html` or `http://localhost/swagger-ui/`
   - **Actuator Health**: `http://localhost:8080/actuator/health`

3. **Stop the full stack**

   ```bash
   docker-compose down
   ```

## Development (Run Locally without Docker for Backend/Frontend)

If you prefer to run services individually during development:

1. **Start infrastructure (Postgres + Redis)**

   ```bash
   docker-compose up -d postgres redis
   ```

2. **Run the backend**

   ```bash
   cd backend
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. **Run the frontend**

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## Security invariants

- The owner's phone number is **never** exposed via API, frontend, QR code,
  URLs, redirects or logs.
- QR codes encode only a random public token `/c/{token}`; only its SHA-256
  hash is stored.
- Rate limiting per IP and per QR token (Redis-backed).
- Passwords hashed with BCrypt (strength 12).
- JWT access tokens are short-lived; refresh tokens are stored hashed and revocable.

## Production deployment (Docker)

```bash
cp .env.example .env.prod            # then fill in real secrets
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

- The app container runs a **multi-stage build** (`backend/Dockerfile`).
- The frontend container runs a **multi-stage build** (`frontend/Dockerfile`) serving static assets via Nginx.
- All containers depend on healthchecks before starting.

## License

Proprietary. © 2026 CarLink.