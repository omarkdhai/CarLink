# CarLink Production Deployment Runbook

Operational guide for running CarLink in production. For the security model see
[security.md](security.md); for how the code is arranged see
[architecture.md](architecture.md).

The production stack is defined in `docker-compose.prod.yml`: Nginx (static SPA +
API proxy), Spring Boot, PostgreSQL and Redis.

---

## 1. Prerequisites

- Docker Engine 24+ with the Compose plugin (`docker compose version`).
- A host with enough disk for the Postgres volume plus at least 2 GB free.
- A domain with DNS pointing at the host, and TLS terminated in front of it
  (see §3).
- SMTP credentials for the sending domain.
- A copy of `.env.example` filled in as `.env.prod`. **`.env.prod` is
  gitignored; never commit it.**

## 2. Secrets

Generate the JWT signing secret (refresh tokens are opaque random values
hashed in the database, so there is no second secret). The backend refuses to
start with a missing, short (< 32 bytes), publicly known or low-entropy value.

```bash
openssl rand -hex 48   # JWT_SECRET
```

`docker-compose.prod.yml` refuses to start if `POSTGRES_PASSWORD`,
`REDIS_PASSWORD`, `JWT_SECRET`, `MAIL_HOST`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, `APP_BASE_URL` or `APP_PUBLIC_URL` is unset —
this is deliberate, since each of those silently falls back to a
development-grade default if omitted.

| Variable | Notes |
|---|---|
| `MAIL_HOST` / `MAIL_PORT` | SMTP transport. Gmail: `smtp.gmail.com` on `465` (implicit TLS). |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP account. Gmail needs 2-step verification and a 16-character **App Password** — the normal account password is rejected. |
| `MAIL_SMTP_AUTH` / `MAIL_SMTP_STARTTLS` | Default `true` / `false`. Keep STARTTLS `false` on port 465; set it `true` only when the provider is reached on 587. |
| `MAIL_FROM` / `MAIL_REPLY_TO` | Envelope sender and Reply-To on every transactional mail. Gmail only accepts a sender the authenticated account owns, so both track `MAIL_USERNAME` until a branded mailbox exists. Customers replying to an order confirmation reach the Reply-To address. |
| `EMAIL_PROVIDER` | Defaults to `smtp` in prod. **Never `mock` in production** — the mock sender logs messages instead of delivering them, so verification, password-reset and order-confirmation mail silently disappears. |
| `APP_BASE_URL` | Public origin used to build QR, verification and reset links. Must be the real `https://` URL, or those links point nowhere. |
| `APP_PUBLIC_URL` | Public frontend origin, used for links and CORS. |
| `CORS_ALLOWED_ORIGINS` | Leave **empty** for the normal deployment: nginx serves the SPA and API from one origin, so no cross-origin access is needed. |
| `CONTACT_PROVIDER` | `mock` (default) logs instead of sending real SMS. Set `telnyx` and supply the Telnyx variables below to go live. |
| `CONTACT_ENABLED_CHANNELS` | Channels advertised and accepted. Defaults to `SMS`; WhatsApp is deliberately not enabled (needs a Meta template outside the 24-hour window). |
| `TELNYX_API_KEY` | Telnyx API key. Required when `CONTACT_PROVIDER=telnyx`; startup fails fast if it is blank. |
| `TELNYX_SMS_FROM` | SMS-enabled number in E.164, attached to a messaging profile. Required for SMS relay. |
| `TELNYX_VOICE_FROM` | Caller ID for the alert call. |
| `TELNYX_CONNECTION_ID` | Call Control connection the outbound alert call is placed on. |
| `TELNYX_VOICE_AUDIO_URL` | Public HTTPS URL of the clip played when the owner answers. Without it the alert call is skipped (a call to silence is worse than none). |
| `HSTS_ENABLED` | `false` until TLS is confirmed working — see §3. |
| `PASSWORD_BREACH_CHECK` | Default `true` in prod: new passwords are checked against Have I Been Pwned by k-anonymity (only 5 hex characters of the SHA-1 leave the server). Needs outbound HTTPS to `api.pwnedpasswords.com`; if it is unreachable the check fails open. |
| `JWT_ADMIN_ACCESS_EXPIRATION_MINUTES` | Admin access-token lifetime, default `5` (S-10). |

**Rotating `JWT_SECRET` invalidates every issued access token.** Sessions
recover transparently through their (database-backed) refresh token; to force
everyone to sign in again, also revoke the rows in `refresh_tokens`.

## 3. TLS (Caddy edge, security plan S-02)

`docker-compose.prod.yml` terminates TLS itself with a **Caddy** container
(`caddy/Caddyfile`). Caddy is the only service published to the internet; it
obtains and renews the certificate automatically, redirects HTTP to HTTPS
(308), sends HSTS (`max-age=31536000`, no `includeSubDomains`), strips the
`Server` header, and proxies everything to the nginx container.

Required:

| Variable | Notes |
|---|---|
| `CARLINK_DOMAIN` | Public hostname (e.g. `carlink.tn`). Its DNS A/AAAA record must point at this host **before** first start. |
| `ACME_EMAIL` | Address for certificate-expiry notices from the CA. |
| `HTTP_PORT` / `HTTPS_PORT` | Default `80` / `443`. Both must be reachable from the internet (HTTP-01 / TLS-ALPN-01 challenges). |

Things to get right:

- **Keep the `caddy_data` volume.** It holds the certificates and the ACME
  account; recreating it on every deploy hits the CA's rate limits.
- **Leave `HSTS_ENABLED=false`** in the backend: Caddy already sends HSTS for
  every response, the backend would only duplicate it on `/api`.
- **`APP_BASE_URL` / `APP_PUBLIC_URL` must be `https://<CARLINK_DOMAIN>`**; links
  in e-mails and QR codes are built from them.
- **Client IPs.** nginx trusts `X-Forwarded-For` only from the prod Docker
  subnet `172.28.0.0/24` (`set_real_ip_from` in `frontend/nginx.conf`), and
  Caddy discards any client-supplied value, so per-IP rate limits see the real,
  unspoofable visitor address. If you change the subnet in the compose file,
  change it in `nginx.conf` too.
- To use another TLS terminator instead (Cloudflare Tunnel, a load balancer),
  remove the `caddy` service, publish the frontend, and point
  `set_real_ip_from` at that proxy's address range.

The backend is bound to `127.0.0.1` and nginx is not published at all, so the
API and `/actuator` cannot be reached without going through Caddy → nginx. To
reach the API from the host, use `docker compose exec` (§10).

Verified locally with `CARLINK_DOMAIN=localhost` (Caddy's internal CA): 308
redirect, HSTS + CSP on HTTPS, API over TLS, metrics 404 at the edge, and nginx
logging the real client IP even when a spoofed `X-Forwarded-For` is sent.

## 4. First deploy

```bash
cp .env.example .env.prod
$EDITOR .env.prod          # fill in secrets and URLs from §2

# Build and start
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# Watch it come up
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f backend
```

Startup order is enforced by healthchecks: Postgres and Redis must be healthy
before the backend starts, and the backend must be healthy before Nginx starts.
Flyway runs automatically at backend startup.

## 5. Database migrations

Flyway migrations in `backend/src/main/resources/db/migration` are applied by the
backend on startup, and Hibernate is set to `validate`, never `update`. So a
migration that has not been applied is a startup failure, not a silent schema
drift — the backend will not come up until the schema matches.

Consequences worth planning around:

- Migrations are **forward-only**. There is no automated down-migration; rolling
  back the application code after a migration means restoring the database (see
  §8). Back up before deploying any release containing a new `V*.sql`.
- Keep migrations **backward-compatible** with the currently running version
  (add columns, don't rename or drop in the same release that stops using them),
  so a rollback does not require a restore.
- Check what a release will do before deploying:

  ```bash
  git diff <deployed-ref>..HEAD -- backend/src/main/resources/db/migration
  ```

## 6. Post-deploy verification

```bash
# Containers up and healthy
docker compose -f docker-compose.prod.yml --env-file .env.prod ps

# Overall, liveness, readiness (all should report UP)
curl -fsS https://carlink.example.com/actuator/health
curl -fsS https://carlink.example.com/actuator/health/liveness
curl -fsS https://carlink.example.com/actuator/health/readiness
```

Then confirm, through the public entry point only:

- The SPA loads and the language switcher works.
- `/c/{token}` renders, requires a reason, and a submission succeeds.
- Registration sends a **real** verification email that arrives (this is the
  check that catches `EMAIL_PROVIDER=mock`).
- Password reset delivers a real email.
- Placing a test order delivers the **order-confirmation** email to the buyer
  address, with the reference, item/total breakdown and delivery details.
  Delivery failure only logs a warning (no customer data), so a broken SMTP
  setup never loses the order — but it will go unnoticed without this check.
- Swagger is **not** reachable: `/swagger-ui/` and `/v3/api-docs` must 404 or
  return the SPA shell, not API documentation. They are disabled in the prod
  profile.

## 7. Backups

Postgres holds everything that matters. Redis only holds rate-limit counters
and short-lived caches, so losing it costs a burst of un-throttled requests, not
data.

```bash
# Daily, from the host
docker compose -f docker-compose.prod.yml --env-file .env.prod \
  exec -T postgres pg_dump -U carlink -Fc carlink \
  > "carlink-$(date +%F).dump"
```

Store dumps **off-host**, and test a restore at least once (below). A backup you
have never restored is a guess.

## 8. Restore

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod stop backend

docker compose -f docker-compose.prod.yml --env-file .env.prod \
  exec -T postgres pg_restore -U carlink -d carlink --clean --if-exists < carlink-YYYY-MM-DD.dump

docker compose -f docker-compose.prod.yml --env-file .env.prod start backend
```

Restoring discards everything written since the dump, including accounts
registered after it. Take a fresh dump first if that data matters.

## 9. Rollback

Rollback is only safe if no migration ran (§5).

```bash
# Redeploy the previous code. compose cannot pull an older image by reference,
# so check the ref out and rebuild.
git checkout <previous-ref>
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Keep the previous tag's images before rebuilding, so a rollback does not have to
re-resolve every dependency from Maven Central.

If a migration *did* run, roll the code back **and** restore the database from
the pre-deploy dump (§8). There is no down-migration to fall back on.

## 10. Logs and debugging

Logs are capped per container at 10 MB × 5 files, so they rotate rather than
filling the disk.

```bash
DC="docker compose -f docker-compose.prod.yml --env-file .env.prod"

$DC logs -f backend
$DC logs --tail 200 frontend
$DC exec backend sh -c 'id && ls -l /app/app.jar'   # image sanity check
$DC exec postgres psql -U carlink -c 'SELECT "version", description, success FROM flyway_schema_history ORDER BY installed_rank;'
```

The backend runs as the unprivileged `carlink` user (`uid=999`), not root.

## 11. Monitoring

`/actuator/prometheus` and `/actuator/metrics` are **authenticated** — they fall
through to the authenticated rule in `SecurityConfig`, so a scraper needs
credentials. Only `/actuator/health`, `/actuator/health/*` and
`/actuator/info` are public, and they must stay public for probes to work.

Worth alerting on: readiness flapping, 5xx rate, `429` rate on `/c/{token}`
(possible scan abuse), and Postgres disk growth.

## 12. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Backend exits at startup, complains about the schema | A Flyway migration failed or is missing. Check `flyway_schema_history` (§10). |
| Backend exits, "could not resolve parent POM" / Maven errors | Only affects `--build`; see the Dockerfile retry notes. A cached image avoids it. |
| Verification emails never arrive | `EMAIL_PROVIDER` is `mock`, or SMTP is rejecting. Check §2 and the backend log. |
| `535` / "Username and Password not accepted" | Gmail rejects the account password — create an App Password (2-step verification required) and put it in `MAIL_PASSWORD`. |
| Order confirmations missing but orders exist | The send is fail-safe: grep the backend log for `Order confirmation email could not be sent for <reference>`; the cause is on the following lines. The order itself was still created. |
| QR / reset links point at `localhost` | `APP_BASE_URL` unset or wrong. It is required in prod, so this usually means the dev profile is active. |
| Swagger still reachable | The `prod` profile is not active; `springdoc` is only disabled there. |
| `429`s from one IP | Working as intended — see `carlink.ratelimit` in `application.yml`. Raising limits weakens spam protection. |
| Requests all appear to come from one IP | TLS terminates upstream and `HSTS`/forwarded headers are misconfigured; check §3 and the `X-Forwarded-For` note in `frontend/nginx.conf`. |

## 13. Known operational gaps

Not yet automated, listed so they are not mistaken for oversights:

- **No automated backup schedule.** §7 is a manual command; there is no cron,
  systemd timer or managed-snapshot policy in this repo.
- **No metrics shipping.** The Prometheus endpoint exists, is admin-only and is not proxied by nginx (scrape backend:8080 from inside the network),
  but no scrape config or alert rules are provided.
- **No log aggregation.** Logs are container-local and lost on host loss.
- **Redis has no memory cap.** It defaults to `noeviction`; if it fills, writes
  fail and rate limiting breaks. Note that switching to an eviction policy would
  discard rate-limit counters, which weakens abuse protection — this needs a
  deliberate decision, not a config flip.
- **No TLS or reverse-proxy config in this repo.** §3 assumes one exists
  elsewhere.
- **Contact relay defaults to the logging mock.** With `CONTACT_PROVIDER=mock`,
  submissions are recorded but never reach the owner. Set `telnyx` and supply
  `TELNYX_API_KEY`, `TELNYX_SMS_FROM`, `TELNYX_VOICE_FROM`,
  `TELNYX_CONNECTION_ID` and `TELNYX_VOICE_AUDIO_URL` for real SMS + alert calls.
- **WhatsApp is unsupported by design.** Telnyx delivers WhatsApp free-text only
  inside a 24-hour window opened by the recipient; a QR scan never opens it, so
  WhatsApp would need a Meta-approved template. `CONTACT_ENABLED_CHANNELS`
  defaults to `SMS` and both the page and the API honour it (a disabled channel
  is a 400).
- **The alert call plays a static clip.** `TELNYX_VOICE_AUDIO_URL` is fixed at
  deploy time; the outbound dial API cannot speak the visitor's dynamic message.
  Speaking it would require hosting a Call Control `speak` webhook, which this
  app does not do. With no URL set the call is skipped (the SMS still sends).

## 14. SMS relay abuse limits (security plan S-12)

Every visitor alert can become a paid SMS. The backend caps it in layers, all
tunable under `carlink.throttle.*` (`max` / `window-seconds`):

| Limit | Default | Purpose |
|---|---|---|
| `relay-visitor-cooldown` | 1 per 120 s per (IP, sticker) | one visitor cannot spam one owner |
| `relay-sticker-day` | 30 per day per sticker | harassment through one sticker |
| `relay-owner-day` | 60 per day per owner | all of an owner's stickers together |
| `ratelimit.qr-per-hour` | 20 per hour per token | scans + submissions together |

Links in a visitor's note are replaced by `[link removed]` before the note is
stored or relayed (no phishing through a trusted sender).

**Also configure, in the Telnyx portal** (cannot be done from this repo): a
low-balance / spend alert on the account and a daily spend limit on the
messaging profile, so a bypass of these limits costs a bounded amount.

## 15. Rotating local secrets

The local stack (`docker-compose.yml`) reads every secret from the untracked
`.env`. `node scripts/dev-secrets.mjs` creates it and replaces any blank,
placeholder or previously committed value with a random one; it never prints
values. Rotation of an existing stack:

1. Blank the keys to rotate in `.env` (e.g. `JWT_SECRET=`) and run
   `node scripts/dev-secrets.mjs`.
2. **Postgres keeps the password it was initialised with.** Apply the new one
   inside the database *before* recreating the backend, feeding the SQL on
   stdin so the value never appears in a command line:
   `ALTER USER carlink PASSWORD '<new POSTGRES_PASSWORD>';` via
   `docker exec -i carlink-postgres psql -U carlink -d carlink`.
3. `docker compose up -d --build` (Redis and the JWT secret take effect on
   recreate; all access tokens become invalid, users simply sign in again).
4. The bootstrap admin is only created when absent: changing
   `ADMIN_BOOTSTRAP_PASSWORD` does not change an existing admin's password —
   use the password-reset flow for that.
