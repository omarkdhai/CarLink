/**
 * Client mirror of the backend PasswordPolicy (security plan S-14), for
 * instant feedback only — the server stays the source of truth and also
 * refuses common and breached passwords, which are not checked here.
 *
 * NIST SP 800-63B style: length over composition. No "letter + digit" rule,
 * so passphrases ("ma voiture est bleue") are welcome.
 */

export const PASSWORD_MIN_LENGTH = 10
/** BCrypt only reads 72 bytes; the server refuses anything longer. */
export const PASSWORD_MAX_BYTES = 72

const RUNS = ['01234567890123', 'abcdefghijklmnopqrstuvwxyz', 'qwertyuiopasdfghjklzxcvbnm', 'azertyuiopqsdfghjklmwxcvbn']

export function passwordLength(value: string): number {
  return Array.from(value).length
}

export function passwordBytes(value: string): number {
  return new TextEncoder().encode(value).length
}

/** Repeated characters, keyboard/digit runs. Mirrors PasswordPolicy.isGuessable minus the lists. */
export function isSimplePattern(value: string): boolean {
  const compact = value.toLowerCase().replace(/\s+/g, '')
  if (compact.length === 0) return false
  if (new Set(compact).size <= 2) return true
  return RUNS.some((run) => run.includes(compact) || [...run].reverse().join('').includes(compact))
}
