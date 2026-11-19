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
- [x] **S-07 (part)** `npm audit fix`; bump the frontend build image to `node:22-alpine`. — done 2026-11-18 (prod deps: 0 vulnerabilities).
  - Residual, dev-only: `picomatch` ReDoS (via vite/vitest/tinyglobby) has no patched release; npm's
    "fix" would *downgrade* vite 8→6 and vitest 5→3, so it is not applied. Build-time only, never shipped
    to browsers. Re-check with `npm audit` when picomatch publishes a fix (tracked under S-19 Dependabot).
  - **Done when:** `npm audit --omit=dev` reports 0 high.

### Phase 1 — Before any public launch (High)

- [x] **S-02** — done 2026-11-18 (Caddy edge in prod compose: auto certs, 308 redirect, HSTS; real client IP via set_real_ip_from; verified locally with Caddy internal CA). Needs CARLINK_DOMAIN + ACME_EMAIL + DNS at deploy time. TLS termination + HTTP→HTTPS redirect + HSTS (prod compose / deployment runbook).
  - **Done when:** `curl -I http://…` → 301 to https; `Strict-Transport-Security` present; SSL Labs ≥ A.
- [x] **S-03** — done 2026-11-18 (carlink.throttle.*; AuthThrottleIntegrationTest). Rate limits on `register`, `password-reset`, `password-reset/confirm`, `verify-email`,
  `refresh`, `activate-by-code`, `/{token}/activate` (per IP + per target).
  - **Done when:** integration tests get 429 after the threshold for each endpoint.
- [x] **S-04** — done 2026-11-18 (per-IP cap, (IP,e-mail) lockout, dummy BCrypt; distributed per-account guessing is left to S-11 CAPTCHA). Login: per-IP limiter, progressive delay instead of hard lock, dummy BCrypt for unknown users.
  - **Done when:** tests: other IPs can still log in while one IP is throttled; unknown-email and
    wrong-password responses take comparable time (± tolerance).
- [x] **S-06** — done 2026-11-18 (strict CSP, no eval/inline scripts; crawl of 29 page loads FR/AR + owner app: 0 violations). Security headers on the SPA (`nginx.conf`) + strict CSP; `server_tokens off`.
  - **Done when:** `curl -I http://localhost/` shows CSP, `X-Frame-Options: DENY`/`frame-ancestors 'none'`,
    `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`; app works with no CSP
    violations in the console (home, order, scan, owner pages, AR/FR/EN).
- [~] **S-07** Upgrade Spring Boot to a supported line; update jjwt/springdoc accordingly.
  - **Step 1 done 2026-11-18:** 3.2.5 → **3.5.16** (springdoc 2.8.17, jjwt 0.12.7, Flyway 11 +
    `flyway-database-postgresql`); 285 tests pass; live stack + browser crawl clean.
  - **Step 2 open:** 3.5 OSS support ended June 2026 (3.5.16 is its last free release), so the
    supported target is **4.1.x** — a major migration (Jackson 3, Spring Security 7, Hibernate 7,
    Testcontainers 2, springdoc 3, `@MockBean` → `@MockitoBean`). Tracked as its own task.
  - Not run: OWASP dependency-check (needs the NVD feed / an API key) — folded into S-19 CI.
  - **Done when:** 235+ backend tests pass; OWASP dependency-check has no High/Critical.
- [x] **S-08** — done 2026-11-18 (X-CarLink-Token header; nginx masks /c/{token} in URI + Referer; e2e scan→activate→contact left 0 tokens in the log). Move tokens out of URL paths (body/header) for public QR + sticker activation; masking
  `log_format` in nginx as defence in depth.
  - **Done when:** nginx access log contains no 64-char token after a scan + contact (test script).
- [x] **S-11** — done 2026-11-18. Cloudflare Turnstile on order, contact form, report and scan-contact
  (`X-Captcha-Token`, fails closed, site key served by `GET /api/v1/public/config`). **Off until
  `CAPTCHA_ENABLED/SITE_KEY/SECRET_KEY` are set** (prod logs a warning while off). Verified live with
  Cloudflare test keys: no token → 400 `CAPTCHA_FAILED`, widget renders under the CSP and unlocks submit.
  - Follow-ups (not done): per-email/phone daily order cap; challenge on login after repeated
    failures for one account across IPs (the distributed case left open by S-04).
  CAPTCHA/Turnstile on order, contact form, report and scan-contact; per-email/phone order cap.
- [x] **S-12** — done 2026-11-18 (visitor cooldown, sticker/day, owner/day caps; links neutralised; Telnyx spend alert documented in deployment.md §14, to set in the portal). Per-sticker daily cap, per-(IP,sticker) cooldown, URL neutralisation, Telnyx spend alert.
- [x] **S-18** — done 2026-11-18 (JwtService refuses weak/public secrets; JWT_REFRESH_SECRET removed). Remove secret defaults from the base profile; startup secret-strength guard; drop
  `JWT_REFRESH_SECRET`.

### Phase 2 — Hardening (Medium)

- [x] **S-09** — done 2026-11-18 (carlink_rt HttpOnly/SameSite=Strict cookie on /api/v1/auth, X-Requested-With required on refresh/logout, Web Locks serialise refresh across tabs; also fixed: refresh-token reuse detection was rolled back by the 401 and never took effect). Refresh token → `HttpOnly; Secure; SameSite=Strict` cookie + CSRF on cookie endpoints (S-24).
- [x] **S-10** — done 2026-11-19 (tokenVersion "tv" claim + V9 column; JWT filter checks active/role/version against live state — DB on /api/v1/admin/**, 30 s cache elsewhere, evicted on commit; deactivation, role change and password reset end all sessions; admin access TTL 5 min). Admin routes re-check `active`/`role`; admin access TTL 5 min; `tokenVersion` claim.
- [x] **S-13** — done 2026-11-19 (BOUND stickers refused with STICKER_ALREADY_BOUND; sticker checked before the e-mail, so EMAIL_ALREADY_REGISTERED needs a real unclaimed code; both behind the S-03 register throttle). Registration checks sticker is claimable; responses reviewed with S-03 in place.
- [x] **S-14** — done 2026-11-19 (PasswordPolicy: 10 chars min / 72 bytes max, no composition rule, common/pattern/e-mail-name refusals, HIBP k-anonymity in prod failing open; login capped, >72-byte login = 401 instead of a BCrypt 500; SPA checklist + messages in fr/en/ar). Password policy (min length, passphrases, optional HIBP); `@Size(max=72)` on login.
- [ ] **S-15** TOTP MFA for admins; forced password change for the bootstrap admin.
- [ ] **S-17** Purge jobs, IP anonymisation, phone column encryption, account deletion endpoint + UI.
- [x] **S-19** — done 2026-11-19 (ci.yml: contents:read, SHA-pinned actions, frontend lint/test/build/npm audit job, both images built and Trivy-scanned; security.yml: gitleaks, CodeQL java+ts (security-extended), Trivy fs dependency scan to the Security tab, weekly schedule; dependabot.yml for maven/npm/docker/compose/actions. OWASP dependency-check replaced by Trivy fs: no NVD key needed. Gates fail on fixable CRITICAL; HIGH reported). CI: Dependabot (maven, npm, docker, actions), dependency-check, CodeQL/Semgrep, gitleaks,
  Trivy on both images, frontend build/test/audit job, `permissions: contents: read`, SHA-pinned actions.

### Phase 3 — Ongoing / hygiene (Low)

- [ ] **S-20** Container hardening and digest-pinned images.
- [x] **S-21** — done 2026-11-19 (a new reset request, and a completed reset, mark every other unused reset token used). Invalidate older reset tokens on a new request.
- [x] **S-22** — done 2026-11-19 (defaults no-reply@carlink.invalid; .env.example blank; prod refuses a missing/placeholder MAIL_FROM). Neutral default sender; remove personal address from config.
- [x] **S-23** — done 2026-11-19 (unpadded Base64URL: 32 bytes = 43 chars = 256 bits, was 192; old tokens stay valid, only hashes are stored). Base64URL token encoding.
- [ ] **S-25** Security alerting (logs → alerts) and a short incident-response runbook in `deployment.md`.
- [ ] External penetration test before scaling; repeat this audit after Phase 2.

---

## 5. Changelog of this plan

| Date | Change |
|---|---|
| 2026-11-18 | Initial audit: 25 findings (1 critical-local, 6 high, 12 medium, 6 low/info), 4-phase plan. |
| 2026-11-18 | **Phase 0 complete**: S-01, S-05, S-16, S-07 (npm + Node 22). Remaining S-07 work (Spring Boot upgrade) is in Phase 1. |
| 2026-11-18 | **Phase 1** — done: S-02, S-03, S-04, S-06, S-08, S-11, S-12, S-18, S-07 step 1 (Boot 3.5.16). Open: S-07 step 2 (Boot 4.1 migration); S-11 follow-ups (order cap per e-mail/phone, login challenge); operator actions: Turnstile keys, Telnyx spend alert, CARLINK_DOMAIN/ACME_EMAIL + DNS. |
