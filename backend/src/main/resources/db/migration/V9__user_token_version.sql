-- Security plan S-10: access tokens carry the user's token version ("tv" claim).
-- Bumping it (deactivation, role change, password reset) invalidates every
-- access token already issued, instead of letting them live until expiry.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
