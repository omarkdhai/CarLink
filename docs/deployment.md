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

Generate the two JWT secrets independently. They must differ from each other.

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 48   # JWT_REFRESH_SECRET
```

`docker-compose.prod.yml` refuses to start if `POSTGRES_PASSWORD`,
`REDIS_PASSWORD`, `JWT_SECRET`, `JWT_REFRESH_SECRET`, `MAIL_HOST`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, `APP_BASE_URL` or `APP_PUBLIC_URL` is unset —
this is deliberate, since each of those silently falls back to a
development-grade default if omitted.

| Variable | Notes |
|---|---|
| `EMAIL_PROVIDER` | Defaults to `smtp` in prod. **Never `mock` in production** — the mock sender logs messages instead of delivering them, so verification and password-reset mail silently disappears. |
| `APP_BASE_URL` | Public origin used to build QR, verification and reset links. Must be the real `https://` URL, or those links point nowhere. |
| `APP_PUBLIC_URL` | Public frontend origin, used for links and CORS. |
| `CORS_ALLOWED_ORIGINS` | Leave **empty** for the normal deployment: nginx serves the SPA and API from one origin, so no cross-origin access is needed. |
| `CONTACT_PROVIDER` | `mock` (default) logs instead of sending real WhatsApp/SMS. Set `twilio` and supply the Twilio variables to go live. |
| `HSTS_ENABLED` | `false` until TLS is confirmed working — see §3. |

**Rotating `JWT_SECRET` or `JWT_REFRESH_SECRET` invalidates every issued token,
logging all users out.** Rotate deliberately, not as a reflex.

## 3. TLS

The app does not terminate TLS. Put Nginx behind a reverse proxy, load balancer
or tunnel that does (Caddy, nginx, Cloudflare Tunnel, ALB, …), and point it at
the frontend container's port 80.

Two things to get right:

- **`HSTS_ENABLED=true` only once TLS is confirmed working.** HSTS is ignored by
  browsers over plain HTTP, and `includeSubDomains` cannot be withdrawn once a
  browser has cached it. Turning it on early risks locking yourself out of
  every subdomain.
- **`APP_BASE_URL` must be the `https://` URL.** If TLS terminates upstream of
  the Nginx container, Nginx's `$scheme` is `http`, so a link derived from the
  request would be downgraded. Building links from `APP_BASE_URL` instead keeps
  them correct regardless of where TLS ends.

Only the frontend container's port is published. The backend is bound to
`127.0.0.1` and is reached over the internal network by service name, so the
API and `/actuator` are not reachable directly from the internet — they cannot
bypass the proxy's rules. To reach the API from another host, use
`docker compose exec` (§10) rather than re-publishing the port.

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
| QR / reset links point at `localhost` | `APP_BASE_URL` unset or wrong. It is required in prod, so this usually means the dev profile is active. |
| Swagger still reachable | The `prod` profile is not active; `springdoc` is only disabled there. |
| `429`s from one IP | Working as intended — see `carlink.ratelimit` in `application.yml`. Raising limits weakens spam protection. |
| Requests all appear to come from one IP | TLS terminates upstream and `HSTS`/forwarded headers are misconfigured; check §3 and the `X-Forwarded-For` note in `frontend/nginx.conf`. |

## 13. Known operational gaps

Not yet automated, listed so they are not mistaken for oversights:

- **No automated backup schedule.** §7 is a manual command; there is no cron,
  systemd timer or managed-snapshot policy in this repo.
- **No metrics shipping.** The Prometheus endpoint exists and is authenticated,
  but no scrape config or alert rules are provided.
- **No log aggregation.** Logs are container-local and lost on host loss.
- **Redis has no memory cap.** It defaults to `noeviction`; if it fills, writes
  fail and rate limiting breaks. Note that switching to an eviction policy would
  discard rate-limit counters, which weakens abuse protection — this needs a
  deliberate decision, not a config flip.
- **No TLS or reverse-proxy config in this repo.** §3 assumes one exists
  elsewhere.