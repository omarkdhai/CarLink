#!/bin/sh
# Fills the empty secrets of a production env file with strong random values.
#
#   sh scripts/prod-secrets.sh .env.prod
#
# - Only touches a secret whose line is empty ("KEY=" with nothing after it):
#   an existing value is NEVER replaced. That matters most for
#   PHONE_ENCRYPTION_KEY, which cannot change on a live database.
# - Never prints a value: it only reports which keys it generated.
# - Needs nothing but /bin/sh and openssl (present on Ubuntu).
set -eu

file="${1:-.env.prod}"
if [ ! -f "$file" ]; then
  echo "error: $file not found (cp .env.prod.example $file first)" >&2
  exit 1
fi
command -v openssl >/dev/null 2>&1 || { echo "error: openssl is required" >&2; exit 1; }

# KEY:bytes — hex output keeps every value shell-, YAML- and URL-safe.
# JWT_SECRET 48 bytes (backend requires >= 32); PHONE_ENCRYPTION_KEY exactly
# 32 bytes = 64 hex chars (AES-256); admin password 24 hex chars (>= 12).
secrets="POSTGRES_PASSWORD:24 REDIS_PASSWORD:24 JWT_SECRET:48 PHONE_ENCRYPTION_KEY:32 METRICS_SCRAPE_TOKEN:32 ADMIN_BOOTSTRAP_PASSWORD:12"

umask 077
generated=""
for entry in $secrets; do
  key="${entry%%:*}"
  bytes="${entry#*:}"
  # Empty value, optionally followed by spaces / a comment.
  if grep -Eq "^${key}=[[:space:]]*(#.*)?$" "$file"; then
    value="$(openssl rand -hex "$bytes")"
    tmp="$(mktemp "${file}.XXXXXX")"
    sed -E "s|^${key}=[[:space:]]*(#.*)?$|${key}=${value}|" "$file" > "$tmp"
    mv "$tmp" "$file"
    generated="$generated $key"
  elif ! grep -Eq "^${key}=" "$file"; then
    echo "warning: $key is not in $file; add it from .env.prod.example" >&2
  fi
done
chmod 600 "$file"

if [ -n "$generated" ]; then
  echo "generated:$generated"
else
  echo "nothing to generate: every secret already has a value"
fi

# Settings only a person can provide (the lines under "# FILL"). Names only.
missing=""
for key in CARLINK_VERSION CARLINK_DOMAIN ACME_EMAIL ADMIN_BOOTSTRAP_EMAIL MAIL_USERNAME \
           MAIL_PASSWORD CONTACT_TO_EMAIL CAPTCHA_SITE_KEY CAPTCHA_SECRET_KEY \
           TUNISIESMS_API_KEY; do
  if grep -Eq "^${key}=[[:space:]]*$" "$file"; then
    missing="$missing $key"
  fi
done
if [ -n "$missing" ]; then
  echo "still to fill in $file:$missing"
else
  echo "all required settings have a value"
fi
echo "Copy PHONE_ENCRYPTION_KEY to your password manager before the first start."
