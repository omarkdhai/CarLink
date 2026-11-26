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
- A copy of `.env.prod.example` filled in as `.env.prod` (§4). **`.env.prod` is
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
| `CONTACT_PROVIDER` | `mock` (default) logs instead of sending real SMS. **`tunisiesms`** sends the owner alerts by SMS through TunisieSMS (production in Tunisia); `telnyx` is kept as an alternative. |
| `TUNISIESMS_API_KEY` | TunisieSMS API key (client area app.tunisiesms.tn). Required when `CONTACT_PROVIDER=tunisiesms`; startup fails fast if blank. Sent only in a POST body, never in a URL or a log. |
| `TUNISIESMS_SENDER` | Sender name authorized on the account (default `CarLink`). A sender that is not authorized makes every SMS fail with code 442. |
| `WHATSAPP_PROVIDER` | `off` (default, SMS only), `mock` (logs) or `meta`: every alert also goes to the owner's WhatsApp through Meta's WhatsApp Cloud API, unless the owner turned it off in their profile. Setup: `docs/whatsapp.md`. |
| `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN` | Required when `WHATSAPP_PROVIDER=meta`; startup fails fast if one is blank. The token and app secret are secrets. |
| `WHATSAPP_TEMPLATE_NAME`, `WHATSAPP_TEMPLATE_LANG` | The approved Utility template (default `carlink_alert`, `ar`). |
| `TELNYX_API_KEY` | Telnyx API key. Required when `CONTACT_PROVIDER=telnyx`; startup fails fast if it is blank. |
| `TELNYX_SMS_FROM` | SMS-enabled number in E.164, attached to a messaging profile. Required for SMS relay. |
| `TELNYX_VOICE_FROM` | Caller ID for the alert call. |
| `TELNYX_CONNECTION_ID` | Call Control connection the outbound alert call is placed on. |
| `TELNYX_VOICE_AUDIO_URL` | Public HTTPS URL of the clip played when the owner answers. Without it the alert call is skipped (a call to silence is worse than none). |
| `HSTS_ENABLED` | `false` until TLS is confirmed working — see §3. |
| `PHONE_ENCRYPTION_KEY` | **Required.** AES-256 key for owners' phone numbers at rest: 64 hex characters (`openssl rand -hex 32`). Never change it on a live database — the stored numbers become unreadable — and keep its backup apart from database dumps (S-17). |
| `METRICS_SCRAPE_TOKEN` | Optional. Prometheus credential for `/actuator/prometheus` only (32+ chars, `openssl rand -hex 32`); see §11. |
| `RETENTION_*` | Data-retention periods, run daily at 03:30 (`RETENTION_CRON`): `RETENTION_CONVERSATION_DAYS` (90), `RETENTION_TOKEN_GRACE_DAYS` (7), `RETENTION_IP_DAYS` (90), `RETENTION_CONTACT_MESSAGE_DAYS` (365); `RETENTION_ENABLED=false` pauses it (e.g. during a restore). |
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

The server never builds the application. Releases are images published by
`.github/workflows/release.yml`: push a tag `vX.Y.Z` and, once the full CI
passes on that commit, `ghcr.io/omarkdhai/carlink-backend:X.Y.Z` and
`carlink-frontend:X.Y.Z` (plus an immutable `sha-<commit>` tag) are pushed to
GitHub Container Registry. `CARLINK_VERSION` in `.env.prod` selects the release.

GHCR packages are private when first created. Either make both packages public
(the repository is public and the images contain no secrets), or log the server
in once with a read-only token: a classic PAT with only `read:packages`,
`docker login ghcr.io -u <user>` (password on stdin, never on the command line).

```bash
cp .env.prod.example .env.prod
sh scripts/prod-secrets.sh .env.prod   # generates every secret, prints none, lists what is left
$EDITOR .env.prod          # fill every value under a "# FILL" line (see §2)

# Pull the release and start
docker compose -f docker-compose.prod.yml --env-file .env.prod pull --ignore-buildable
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --no-build

# Watch it come up
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f backend
```

Startup order is enforced by healthchecks: Postgres and Redis must be healthy
before the backend starts, and the backend must be healthy before Nginx starts.
Flyway runs automatically at backend startup.

Local dry-run of the production stack (no registry needed): set
`CARLINK_VERSION=local` and use `up -d --build`; compose builds both images
under the release names.

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

`scripts/backup.sh` dumps the database (custom format), checks the archive,
keeps the newest 14 dumps in `/var/backups/carlink` (root only) and, when
configured, copies each dump off the server and pings a dead-man check. It never
prints a secret; the dump does not contain `PHONE_ENCRYPTION_KEY`.

```bash
# Once by hand, from /opt/carlink
sh scripts/backup.sh

# Nightly at 02:30 (before the 03:30 retention job): crontab -e as root
30 2 * * * cd /opt/carlink && BACKUP_REMOTE=b2crypt:carlink-backups BACKUP_PING_URL=https://hc-ping.com/<uuid> sh scripts/backup.sh >> /var/log/carlink-backup.log 2>&1
```

**Off-site copy (free).** Server snapshots live at the same provider as the
server, so also keep a copy elsewhere:

1. Backblaze B2 (10 GB free): create a private bucket and an application key
   limited to that bucket.
2. On the server: `apt -y install rclone`, then `rclone config`: a `b2` remote
   with that key, and a `crypt` remote named `b2crypt` on top of it (the dumps
   hold e-mail addresses; crypt encrypts them before they leave the server).
   Store the crypt passwords in your password manager.
3. Set `BACKUP_REMOTE=b2crypt:carlink-backups`; remote dumps older than 30 days
   are deleted (`BACKUP_REMOTE_DAYS`).

**Know when it stops.** Create a free check on healthchecks.io (daily period)
and put its ping URL in `BACKUP_PING_URL`; if a night is missed or fails, it
e-mails you.

**Test a restore every month**: copy a dump to your PC and restore it into a
throwaway container (§8 commands against `docker run --rm -e POSTGRES_PASSWORD=t
postgres:15-alpine`), then count a few tables. A backup you have never
restored is a guess.

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
# Point CARLINK_VERSION in .env.prod back at the previous release, then:
docker compose -f docker-compose.prod.yml --env-file .env.prod pull --ignore-buildable
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --no-build
```

Every release stays in GHCR, so nothing is rebuilt and no dependency is
re-resolved from Maven Central during a rollback.

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

Metrics live at `/actuator/prometheus` on the backend (`backend:8080` inside the
Docker network). They are never reachable through nginx (security plan S-05),
and reading them needs either an ADMIN token or, for Prometheus,
`METRICS_SCRAPE_TOKEN` (32+ characters), which opens that one path and nothing
else (S-25).

**Security signals (S-25).** Every security-relevant event increments
`carlink_security_events_total{type, detail}` and writes one line on the
`carlink.security` logger — `SECURITY_EVENT type=… detail=…` — with no
personal data (`detail` is a route pattern or a fixed name):

| type | Meaning |
|---|---|
| `login_failure` | Wrong e-mail or password |
| `login_blocked` | Login refused by an (IP, e-mail) lockout |
| `rate_limited` | Any 429; `detail` is the route pattern |
| `captcha_failed` | Turnstile token missing or rejected |
| `admin_login` | An administrator signed in |
| `refresh_token_reuse` | A rotated refresh token was replayed (sessions revoked automatically) |
| `sessions_revoked` | An account's sessions were ended (deactivation, role change, reset) |
| `account_deleted` | An owner deleted their account |
| `server_error` | Unhandled exception (500); `detail` is the exception class |

**Alert rules.** `monitoring/prometheus/alerts.yml` (validated in CI by
`promtool`) covers: backend down, 5xx bursts, failed-login spikes, lockouts,
429 spikes per route, captcha failures, refresh-token reuse, every admin
sign-in, and mass session revocation. `monitoring/prometheus/prometheus.yml`
is a ready scrape config: run Prometheus on the CarLink network, put the scrape
token in `/etc/prometheus/scrape-token`, and point it at an Alertmanager that
reaches a person (e-mail or Telegram).

**Without Prometheus**, alert on the log lines instead: `SECURITY_EVENT
type=refresh_token_reuse`, `type=server_error` (both WARN) and bursts of the
others. Also watch Postgres disk growth and readiness flapping.

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

- **No metrics shipping.** The Prometheus endpoint exists, is admin-only and is not proxied by nginx (scrape backend:8080 from inside the network),
  but no scrape config or alert rules are provided.
- **No log aggregation.** Logs are container-local and lost on host loss.
- **Redis is capped at 64 MB with `noeviction`** (`docker-compose.prod.yml`). At
  the cap, writes fail and rate limiting breaks rather than silently dropping
  counters; CarLink needs a few KB per active visitor, so the cap is far away.
  Every container also has a memory limit sized for a 4 GB server.
- **No TLS or reverse-proxy config in this repo.** §3 assumes one exists
  elsewhere.
- **Contact relay defaults to the logging mock.** With `CONTACT_PROVIDER=mock`,
  submissions are recorded but never reach the owner. Production uses
  `CONTACT_PROVIDER=tunisiesms` with `TUNISIESMS_API_KEY` (SMS only, one SMS per
  alert, always in Tunisian Derja: an emoji + the reason, then the visitor's note
  exactly as typed. Arabic makes it a Unicode SMS of 70 characters, so a long
  note is shortened; the full note is in the dashboard and the public page shows
  the visitor how many characters fit). There is no alert call with TunisieSMS.
  Watch the balance in the TunisieSMS client area: at zero every alert fails
  (log line `[TUNISIESMS] insufficient balance (402)`). Telnyx (`telnyx` and the
  `TELNYX_*` variables) remains available for SMS + alert calls.
- **WhatsApp needs Meta's setup.** With `WHATSAPP_PROVIDER=meta` every alert is
  also sent as the approved `carlink_alert` template (docs/whatsapp.md). Its
  delivered/read status arrives on the webhook
  `/api/v1/public/webhooks/whatsapp`, which needs the public HTTPS domain; until
  the webhook is configured in Meta, the WhatsApp channel stays "Sent". The alert
  counts as delivered when either channel reaches the owner.
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

**Off-topic notes are refused** by a small classifier built into the backend
(`NoteClassifier`, logistic regression over character fragments and words; no
library, no outside service — the note never leaves the server, nothing to pay
or keep running). Before anything is saved or sent, a note the model judges
not about the car (ads, scams, dating, insults, threats, unrelated chat,
gibberish) with probability ≥ `CONTACT_SPAM_FILTER_THRESHOLD` (default 0.8)
gets 400 `NOTE_OFF_TOPIC`, and the page asks the visitor to change it. Phone
numbers and e-mail addresses are welcome; an empty note always passes. The
answer never repeats the note; the log line has only the score.

- **Training data:** `backend/src/main/resources/spam/notes-train.tsv`, one
  labelled note per line (`A` = allowed, `B` = blocked) in Derja (Arabic and
  Latin letters), Arabic, French and English. The model is trained from it at
  startup (deterministic). To fix a mistake, add the note (or a similar one)
  with the right label and redeploy — never with a real phone number or name.
- **Quality gate:** `NoteClassifierEvaluationTest` measures the model on
  held-out notes (`src/test/resources/spam/notes-test.tsv`) and by 5-fold
  cross-validation, and fails the build if it starts blocking honest notes
  (more than 1 held-out, more than 2% in cross-validation) or catching too
  little spam. Measured at 0.8 with the Derja insult data (2026-11-26):
  honest notes wrongly blocked 0/52 held-out and 1.0% (6/619) in
  cross-validation; spam caught 18/32 held-out and 70% in cross-validation;
  unseen Derja insults refused 47/47.
- **Threshold:** lower catches more spam but blocks more honest notes (0.7:
  about 3% honest notes blocked in cross-validation, 49% of spam caught).
  Blocking a real alert is the worse mistake, hence 0.8.
- `CONTACT_SPAM_FILTER_ENABLED=false` turns the filter off.
- **Vulgar Derja never reaches the operator.** TunisieSMS refuses vulgar or
  insulting content and can block the sender account, so the model is trained
  on Derja insults in Latin and Arabic letters (`zebi`, `nikomk`, `3asba`,
  `sorm`, `9a7ba`…), alone and inside polite car sentences, and reads each word
  a second time with disguises undone (`NoteClassifier.fold`: `z.e.b.i`,
  `k@hba`, `3a$ba`, `9a7ba`, `mibboun`). A stricter gate,
  `derjaInsultsAreAlwaysRefused` (`src/test/resources/spam/insults-test.tsv`),
  fails the build unless **every** unseen insult is refused and every
  look-alike honest note passes. A new insult spelling that gets through: add
  it (and a few variants) as `B` to the training file and redeploy.
- **Testing spam notes safely:** never test insults or spam against the live
  SMS provider. Set `CONTACT_PROVIDER=mock` (and `WHATSAPP_PROVIDER=mock`) in
  `.env` and restart the backend: the filter behaves the same, and nothing is
  sent to TunisieSMS or Meta.
- **Second line of defence:** if a note the classifier missed is refused by the
  phone operator for its content (TunisieSMS report `UNAUTHORIZED`), the alert
  is hidden from the owner (`conversations.hidden_at`, V13: not in the list,
  the detail answers 404) and the visitor sees "Your note was refused" with a
  button to change it. The row stays for admin moderation and normal retention.
  Add such notes to `notes-train.tsv` as `B` so the classifier learns them.

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


## 16. Incident response (security plan S-25)

Keep it short and in this order. Write down times and actions as you go.

**1. Confirm.** Which alert fired, since when, how many events? Read the
`SECURITY_EVENT` lines and the audit log (`/admin` → Audit). Is it one
account, one route, one IP range, or everything?

**2. Contain** — pick what fits, smallest first:

| Situation | Action |
|---|---|
| One account taken over | Admin → Users → **Deactivate**: ends all its sessions at once (S-10). Then the owner resets their password. |
| Credential stuffing / login flood | Lower `carlink.throttle.login-ip`, and turn on Turnstile (`CAPTCHA_ENABLED=true`) if it is off. |
| Public forms or relay abused | Turn on Turnstile; lower the `relay-*` throttles; set a hard daily cap in the Telnyx portal (§14). |
| Refresh-token reuse repeats for one user | Deactivate the account; check their devices with them. |
| Admin account suspicious | Deactivate it from another admin account; rotate `ADMIN_BOOTSTRAP_PASSWORD` only if that account is the bootstrap admin. |
| `JWT_SECRET` exposed | Replace it (§15) and restart: every access token dies, everybody signs in again. |
| Database or Redis password exposed | Rotate per §15; check `pg_stat_activity` for unknown clients. |
| `PHONE_ENCRYPTION_KEY` exposed together with a DB dump | Treat owners' phone numbers as disclosed (step 4). Re-keying needs a re-encryption job — do not just swap the key, or every number becomes unreadable. |
| Server compromised | Take it off the network, keep the disk for analysis, rebuild from Git on a clean host, restore the last good backup (§8), rotate **every** secret. |

**3. Eradicate and recover.** Fix the cause (patch, config, revoked key), deploy,
and watch the same alert stay quiet for a day.

**4. Notify.** If personal data (e-mails, phone numbers, messages) may have been
read by someone else: the INPDP (Tunisian data-protection authority, Organic
Law 2004-63) and, for EU visitors, GDPR's 72-hour rule. Tell affected owners
plainly what happened and what to do.

**5. Learn.** A short note in `docs/progress.md`: timeline, cause, what changed,
and any new alert or test that would have caught it earlier.
