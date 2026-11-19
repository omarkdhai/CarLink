#!/usr/bin/env node
// Creates or repairs the local .env used by docker-compose.yml (security plan S-01).
//
//   node scripts/dev-secrets.mjs
//
// - Copies .env.example to .env when .env does not exist.
// - Replaces every secret that is empty, a placeholder, or a value that has ever
//   been committed to this repository with a strong random value.
// - Never prints a secret: it only reports which keys it generated.
//
// Run it once after cloning, and again whenever you want fresh secrets. If the
// Postgres volume already exists, a new POSTGRES_PASSWORD must also be applied
// inside the database (see docs/deployment.md, "Rotating local secrets").
import { randomBytes } from 'node:crypto'
import { constants, readFileSync, writeFileSync, copyFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const envPath = join(root, '.env')
const examplePath = join(root, '.env.example')

/** Values that are public (committed now or in history) and must never be used. */
const PUBLIC_VALUES = new Set([
  'dev-only-change-me-in-production-please-32chars',
  'dev-only-refresh-secret-please-change-32chars',
  'replace_with_a_strong_random_secret_at_least_32_chars',
  'replace_with_another_strong_random_secret_32_chars',
  'carlink_dev_password',
  'change_this_dev_password',
  'carlink_redis_password',
  'change_this_redis_password',
  'ChangeMe123!',
  'change_me',
])

// hex keeps the values shell-, YAML- and SQL-safe (no quoting needed anywhere).
const hex = (bytes) => randomBytes(bytes).toString('hex')

/** Key → generator. Only these keys are ever touched. */
const SECRETS = {
  JWT_SECRET: () => hex(48),
  POSTGRES_PASSWORD: () => hex(24),
  REDIS_PASSWORD: () => hex(24),
  // Meets the password rule (letters + digits); used only to create the first
  // admin when none exists.
  ADMIN_BOOTSTRAP_PASSWORD: () => `Adm${hex(16)}9`,
}
const DEFAULTS = { ADMIN_BOOTSTRAP_EMAIL: 'admin@carlink.local' }

// Create .env from the example only if it does not exist, atomically
// (COPYFILE_EXCL), instead of check-then-copy, which can race (CodeQL
// js/file-system-race). The file is then read once and written once.
try {
  copyFileSync(examplePath, envPath, constants.COPYFILE_EXCL)
  console.log('Created .env from .env.example')
} catch (e) {
  if (e.code !== 'EEXIST') throw e
}

const original = readFileSync(envPath, 'utf8')
const eol = original.includes('\r\n') ? '\r\n' : '\n'
const lines = original.split(/\r?\n/)
const seen = new Set()
const generated = []

const out = lines.map((line) => {
  const m = /^([A-Z0-9_]+)=(.*)$/.exec(line)
  if (!m) return line
  const [, key, value] = m
  seen.add(key)
  if (key in SECRETS && (value.trim() === '' || PUBLIC_VALUES.has(value.trim()))) {
    generated.push(key)
    return `${key}=${SECRETS[key]()}`
  }
  return line
})

for (const key of Object.keys(SECRETS)) {
  if (!seen.has(key)) {
    out.push(`${key}=${SECRETS[key]()}`)
    generated.push(key)
  }
}
for (const [key, value] of Object.entries(DEFAULTS)) {
  if (!seen.has(key)) out.push(`${key}=${value}`)
}

writeFileSync(envPath, out.join(eol))
console.log(generated.length ? `Generated new values for: ${generated.join(', ')}` : 'All secrets already set; nothing changed.')
if (generated.includes('POSTGRES_PASSWORD')) {
  console.log('Note: an existing Postgres volume keeps its old password. Apply the new one inside the database')
  console.log('      (docs/deployment.md, §15 "Rotating local secrets") or start from an empty volume.')
}
