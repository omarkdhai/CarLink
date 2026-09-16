import { describe, expect, it } from 'vitest'
import { DEFAULT_PHONE_CODE, joinPhone, splitPhone } from './phone'

describe('joinPhone', () => {
  it('combines code and national number into E.164', () => {
    expect(joinPhone('+216', '22100122')).toBe('+21622100122')
  })

  it('trims whitespace', () => {
    expect(joinPhone(' +216 ', ' 22 100 122 ')).toBe('+21622100122')
  })

  it('returns empty string if either part is missing', () => {
    expect(joinPhone('', '22100122')).toBe('')
    expect(joinPhone('+216', '')).toBe('')
    expect(joinPhone('+216', undefined)).toBe('')
    expect(joinPhone(undefined, undefined)).toBe('')
  })
})

describe('splitPhone', () => {
  it('splits a stored E.164 number', () => {
    expect(splitPhone('+21622100122')).toEqual({ code: '+216', national: '22100122' })
  })

  it('splits non-Tunisian codes best-effort', () => {
    // UAE (+971) is exactly 3 digits, so a generic match is unambiguous.
    expect(splitPhone('+971501234567')).toEqual({ code: '+971', national: '501234567' })
  })

  it('falls back to defaults when absent', () => {
    expect(splitPhone('')).toEqual({ code: DEFAULT_PHONE_CODE, national: '' })
    expect(splitPhone(null)).toEqual({ code: DEFAULT_PHONE_CODE, national: '' })
    expect(splitPhone(undefined)).toEqual({ code: DEFAULT_PHONE_CODE, national: '' })
  })
})

describe('round-trip', () => {
  it('join(split(phone)) is idempotent', () => {
    for (const phone of ['+21622100122', '+33612345678', '+14155552671']) {
      const { code, national } = splitPhone(phone)
      expect(joinPhone(code, national)).toBe(phone)
    }
  })
})