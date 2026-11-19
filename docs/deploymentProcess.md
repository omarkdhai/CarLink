# CarLink — deployment process (simple, low cost)

**Goal:** put CarLink online for a launch of **up to ~200 users**, on one small server, for
**about 6 € a month** plus SMS. Keep it simple now; add more only when real usage asks for it
(see [§7](#7-when-to-grow)).

The detailed operating manual (all variables, TLS, restore, incident response) stays in
[deployment.md](deployment.md). This page is the short path to "live".

---

## 1. What 200 users means

| | At launch |
|---|---|
| Registered owners | ≤ 200 |
| QR scans | a few hundred per month |
| SMS sent | ~100–500 per month |
| People online at the same time | < 10 |

That is a very light load. **One small server runs everything** (website, API, database, cache,
HTTPS) with room to spare. No second server, no Kubernetes, no paid monitoring.

## 2. What you use and what it costs

| What | Service | Cost |
|---|---|---|
| Server | **Hetzner Cloud CX22** — 2 vCPU, 4 GB RAM, 40 GB disk, x86, Ubuntu 24.04 | ~5 €/month |
| Server backups | Hetzner automatic backups (7 daily copies, one click) | ~1 €/month |
| Off-site database copy | Backblaze B2 free tier (10 GB) | 0 € |
| Domain | `.com` now (~10 €/year); `.tn` later once the company exists | ~1 €/month |
| DNS | Cloudflare free | 0 € |
| HTTPS certificate | Let's Encrypt via Caddy (already in the project) | 0 € |
| E-mail (verification, password reset, orders) | Brevo free plan, 300 mails/day | 0 € |
| Bot protection | Cloudflare Turnstile | 0 € |
| Uptime alert | UptimeRobot free (e-mail / Telegram when the site is down) | 0 € |
| Application images | GitHub Container Registry (already automated, free for this public repo) | 0 € |
| SMS | Your Tunisian Ooredoo-connected provider, pay per message | ~5–25 €/month at this volume |
| **Total** | | **~7 €/month + SMS** |

The application stack does not change: `docker-compose.prod.yml` already contains
Caddy (HTTPS) → nginx (website) → Spring Boot (API) → PostgreSQL + Redis.

## 3. What you prepare (👤 you)

1. **Domain** — buy it (e.g. on Cloudflare Registrar or Namecheap) and manage its DNS in Cloudflare.
2. **Hetzner account** — create it; add your SSH public key in the console.
3. **Brevo account** — verify your domain (Brevo gives you the SPF/DKIM records to add in Cloudflare);
   note the SMTP login and key.
4. **Turnstile** — in Cloudflare, create a site for your domain; note the site key and secret key.
5. **SMS provider** — finish the contract; get the API documentation, test credentials, the sender
   name ("CarLink") and the price per SMS. Ask for a **monthly spending limit**.
6. **UptimeRobot account** — free.
7. **Legal** — ask a local lawyer one question: is a declaration to the **INPDP** (personal-data
   authority) needed, and is a server in Germany acceptable for owners' phone numbers? If a Tunisian
   host is required, only step 4.1 below changes.

## 4. Small code tasks before launch (🤖 me, one PR each)

| # | Task | Why |
|---|---|---|
| ☑ 1 | Release images: a `vX.Y.Z` tag runs all tests and publishes the images to GHCR | The server only downloads, never builds (done, PR #40) |
| ☐ 2 | `.env.prod.example` (Brevo, Turnstile and SMS settings pre-filled) + `scripts/prod-secrets.sh` (fills the passwords and keys with `openssl`, never prints them; no extra software on the server) | One file to fill on the server |
| ☐ 3 | Sizing for a 4 GB server: memory limits per container, smaller Java heap, 2 GB swap note | So the database and the API never starve each other |
| ☐ 4 | First admin in production: pass `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` in `docker-compose.prod.yml` | Today the production stack has no way to create the admin |
| ☐ 5 | `scripts/backup.sh`: nightly database dump, keep 14 on the server, copy to Backblaze B2 | Off-site copy in case the server is lost |
| ☐ 6 | SMS adapter for your provider (**only when you give the go-ahead** and the API docs); Telnyx code stays | Real SMS to owners |

Until task 6 is done the site works with `CONTACT_PROVIDER=mock` (messages are saved and shown in the
owner dashboard, but no SMS is sent).

## 5. Going live, step by step

### 5.1 Create the server (10 minutes)
- Hetzner console → New server: **CX22**, **Ubuntu 24.04**, location Nuremberg or Falkenstein,
  your SSH key, **enable Backups**.
- Hetzner console → Firewalls: allow inbound **22, 80, 443** only; attach it to the server.

### 5.2 Point the domain (5 minutes)
- Cloudflare DNS: `A` record `@` (and `www` if wanted) → the server's IP, **DNS only** (grey cloud),
  so Caddy can obtain the certificate.

### 5.3 Prepare the server (15 minutes)
```bash
ssh root@<server-ip>
apt update && apt -y upgrade && apt -y install unattended-upgrades git
curl -fsSL https://get.docker.com | sh                       # Docker + Compose
fallocate -l 2G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
git clone https://github.com/omarkdhai/CarLink.git /opt/carlink
```

### 5.4 Configure (15 minutes)
```bash
cd /opt/carlink
cp .env.prod.example .env.prod && sh scripts/prod-secrets.sh .env.prod   # (code task 2)
nano .env.prod     # domain, e-mail, Brevo SMTP, Turnstile keys, CARLINK_VERSION, first admin
chmod 600 .env.prod
```
**Copy `PHONE_ENCRYPTION_KEY` into your password manager now.** Without it the saved phone numbers
cannot be read back, and it must not live only on the server or only inside the backups.

### 5.5 Release and start (10 minutes)
On your PC: `git tag v1.0.0 && git push origin v1.0.0` → wait for the green "Release" run on GitHub.
In GitHub → Packages, set `carlink-backend` and `carlink-frontend` to **public** (once).

On the server:
```bash
cd /opt/carlink
docker compose -f docker-compose.prod.yml --env-file .env.prod pull --ignore-buildable
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --no-build
docker compose -f docker-compose.prod.yml --env-file .env.prod ps     # all "healthy"
```

### 5.6 Check (15 minutes)
- `https://<domain>` opens, the padlock is valid, `http://` redirects to `https://`.
- Register an owner account → the verification e-mail arrives → log in.
- Log in as admin → **change the admin password**, then remove `ADMIN_BOOTSTRAP_PASSWORD` from
  `.env.prod` and run the `up -d --no-build` command again.
- Order a sticker, activate it, scan it with a phone, send a message → it appears in the dashboard.
- UptimeRobot: add an HTTPS monitor on `https://<domain>/actuator/health`.
- Backups: enable the nightly cron from code task 5 and run it once by hand.

**You are live.**

## 6. Everyday operation

| Task | How |
|---|---|
| Release an update | Merge to `master` → `git tag v1.0.1 && git push origin v1.0.1` → on the server set `CARLINK_VERSION=1.0.1` in `.env.prod`, then `git pull`, `pull --ignore-buildable`, `up -d --no-build` |
| Roll back | Set `CARLINK_VERSION` back to the previous version, `pull`, `up -d --no-build` (if the update changed the database, restore the dump taken before it — [deployment.md §8–9](deployment.md)) |
| Look at errors | `docker compose -f docker-compose.prod.yml logs --tail 200 backend` |
| Server updates | Automatic (unattended-upgrades); reboot from the Hetzner console once a month |
| Check backups | Once a month: restore last night's dump into a test container on your PC |

## 7. When to grow

Add things only when one of these happens:

| Signal | Then |
|---|---|
| > 1,000 users, or the server is often above 70 % memory | Resize to CX32 (8 GB, ~8 €/month) in the Hetzner console — 2 minutes, same disk |
| Updates start breaking things for users | Add a second small server as **staging** (~5 €/month) |
| You need to know about problems before users report them | Turn on the Prometheus alert rules already in `monitoring/prometheus/` |
| Real customers or partners ask for proof of security | Book the external penetration test (scope in [pentest-2026-11-19.md](pentest-2026-11-19.md)) |
| Company registered | Move to the `.tn` domain |
| Users ask to pay online | Add Konnect or Flouci next to cash on delivery |

## 8. Timeline

| When | What |
|---|---|
| Week 1 | You: domain, Hetzner, Brevo, Turnstile, lawyer question · Me: code tasks 2–5 |
| Week 2 | Server up (§5), test everything with friends using `CONTACT_PROVIDER=mock` |
| When the SMS contract is signed | Code task 6, switch to real SMS, **public launch** |
