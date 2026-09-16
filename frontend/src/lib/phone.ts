/**
 * Phone number helpers.
 *
 * Registration and the profile form split the number into a country code
 * (defaulting to +216) and the national number; the API always receives a
 * single E.164 string. These helpers compose/parse that pair.
 */

/** Default country code prefix shown in the phone fields. */
export const DEFAULT_PHONE_CODE = '+216'

/** E.164: country code + up to 14 national digits. */
const E164 = /^\+[1-9][0-9]{6,14}$/

/**
 * Join a country code and national number into a full E.164 phone.
 * Any whitespace a user typed (e.g. "22 100 122") is stripped so the result
 * passes backend E.164 validation.
 */
export function joinPhone(code: string | undefined, national: string | undefined): string {
  const c = (code ?? '').replace(/\s+/g, '')
  const n = (national ?? '').replace(/\s+/g, '')
  return c && n ? `${c}${n}` : ''
}

/**
 * Split a stored E.164 number (e.g. "+21622100122") back into its country
 * code and national part.
 *
 * The configured default code (+216) is preferred when it is a prefix: a
 * greedy generic country-code match can otherwise steal one digit of a
 * Tunisian national number (e.g. "+2162"/"2100122"). Any other number falls
 * back to a 1-3 digit country-code match and keeps the rest as national —
 * best-effort for non-Tunisian codes. Returns the default code + empty
 * national when the input is absent or malformed.
 */
export function splitPhone(phone?: string | null): { code: string; national: string } {
  if (!phone) return { code: DEFAULT_PHONE_CODE, national: '' }
  const trimmed = phone.trim()
  if (trimmed.startsWith(DEFAULT_PHONE_CODE)) {
    const national = trimmed.slice(DEFAULT_PHONE_CODE.length)
    if (/^\d{6,14}$/.test(national)) return { code: DEFAULT_PHONE_CODE, national }
  }
  const match = trimmed.match(/^(\+[1-9][0-9]{0,2})(\d{6,14})$/)
  if (match) return { code: match[1], national: match[2] }
  return E164.test(trimmed) ? { code: '', national: trimmed } : { code: DEFAULT_PHONE_CODE, national: '' }
}