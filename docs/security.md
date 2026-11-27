# CarLink Security Model

## Threat model

Assets to protect:

1. **Owner phone numbers** — the crown jewel. Never leaves the server except back to the owner themselves (see the narrow exception below).
2. **Account credentials** — hashed, never logged.
3. **QR tokens** — the public interface; must be unguessable and rate-limited.
4. **Message content** — the payload of an SMS relay.

Primary threats:

| Threat | Mitigation |
|--------|-----------|
| QR token enumeration | 96-bit CSPRNG tokens (12 bytes, 16 chars; older 32-byte tokens stay valid); SHA-256 stored only; with the per-IP rate limit, guessing a live token is out of reach |
| Phone number leakage | Phone serialized only to the owner who holds the account JWT (login / refresh / `/users/me`); never in QR/URL/HTML/logs or any third-party/public DTO |
| Spam / abuse on `/c/{token}` | Redis rate limit per IP + per token, CAPTCHA abstraction, message length cap, links removed, off-topic notes refused (`NOTE_OFF_TOPIC`, deployment.md §14) |
| Credential stuffing | BCrypt(12), account lockout/backoff, generic error messages |
| Token theft | Short-lived access JWT; refresh token rotation + revocation; hashed at rest |
| Enumeration via registration | 400 `EMAIL_ALREADY_REGISTERED` with a clear "already registered" message — the 400 status already leaked existence under the old generic-message design; this trades enumeration theatre for honest UX |
| JWT secret leakage | Secret injected via env, never in source |

## Owner-only profile exception (phone)

The account's own phone number IS returned to the authenticated owner in
`UserResponse` (login, refresh, `GET /users/me`, and the register response —
which only echoes back the number the caller just submitted). This is how the
owner verifies the SMS / anonymous-call relay number in their profile. It is a
**narrow** exception: the phone is never in any third-party view, public scan
response, HTML, QR, URL, Redis/DB export, or log. Admin DTOs (`AdminUserResponse`)
still omit it.

## Logging rules

- **Never** log phone numbers, raw QR tokens, passwords, or message bodies.
- Raw QR / sticker tokens never travel in an API path: clients send them in
  the `X-CarLink-Token` header (`GET /api/v1/public/qr`,
  `POST /api/v1/public/qr/contact`,
  `GET /api/v1/public/qr/contact/{conversationId}/status`,
  `POST /api/v1/stickers/activate`). The status endpoint answers only for an
  alert of the vehicle behind that token (else 404), returns one of
  DELIVERED / SENT / FAILED — never the failure reason, which could reveal
  something about the owner's number (the one exception, REFUSED, says only
  that the operator refused the note's content, so the visitor changes it) —
  and is rate-limited per hashed IP. The
  only URL carrying a token is the QR's own `/c/{token}` page; nginx's
  `carlink` log format masks it in the request line *and* in the Referer of
  the API calls made from that page (security plan S-08).
- WhatsApp (docs/whatsapp.md): the owner's number goes to Meta only in the JSON
  body of the send, never in a URL or a log; Meta's replies and webhook calls
  carry it (`wa_id`, `recipient_id`) and are never logged or stored. Meta's
  message id (`wamid.…`) is Base64 that embeds the recipient's number, so it is
  never logged and only its SHA-256 is stored (`WhatsAppMessageIds`, migration
  V14); the webhook fingerprints the id it receives before matching. The
  webhook `POST /api/v1/public/webhooks/whatsapp` reads only calls signed with
  the app secret (`X-Hub-Signature-256`, constant-time check; else 401), and
  logs counts and error codes only. Messages owners send to the CarLink number
  are ignored. The owner's WhatsApp opt-out is never revealed to visitors: the
  public page lists the configured channels, not the owner's.
- IP addresses are logged only for rate-limit/audit purposes.
- Error responses never include stack traces or internal identifiers.
- The order-confirmation email is the one place customer PII (name, phone,
  address, notes) leaves the system, and it goes only to the address the buyer
  supplied at checkout. The `mock` dev sender logs only the subject and body
  length — never the HTML — and a delivery failure logs just the order
  reference and exception type, so a broken SMTP setup cannot dump a customer's
  address into the log file. Raw QR/sticker tokens are never included in the
  email or in the checkout response: only an admin reads them (see below).

## JWT lifecycle

- Access token TTL: configurable, default **15 minutes**.
- Admin access tokens live **5 minutes** (`JWT_ADMIN_ACCESS_EXPIRATION_MINUTES`).
- Every access token carries the account's token version (`tv`). The JWT filter checks it, together with
  `active` and the role, against the live account (fresh from the database on `/api/v1/admin/**`, cached
  for 30 s elsewhere and evicted on change). Deactivation, a role change and a password reset bump the
  version and revoke refresh tokens, so every session ends at once. The role used for authorisation is
  the database's, not the token's (security plan S-10).
- Refresh token TTL: configurable, default **7 days**, rotation on use.
- On refresh, the old refresh token is revoked and a new one issued. Presenting an already-used token
  revokes every session of that user (reuse detection).
- The refresh token is never in a JSON body or in browser storage: it travels only in the
  `carlink_rt` cookie — `HttpOnly; SameSite=Strict; Path=/api/v1/auth`, plus `Secure` outside the dev
  profile (`carlink.auth.refresh-cookie-secure`). The SPA keeps the access token in memory only.
- CSRF: `/auth/refresh` and `/auth/logout` (the only cookie-authenticated endpoints) also require the
  `X-Requested-With` header, which a cross-site form cannot send (security plan S-09 / S-24).

## Rate limiting (public endpoint)

Defaults applied from `carlink.ratelimit`:

- **5 requests/minute / IP**
- **20 requests/hour / QR token**

Both are Redis-backed, configurable via env, and return `429 Too Many Requests`
with a `Retry-After` header.

## Data at rest

- Passwords: BCrypt strength 12.
- Distributed guessing (S-11): failed logins are also counted per account across all IPs; at 10 in
  15 minutes that account's login requires a Turnstile token (`CAPTCHA_REQUIRED`), or, with Turnstile
  off, one attempt per 30 s (`LOGIN_SLOWED_DOWN`). It is never a lock, so nobody can shut an owner out,
  and a successful sign-in clears it. Guest orders are capped at 3 per e-mail and 3 per mobile per day.
- Owner phone numbers are encrypted at rest (AES-256-GCM, `PHONE_ENCRYPTION_KEY`, `v1:` prefix,
  fresh IV per value); the entity holds the plain number for the relay only (security plan S-17).
- Retention (S-17, `RetentionService`, daily): conversations and their messages are deleted after
  90 days (kept while an abuse report on them is OPEN); expired/revoked/used tokens after 7 days;
  IP addresses and user agents in audit logs and reports are erased after 90 days; public contact
  messages are deleted after 365 days. Guest orders (name, mobile, address) are kept as business
  records; they are not linked to accounts.
- Account deletion (S-17): `DELETE /api/v1/users/me` with the current password releases the
  owner's stickers (claimable again, owner cleared) and hard-deletes the account; the database
  cascades vehicles, QR codes, conversations, messages and tokens. Live sessions end at once.
  Only an `ACCOUNT_DELETED` audit entry with the id remains. Admins cannot self-delete.
- Password policy (security plan S-14, NIST SP 800-63B): at least 10 characters and at most 72 bytes
  (BCrypt's limit), any characters — passphrases welcome, no composition rule. Refused: common
  passwords (`security/common-passwords.txt`), one or two repeated characters, keyboard/digit runs,
  passwords built on the e-mail name, and — in prod — passwords found by the Have I Been Pwned range
  API (k-anonymity, fails open). Each refusal has its own code (`PASSWORD_TOO_SHORT`, `_TOO_LONG`,
  `_TOO_COMMON`, `PASSWORD_BREACHED`). Login input is capped, and a > 72-byte login is a plain 401.
  Existing passwords are not forced to change; the rule applies whenever one is chosen.
- Qr / refresh / reset / verification tokens: SHA-256 **hashed**.
- Sticker tokens and activation codes: looked up by SHA-256 hash; the raw values are also kept
  AES-256-GCM encrypted (`token_enc`, `activation_code_enc`, same key as phone numbers) so an admin
  can print them. Never returned to the buyer.
- Licence plates are stored but never returned in public payloads.
- Owner purchase history (`GET /api/v1/orders/me`, authenticated): an order is shown to an account
  only when it was placed with the account's e-mail **once that e-mail is verified** (so nobody can
  claim someone else's orders by registering with their address), or when it contains a sticker the
  account owns. The response carries reference, status, items, totals and activation counts only —
  never buyer name, mobile, address, notes, tokens or activation codes.

## Sticker codes: admin-only exception

The buyer never receives a sticker's QR token or activation code: the checkout response
(`POST /api/v1/public/orders`) carries only the reference, status, items and total. The codes are
printed on the physical stickers and reach the owner with the delivery.

To print them, `GET /api/v1/admin/orders/{reference}/stickers` (`ROLE_ADMIN` only) decrypts each
sticker's token and activation code and returns its public URL, QR image and code. This is a
deliberate exception to "raw tokens are never returned":

- the raw values are stored only AES-256-GCM encrypted (`PHONE_ENCRYPTION_KEY`), so a database dump
  without the key reveals nothing; the SHA-256 columns stay the lookup keys;
- every read writes an `ORDER_STICKER_CODES_VIEW` audit entry (actor, IP, reference, count — never a
  code);
- the values are never logged.

Stickers sold before migration V15 have only hashes; their codes cannot be recovered and the admin
page marks them "codes not available".

## Message content exceptions

Message content is returned in exactly three places, all authenticated:

- `GET /api/v1/admin/reports/{id}` — `conversation.lastMessageContent` for a
  `ROLE_ADMIN` moderator.
- `GET /api/v1/conversations/{id}` — full history for the **owning owner** (not
  an admin capability; ownership is enforced, mismatches 404).
- `GET /api/v1/conversations` — the owner's dashboard list, which includes an
  80-character `lastMessagePreview` snippet.

`lastMessagePreview` exists **only** in `ConversationSummaryResponse`, which is
served exclusively by the owner-facing authenticated endpoint. It is not part of
any public scan, contact-submission, or order response.

Everywhere else, unauthenticated callers receive identifiers, channel, status
and timestamps — never message text.

## Audit trail

All admin mutations (user activation/deactivation, role changes, report status transitions) are logged to `audit_logs` with:

- Action type (`USER_ACTIVATE`, `USER_DEACTIVATE`, `USER_ROLE_CHANGE`, `REPORT_STATUS_CHANGE`)
- Entity type and ID
- Actor admin ID, IP address, User-Agent
- Details as JSON (`{"from":"X","to":"Y"}` for role/status changes)

The audit service runs in `REQUIRES_NEW` so a logging failure never rolls back the primary action.