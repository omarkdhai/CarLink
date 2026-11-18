# CarLink — Security Analysis & Remediation Plan

> **Status:** open · **Audit date:** 2026-11-18 · **Scope:** backend (Spring Boot), frontend (React SPA),
> nginx, Docker Compose (dev + prod), CI. The admin console is in scope for *access control* only.
>
> This file is the working plan for the security phase. Work through the phases in order; tick each task
> when its **Done when** criteria pass. Every task ID (`S-xx`) is referenced from commits/PRs
> (`fix(security): S-03 …`). The threat model and invariants stay in [`security.md`](security.md).

---

## 1. How the audit was done

- Read the security-relevant code: `SecurityConfig`, `JwtAuthenticationFilter`, `JwtService`, `AuthService`,
  `LoginAttemptService`, `RedisRateLimiter`, `TokenGenerator`, every public controller (`/api/v1/public/**`,
  `/api/v1/auth/**`, `/c/**`), the owner services' ownership checks, the notification senders, all
  `application*.yml` profiles, both compose files, `nginx.conf`, both Dockerfiles, `ci.yml`, and the SPA's
  token handling.
- Ran `npm audit` (frontend) and a secret scan over tracked files.
- **Verified live** against the local Docker stack (marked ✅ below): metrics exposure, SPA response headers,
  missing auth rate limits, ports listening on all interfaces.

## 2. What is already done well (keep it)

| Control | Where |
|---|---|
| BCrypt (cost 12) password hashing | `SecurityConfig#passwordEncoder` |
| QR, sticker, refresh, reset and verification tokens stored as SHA-256 hashes only | `TokenGenerator`, `AuthService` |
| Refresh-token rotation + reuse detection (revokes the whole family) | `AuthService#refresh` |
| All sessions revoked after a password reset | `AuthService#resetPassword` |
| Ownership checks answer **404**, not 403 (no existence oracle) | `VehicleService#getOwned`, `ConversationService`, `StickerService` |
| Owner phone never serialized (`/me` test asserts it) | `AuthFlowIntegrationTest` |
| Stack traces / messages never returned in errors | `server.error.*: never` |
| Prod: Swagger off, backend bound to `127.0.0.1`, required secrets enforced with `:?` | `application-prod.yml`, `docker-compose.prod.yml` |
| `X-Forwarded-For` overwritten by nginx, `ForwardedHeaderFilter` only in prod | `nginx.conf`, `AppConfig` |
| Rate limits on scan, contact relay, report, order, contact form, sticker resolve | public controllers / `PublicQrService` |
| Generic password-reset answer; order size capped at 100 stickers; DTO validation | `AuthController`, `OrderService` |
| Access token kept in memory only on the SPA | `tokenStore.ts` |
| Non-root backend container; prod log rotation; Flyway `validate` | `backend/Dockerfile`, compose |

## 3. Findings

Severity: **Critical** = exploitable now with high impact · **High** = must be fixed before public launch ·
**Medium** = real weakness, fix in hardening · **Low/Info** = best practice / hygiene.

| ID | Sev. | Area | Finding |
|---|---|---|---|
| S-01 | **Critical** (local) / High | Infra | Default stack runs the **dev profile with public secrets and open ports** ✅ |
| S-02 | **High** | Infra | **No TLS** anywhere in the prod stack; HSTS off |
| S-03 | **High** | Auth | **No rate limiting on `/api/v1/auth/**`** nor on sticker activation ✅ |
| S-04 | **High** | Auth | Login: per-email lockout only → **targeted lockout DoS**, credential stuffing, timing-based user enumeration |
| S-05 | **High** | Backend | **Actuator metrics/prometheus (and dev OpenAPI) readable by any logged-in user** ✅ |
| S-06 | **High** | Frontend/nginx | **SPA served with no security headers** (no CSP, framing, nosniff); nginx version leaked ✅ |
| S-07 | **High** | Supply chain | **EOL / vulnerable dependencies**: Spring Boot 3.2.5, Node 20 image, 3 high npm vulns in prod deps |
| S-08 | Medium | Privacy | **Raw QR/sticker tokens in API URL paths** → written to nginx access logs |
| S-09 | Medium | Frontend | Refresh token (7 days) stored in `sessionStorage` (XSS-readable) |
| S-10 | Medium | Auth | Role/active state trusted from the JWT for its whole lifetime |
| S-11 | Medium | Abuse | Public COD orders: no bot protection; server emails arbitrary addresses |
| S-12 | Medium | Abuse | SMS relay: harassment/cost abuse (20 SMS/h per sticker), free text relayed |
| S-13 | Medium | Auth | Registration leaks account existence + sticker-code validity; does not check the sticker is claimable |
| S-14 | Medium | Auth | Weak password policy; `LoginRequest` has no size bound |
| S-15 | Medium | Admin | No MFA for administrators; bootstrap password in config |
| S-16 | Medium | Privacy | **Mock senders log message content / reset links — and prod defaults to the mock relay** |
| S-17 | Medium | Privacy | No retention/purge; phone stored in plaintext; no account deletion |
| S-18 | Medium | Config | Public JWT default secret in the *base* profile; unused `JWT_REFRESH_SECRET` |
| S-19 | Medium | CI | No security gates (dependency, SAST, secrets, container scans); frontend not in CI |
| S-20 | Low | Infra | Unpinned images, nginx master as root, no container hardening flags |
| S-21 | Low | Auth | Older reset tokens stay valid after a new request |
| S-22 | Low | Hygiene | Personal Gmail address hard-coded as default sender; `server` header |
| S-23 | Info | Code | `generateUrlSafe` alphabet/masking mismatch (entropy still 192 bits) |
| S-24 | Info | Design | CSRF disabled — correct today, must change with S-09 |
| S-25 | Low | Ops | No security alerting (failed logins, rate-limit hits, admin actions) |

### Details

#### S-01 — Default stack: dev profile, public secrets, open ports  ✅ verified
- `docker-compose.yml` defaults `SPRING_PROFILES_ACTIVE` to **dev**, so the stack started every morning has:
  - the JWT signing secret committed in `application.yml` / `docker-compose.yml` → **anyone can forge an
    `ADMIN` access token** for this instance;
  - a bootstrap **admin account whose password is committed** in `application-dev.yml`;
  - Postgres `5432`, Redis `6379` and the backend `8080` **listening on all interfaces (`::`)** with the
    committed default passwords (verified with `Get-NetTCPConnection`); 8080 bypasses nginx entirely;
  - Swagger/OpenAPI on, `com.carlink: DEBUG` and Hibernate SQL logging.
- **Risk:** anyone on the same network (Wi-Fi, office LAN) can read/modify the database or log in as admin.
- **Fix:** bind every published port to `127.0.0.1`; move dev secrets to an untracked `.env` (generated);
  remove the bootstrap password from the repo; add a startup guard that refuses known default secrets.

#### S-02 — No TLS
- `nginx.conf` only `listen 80`; `docker-compose.prod.yml` publishes port 80; `HSTS_ENABLED=false`.
- **Risk:** passwords, JWTs, refresh tokens and owner messages travel in clear text.
- **Fix:** TLS termination (Caddy/Traefik with ACME, or nginx + certbot), HTTP→HTTPS redirect, HSTS
  (start without `includeSubDomains`), `Secure` cookies once S-09 lands.

#### S-03 — No rate limiting on authentication endpoints  ✅ verified
- `register`, `password-reset`, `password-reset/confirm`, `verify-email`, `refresh` and the authenticated
  `POST /api/v1/stickers/activate-by-code` have no limiter (8 rapid bad-code registrations → all processed).
- **Risk:** sticker activation codes are 8 chars from a 31-char alphabet (~39.6 bits). `register` answers
  `INVALID_STICKER_CODE` vs other errors, so it is an **unthrottled oracle** to find valid unbound codes and
  hijack a sticker before its buyer activates it. `password-reset` can be used to **email-bomb** any
  address. Account-creation spam.
- **Fix:** reuse `RateLimiter`: per-IP *and* per-target (email hash / code hash) limits on each endpoint.

#### S-04 — Login brute-force protection is per e-mail only
- `LoginAttemptService` keys only on the e-mail: 5 failures lock the account for 15 min.
- **Risks:** (a) anyone can **lock any owner out** indefinitely; (b) no per-IP limit, so one IP can try
  one password across thousands of accounts (credential stuffing); (c) for unknown e-mails no BCrypt
  comparison runs, so response time reveals which e-mails exist.
- **Fix:** per-IP sliding limit + per-(IP,email) counter; replace hard lock with progressive delay /
  CAPTCHA after N failures; run a dummy BCrypt compare when the user does not exist.

#### S-05 — Actuator metrics readable by any user  ✅ verified
- `/actuator/metrics` and `/actuator/prometheus` fall through to `anyRequest().authenticated()`; nginx
  proxies `/actuator/`. A normal owner token got **200** on both (and on `/v3/api-docs` in dev). Becoming an
  owner only requires a sticker code, which anyone can order publicly.
- **Fix:** `hasRole("ADMIN")` or a separate management port not published; nginx only proxies
  `/actuator/health`.

#### S-06 — SPA without security headers  ✅ verified
- `GET /` returns no `Content-Security-Policy`, `X-Frame-Options`/`frame-ancestors`,
  `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`; `Server: nginx/1.31.8`.
  The CSP in `SecurityConfig` applies only to API JSON responses, and it allows `'unsafe-inline'` scripts.
- **Risk:** clickjacking of the owner app; no XSS mitigation on the page that holds the session.
- **Fix:** add headers in `nginx.conf` (`add_header … always`), strict CSP without inline scripts
  (`script-src 'self'`; fonts from Google), `frame-ancestors 'none'`, `server_tokens off`.

#### S-07 — End-of-life and vulnerable dependencies
- `spring-boot-starter-parent` **3.2.5** — the 3.2 line is out of OSS support (missing Spring Security /
  Tomcat / Jackson fixes). `node:20-alpine` build image — Node 20 is EOL.
- `npm audit --omit=dev`: **3 high** (`follow-redirects` via axios, `zod`, `source-map-js`); 10 in total.
- **Fix:** upgrade to a supported Spring Boot line (re-run the 235 tests), `node:22`/`24`, `npm audit fix`,
  then keep current with Dependabot (S-19).

#### S-08 — Tokens in URL paths
- `GET /api/v1/public/qr/{token}`, `POST /api/v1/public/qr/{token}/contact`,
  `POST /api/v1/stickers/{token}/activate` carry the raw token in the path; nginx's default `access_log`
  records it. Contradicts the invariant "QR tokens never logged / never in URLs except `/c/{token}`".
- **Fix:** send the token in the request body (or an `X-QR-Token` header) and/or a masking `log_format`.

#### S-09 — Refresh token in `sessionStorage`
- Documented trade-off in `tokenStore.ts`; any XSS can exfiltrate a 7-day refresh token.
- **Fix:** backend sets the refresh token as `HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`
  cookie; SPA stops storing it; add CSRF protection to the cookie-authenticated endpoints (see S-24).

#### S-10 — JWT claims trusted for the token lifetime
- Role and identity come from the token; a deactivated user or a demoted admin keeps access up to 15 min.
- **Fix:** shorten admin access tokens (5 min) and check `active`/`role` (cached) for `/api/v1/admin/**`;
  optionally a `tokenVersion` claim bumped on deactivation/password change.

#### S-11 — Public order endpoint abuse
- Anyone can place COD orders (5/min/IP, up to 100 stickers each): mints stickers + QR images and sends a
  confirmation e-mail **to any address** → fake orders, spam relay. `ratelimit.capture-hosts`
  (reCAPTCHA/hCaptcha) exists in config but is **not implemented**.
- **Fix:** CAPTCHA (or Turnstile) on order/contact/report forms; per-email/phone daily cap; admin
  confirmation before printing.

#### S-12 — SMS relay abuse
- Per sticker: 20 contacts/hour; per IP: 5/min. A visitor can send ~480 SMS/day to one owner (harassment,
  Telnyx cost). Free text (≤500 chars) is relayed, so links/phishing reach the owner by SMS.
- **Fix:** per-sticker daily cap + per-(IP,sticker) cooldown; per-owner daily cap; strip/neutralise URLs in
  relayed text; spending alert on the Telnyx account; owner "mute this sticker" switch.

#### S-13 — Registration oracles
- `EMAIL_ALREADY_REGISTERED` and `INVALID_STICKER_CODE` reveal existence. The code comment says only
  *claimable* stickers are accepted, but only existence is checked (`findByActivationCodeHash`).
- **Fix:** check status `UNBOUND/DEACTIVATED`; keep explicit messages only behind S-03 limits.

#### S-14 — Password policy
- 8+ chars with a letter and a digit; no breached-password check; `LoginRequest.password` unbounded
  (BCrypt silently truncates at 72 bytes).
- **Fix:** min 10–12 chars, allow passphrases, optional HIBP k-anonymity check, `@Size(max=72)` on login.

#### S-15 — Admin hardening
- Admins log in like owners, no second factor; bootstrap credentials come from config.
- **Fix:** TOTP MFA required for `ADMIN`; bootstrap password only from env and forced change on first
  login; shorter admin sessions; alert on admin login.

#### S-16 — Sensitive data logged by mock senders (and prod default)
- `LogContactChannelSender` logs the **visitor's message**; `LogEmailSender` logs full e-mail bodies
  including **verification/reset links**. `docker-compose.prod.yml` defaults `CONTACT_PROVIDER=mock`, so
  **production logs message content** until Telnyx is configured.
- **Fix:** mock senders log metadata only; prod profile fails fast if a mock provider is selected (or
  relays nothing and says so).

#### S-17 — Retention, encryption at rest, deletion
- Expired conversations keep their messages forever; used/expired tokens never purged; audit logs keep raw
  IPs; `users.phone` in plaintext; no self-service account deletion (`profile.deleteNotAvailable`).
- **Fix:** scheduled purge (messages after N days, tokens after expiry, IPs anonymised after 90 days);
  AES-GCM column encryption for phone (key from env/KMS); account deletion flow (privacy law: Tunisian
  Organic Law 2004-63, GDPR for EU visitors).

#### S-18 — Secret defaults in the base profile
- `application.yml` ships a known JWT secret as default for *every* profile except prod. `JWT_REFRESH_SECRET`
  is required by prod compose but never used (refresh tokens are opaque).
- **Fix:** no default in the base file; startup check: secret ≥ 32 random bytes and not a known default
  outside `dev`; drop the unused property.

#### S-19 — CI has no security gates
- `ci.yml` builds/tests the backend only. No Dependabot, OWASP dependency-check, CodeQL/Semgrep, gitleaks,
  Trivy, frontend `npm ci && build && test && audit`; no `permissions:` block; actions pinned by tag.
- **Fix:** add those jobs; `permissions: contents: read`; pin actions by SHA.

#### S-20 … S-25 — Low / Info
- **S-20** Pin `nginx`, `postgres`, `redis`, `node`, `temurin` images by digest; `nginxinc/nginx-unprivileged`;
  `read_only`, `cap_drop: [ALL]`, `security_opt: [no-new-privileges:true]` where possible.
- **S-21** Requesting a new reset link should invalidate previous unused reset tokens.
- **S-22** Remove the personal Gmail default for `MAIL_FROM`/`MAIL_REPLY_TO`; `server_tokens off`.
- **S-23** `TokenGenerator.generateUrlSafe` masks to 64 values over a 66-char alphabet — use Base64URL.
- **S-24** CSRF is disabled; correct for bearer tokens, **required** for cookie endpoints after S-09.
- **S-25** Alerts on: login lockouts per IP, rate-limit spikes, admin logins/actions, 5xx bursts.

---

## 4. Remediation plan

Each phase ends with: all tests green (`mvn verify`, `npm test`, `npm run build`), commit + push per task,
`graphify update .`, and this file updated (tick + date).

### Phase 0 — Immediate (local safety + quick wins, ~1 day)

- [x] **S-01** Lock down the local stack — done 2026-11-18 (ports on 127.0.0.1, secrets rotated, committed defaults removed)
  - Publish `5432`, `6379`, `8080`, `80` on `127.0.0.1` only in `docker-compose.yml`.
  - Move dev secrets (JWT, DB, Redis, admin bootstrap) to an untracked `.env`; commit `.env.example`
    with placeholders and a one-line generator command.
  - Remove the bootstrap admin password from `application-dev.yml`.
  - **Done when:** `Get-NetTCPConnection` shows only `127.0.0.1/::1`; no secret value remains in tracked
    files (`git grep`); stack still starts with `docker compose up -d`.
- [x] **S-05** Restrict actuator — done 2026-11-18 (owner 403, admin 200, nginx serves health only)
  - `/actuator/**` except health → `hasRole("ADMIN")`; nginx proxies only `/actuator/health`.
  - **Done when:** an owner token gets 403/404 on `/actuator/prometheus` (integration test).
- [x] **S-16** Stop logging content — done 2026-11-18 (metadata-only mock senders; prod refuses mock email, warns on mock relay)
  - Mock senders log channel/reason/hash only; prod refuses `CONTACT_PROVIDER=mock` / `EMAIL_PROVIDER=mock`
    (fail fast with a clear message) or the prod compose default changes.
  - **Done when:** test asserts mock senders never log the message body or a URL containing `token=`.
- [ ] **S-07 (part)** `npm audit fix`; bump the frontend build image to `node:22-alpine`.
  - **Done when:** `npm audit --omit=dev` reports 0 high.

### Phase 1 — Before any public launch (High)

- [ ] **S-02** TLS termination + HTTP→HTTPS redirect + HSTS (prod compose / deployment runbook).
  - **Done when:** `curl -I http://…` → 301 to https; `Strict-Transport-Security` present; SSL Labs ≥ A.
- [ ] **S-03** Rate limits on `register`, `password-reset`, `password-reset/confirm`, `verify-email`,
  `refresh`, `activate-by-code`, `/{token}/activate` (per IP + per target).
  - **Done when:** integration tests get 429 after the threshold for each endpoint.
- [ ] **S-04** Login: per-IP limiter, progressive delay instead of hard lock, dummy BCrypt for unknown users.
  - **Done when:** tests: other IPs can still log in while one IP is throttled; unknown-email and
    wrong-password responses take comparable time (± tolerance).
- [ ] **S-06** Security headers on the SPA (`nginx.conf`) + strict CSP; `server_tokens off`.
  - **Done when:** `curl -I http://localhost/` shows CSP, `X-Frame-Options: DENY`/`frame-ancestors 'none'`,
    `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`; app works with no CSP
    violations in the console (home, order, scan, owner pages, AR/FR/EN).
- [ ] **S-07** Upgrade Spring Boot to a supported line; update jjwt/springdoc accordingly.
  - **Done when:** 235+ backend tests pass; OWASP dependency-check has no High/Critical.
- [ ] **S-08** Move tokens out of URL paths (body/header) for public QR + sticker activation; masking
  `log_format` in nginx as defence in depth.
  - **Done when:** nginx access log contains no 64-char token after a scan + contact (test script).
- [ ] **S-11** CAPTCHA/Turnstile on order, contact form, report and scan-contact; per-email/phone order cap.
- [ ] **S-12** Per-sticker daily cap, per-(IP,sticker) cooldown, URL neutralisation, Telnyx spend alert.
- [ ] **S-18** Remove secret defaults from the base profile; startup secret-strength guard; drop
  `JWT_REFRESH_SECRET`.

### Phase 2 — Hardening (Medium)

- [ ] **S-09** Refresh token → `HttpOnly; Secure; SameSite=Strict` cookie + CSRF on cookie endpoints (S-24).
- [ ] **S-10** Admin routes re-check `active`/`role`; admin access TTL 5 min; `tokenVersion` claim.
- [ ] **S-13** Registration checks sticker is claimable; responses reviewed with S-03 in place.
- [ ] **S-14** Password policy (min length, passphrases, optional HIBP); `@Size(max=72)` on login.
- [ ] **S-15** TOTP MFA for admins; forced password change for the bootstrap admin.
- [ ] **S-17** Purge jobs, IP anonymisation, phone column encryption, account deletion endpoint + UI.
- [ ] **S-19** CI: Dependabot (maven, npm, docker, actions), dependency-check, CodeQL/Semgrep, gitleaks,
  Trivy on both images, frontend build/test/audit job, `permissions: contents: read`, SHA-pinned actions.

### Phase 3 — Ongoing / hygiene (Low)

- [ ] **S-20** Container hardening and digest-pinned images.
- [ ] **S-21** Invalidate older reset tokens on a new request.
- [ ] **S-22** Neutral default sender; remove personal address from config.
- [ ] **S-23** Base64URL token encoding.
- [ ] **S-25** Security alerting (logs → alerts) and a short incident-response runbook in `deployment.md`.
- [ ] External penetration test before scaling; repeat this audit after Phase 2.

---

## 5. Changelog of this plan

| Date | Change |
|---|---|
| 2026-11-18 | Initial audit: 25 findings (1 critical-local, 6 high, 12 medium, 6 low/info), 4-phase plan. |
