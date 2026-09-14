# CarLink Security Model

## Threat model

Assets to protect:

1. **Owner phone numbers** — the crown jewel. Never leaves the server.
2. **Account credentials** — hashed, never logged.
3. **QR tokens** — the public interface; must be unguessable and rate-limited.
4. **Message content** — the payload of an SMS/WhatsApp relay.

Primary threats:

| Threat | Mitigation |
|--------|-----------|
| QR token enumeration | 32-byte CSPRNG tokens; SHA-256 stored only |
| Phone number leakage | Phone filtered from every DTO, never added to QR/URL/HTML/logs |
| Spam / abuse on `/c/{token}` | Redis rate limit per IP + per token, CAPTCHA abstraction, message length cap |
| Credential stuffing | BCrypt(12), account lockout/backoff, generic error messages |
| Token theft | Short-lived access JWT; refresh token rotation + revocation; hashed at rest |
| Enumeration via registration | Generic "we sent you a link" response |
| JWT secret leakage | Secret injected via env, never in source |

## Logging rules

- **Never** log phone numbers, raw QR tokens, passwords, or message bodies.
- IP addresses are logged only for rate-limit/audit purposes.
- Error responses never include stack traces or internal identifiers.

## JWT lifecycle

- Access token TTL: configurable, default **15 minutes**.
- Refresh token TTL: configurable, default **7 days**, rotation on use.
- On refresh, the old refresh token is revoked and a new one issued.

## Rate limiting (public endpoint)

Defaults applied from `carlink.ratelimit`:

- **5 requests/minute / IP**
- **20 requests/hour / QR token**

Both are Redis-backed, configurable via env, and return `429 Too Many Requests`
with a `Retry-After` header.

## Data at rest

- Passwords: BCrypt strength 12.
- Qr / refresh / reset / verification tokens: SHA-256 **hashed**.
- Licence plates are stored but never returned in public payloads.

## Admin-only data access

The following endpoints return message content only to authenticated users with `ROLE_ADMIN`:

- `GET /api/v1/admin/reports/{id}` — returns `conversation.lastMessageContent` for moderation judgment.
- `GET /api/v1/conversations/{id}` — returns full message history for the owning owner.

Both are the only exceptions to "message content never returned by public/unauthenticated APIs". Regular visitors and unauthenticated callers receive only metadata (conversation ID, channel, timestamp, unread flag, truncated preview).

## Audit trail

All admin mutations (user activation/deactivation, role changes, report status transitions) are logged to `audit_logs` with:

- Action type (`USER_ACTIVATE`, `USER_DEACTIVATE`, `USER_ROLE_CHANGE`, `REPORT_STATUS_CHANGE`)
- Entity type and ID
- Actor admin ID, IP address, User-Agent
- Details as JSON (`{"from":"X","to":"Y"}` for role/status changes)

The audit service runs in `REQUIRES_NEW` so a logging failure never rolls back the primary action.