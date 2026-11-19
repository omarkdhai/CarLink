# CarLink — deployment process (from local stack to live pilot)

**Status:** plan approved 2026-11-19 · **Target:** pilot in one Tunisian city · **Budget:** 50–100 €/month

This is the roadmap to the first live version. The technical runbook for operating the production
stack (secrets, TLS, backups, rollback, incident response) stays in [deployment.md](deployment.md);
this document says **what to do, in which order, and who does it**.

## Where we start

- The application is feature-complete and hardened: the [security plan](security-plan.md) is done
  except the independent external penetration test, which needs a live environment
  (internal pre-test: [pentest-2026-11-19.md](pentest-2026-11-19.md)).
- The production stack already exists in `docker-compose.prod.yml`:
  Caddy (automatic TLS) → nginx (SPA + API proxy) → Spring Boot 4.1 → PostgreSQL 15 + Redis 7.
- Missing: the business and legal side, servers, automation (image publishing, backups, monitoring,
  deploys), the local SMS provider, and a staged rollout.

Decisions taken:

| Topic | Decision |
|---|---|
| Launch | Pilot in one city, then city-wide, then nationwide |
| Budget | ~50–100 €/month for infrastructure; SMS paid per use |
| Business | No company or domain yet: both are on the critical path |
| SMS | A Tunisian provider connected to **Ooredoo** (contract under negotiation). The Telnyx integration stays in the code, unused, until the switch is decided |
| Payments | Cash on delivery (already implemented); online payment only after the pilot |

---

## 1. Expected load

| | Month 1–3 (pilot) | Month 4–12 (city-wide) | Nationwide (later) |
|---|---|---|---|
| Stickers / registered owners | 200–1,000 | 1,000–5,000 | 10,000–50,000 |
| QR scans per month | 500–3,000 | 3,000–20,000 | 50,000–300,000 |
| SMS relayed per month | 300–2,000 | 2,000–12,000 | 30,000+ |
| Peak concurrent users | < 20 | < 100 | a few hundred |
| Peak requests per second | < 5 | < 20 | < 100 |

**One 4 vCPU / 8 GB server covers the first two columns with a large margin.** The real cost driver
is SMS, not servers: every alert a visitor sends is a paid message (capped per visitor, sticker and
owner — see [deployment.md §14](deployment.md)).

Growth path: bigger VPS → managed PostgreSQL → separate app servers behind a load balancer.
Kubernetes is not needed at any of these sizes.

## 2. Technology choices

| Need | Choice | Why |
|---|---|---|
| Application | Existing Docker Compose production stack | Already built, hardened (S-20) and tested |
| Production server | Hetzner Cloud **CPX31** (4 vCPU, 8 GB, 160 GB), or an OVH VPS in France if latency to Tunisia matters more | ~15 €/month, snapshots, reliable |
| Staging server | Hetzner Cloud **CPX21** (3 vCPU, 4 GB) | Same stack on `staging.<domain>`: release testing, monitoring, external pentest |
| Operating system | Ubuntu 24.04 LTS, Docker Engine + Compose plugin | Long-term support |
| TLS | Caddy + Let's Encrypt (already in the compose file) | Automatic certificates and renewal |
| DNS | Cloudflare free plan (DNS only at first) | Fast DNS, optional DDoS protection later |
| Images | **GitHub Container Registry**, built, scanned and pushed by CI on every version tag | The server pulls images; rollback = previous tag; no build tools on the server |
| Deploys | GitHub Actions: staging automatically, production on a version tag **with manual approval** | Repeatable and auditable |
| Backups | Nightly `pg_dump` → restic (encrypted) → Hetzner Storage Box, plus server snapshots | Off-host, encrypted, restore-tested |
| Monitoring | Prometheus + Alertmanager (rules in `monitoring/prometheus/`) → Telegram / e-mail; external uptime check (UptimeRobot or Better Stack free) | Alerts reach a person |
| Logs | Docker `json-file` with rotation (already configured); Grafana Cloud free tier later if needed | Enough for a pilot |
| E-mail | Brevo (free 300/day) or Postmark (~15 $/month) on the own domain, with SPF, DKIM, DMARC | Gmail SMTP is not suitable for production sending |
| SMS | Tunisian provider connected to Ooredoo (new adapter) | Local price, registered sender ID, deliverability |
| Bot protection | Cloudflare Turnstile (free), already wired as `CAPTCHA_*` | Protects login, orders and the relay |

## 3. Monthly cost (pilot)

| Item | €/month |
|---|---|
| Production VPS + snapshots | ~18 |
| Staging VPS | ~9 |
| Storage Box (backups) | ~4 |
| Transactional e-mail | 0–15 |
| Domain (`.tn` ~20–30 €/year, `.com` ~12 €/year) | ~2 |
| Cloudflare, Turnstile, GitHub, GHCR, uptime check | 0 |
| **Infrastructure** | **~35–50** |
| SMS (to be confirmed by the contract; e.g. 2,000 SMS) | ~20–40, variable |

---

## 4. Steps to the live version

Legend: 👤 you · 🤖 code/repo work · ☐ open · ☑ done

### Phase A — Business and legal (👤, start now, runs in parallel, 2–6 weeks)

- ☐ A1. Register the activity (patente or company): needed for a `.tn` domain, the SMS contract and invoices.
- ☐ A2. **INPDP** (Instance Nationale de Protection des Données Personnelles, Loi organique 2004-63):
  declare the processing of personal data (owners' phone numbers and e-mails). Hosting outside Tunisia
  may require a transfer authorization: **ask a local lawyer** whether EU hosting is acceptable or a
  Tunisian host is required. The answer can change the server choice in §2.
- ☐ A3. Buy the domain (`.tn` through an ATI-accredited registrar once the company exists; a `.com`
  can serve staging meanwhile).
- ☐ A4. Sign the SMS contract and obtain: API documentation, sandbox credentials, a sender ID
  (e.g. "CarLink") registered with Ooredoo, price per SMS, delivery reports, and coverage of Orange and
  Tunisie Telecom numbers.
- ☐ A5. Final legal pages (privacy policy, terms), including the Arabic version (choose formal Arabic
  or Derja for the 99 strings still shown in French).

### Phase B — Code and repository (🤖, ~1–2 weeks, one PR per item)

- ☐ B1. **Release images**: a release workflow runs the full CI on a `v*` tag, then pushes
  `ghcr.io/omarkdhai/carlink-backend` and `carlink-frontend` with that version;
  `docker-compose.prod.yml` uses those images (`CARLINK_VERSION`).
- ☐ B2. **Production config template**: `.env.prod.example` with every variable, and a secret
  generator for `JWT_SECRET`, `PHONE_ENCRYPTION_KEY`, database/Redis passwords and
  `METRICS_SCRAPE_TOKEN` that never prints them.
- ☐ B3. **Resource limits**: memory limits per service, JVM sized from the container
  (`MaxRAMPercentage`), Redis `maxmemory` with an alert.
- ☐ B4. **Backups**: nightly `pg_dump` → restic → Storage Box (systemd timer), plus a restore-drill
  script that restores into a throwaway container and checks row counts.
- ☐ B5. **Deploy workflow**: SSH deploy through GitHub Environments (`staging`, `production` with
  required approval), `docker compose pull && up -d`, health checks, automatic database dump before
  every production deploy.
- ☐ B6. **Monitoring stack** on the staging server: Prometheus + Alertmanager with the existing rules,
  notifications to a Telegram bot.
- ☐ B7. **Server bootstrap script**: deploy user, SSH keys only, firewall (22, 80, 443), fail2ban,
  automatic security updates, Docker, swap.
- ☐ B8. **Runbook update**: `deployment.md` reflects the release/deploy/backup flow; stale
  "known gaps" removed.
- ☐ B9. **Tunisian SMS adapter** — only when you give the go-ahead and the API documentation: a new
  provider next to the Telnyx one, selected by `CONTACT_PROVIDER`; Telnyx stays. The alert call stays
  off unless the new provider offers voice.

### Phase C — Staging (1 week)

- ☐ C1. Create the staging VPS, run the bootstrap script, point `staging.<domain>` at it.
- ☐ C2. Deploy through the workflow with real SMTP, Turnstile test keys and the SMS sandbox.
- ☐ C3. Full flow on real phones: order → activate sticker → scan → SMS received → reply from the dashboard.
- ☐ C4. Load test (k6, ~50 requests/second on scan and relay) to confirm §1.
- ☐ C5. Restore drill from a real backup.
- ☐ C6. **External penetration test** against staging (scope: "Not covered" in
  [pentest-2026-11-19.md](pentest-2026-11-19.md)), then fix its findings.

### Phase D — Production (1–2 days)

- ☐ D1. Create the production VPS with the same bootstrap; DNS A record to the server
  (Cloudflare DNS-only so Caddy obtains its certificate).
- ☐ D2. Generate `.env.prod` on the server. **Back up `PHONE_ENCRYPTION_KEY` apart from the database
  dumps** (password manager + offline copy): without it the stored phone numbers are unreadable.
- ☐ D3. Production Turnstile keys, real SMS credentials with a **spend cap**, real `MAIL_FROM`.
- ☐ D4. Tag `v1.0.0`, approve the production deploy, run the checks in [deployment.md §6](deployment.md).
- ☐ D5. Create the admin account, replace the bootstrap password, enable uptime monitoring and alerts.

### Phase E — Pilot launch (8–12 weeks)

- ☐ E1. Print the first batch of stickers (200–500); agree on a cash-on-delivery courier.
- ☐ E2. Closed beta (friends and family), then the public pilot in one city.
- ☐ E3. Weekly review: scans, SMS sent and their cost, orders, failed deliveries, alerts, support requests.
- ☐ E4. Go/no-go for the wider launch: no severe incident for 4 weeks, SMS cost per active owner known,
  delivery success above 90 %, backup restore tested every month.

## 5. Timeline

| Week | Work |
|---|---|
| 1–2 | Phase A starts (company, lawyer / INPDP, SMS negotiation) · Phase B1–B8 |
| 3 | Staging online (C1–C5) with sandbox or mock SMS |
| 3–5 | SMS adapter (B9) once the contract is signed · external pentest booked |
| 5–6 | Pentest fixes · domain and legal pages final |
| 6–7 | Production deploy (Phase D) · closed beta |
| 8+ | Public pilot in one city (Phase E) |

**The critical path is the legal work and the SMS contract, not the code.**

## 6. How each step is verified

- Every Phase B change: backend test suite, frontend type-check / lint / tests / build, CI green on the PR.
- Release images: `docker compose -f docker-compose.prod.yml pull` fetches a published version.
- Before staging: production stack dry-run locally (Caddy internal CA, `CARLINK_DOMAIN=localhost`).
- Staging: real-phone end-to-end flow, load test, restore drill, a test alert reaches Telegram.
- Production: [deployment.md §6](deployment.md) checks, SSL Labs grade A, securityheaders.com,
  uptime monitor green.
