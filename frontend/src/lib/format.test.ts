import { formatRelative, formatDateTime } from './format'

describe('formatRelative', () => {
  it('returns "now" for timestamps less than 60s ago', () => {
    const now = new Date()
    expect(formatRelative(now.toISOString())).toBe('now')
  })

  it('returns minutes for timestamps a few minutes ago', () => {
    const d = new Date(Date.now() - 5 * 60 * 1000)
    expect(formatRelative(d.toISOString())).toBe('5 minutes ago')
  })

  it('returns hours for timestamps a few hours ago', () => {
    const d = new Date(Date.now() - 3 * 60 * 60 * 1000)
    expect(formatRelative(d.toISOString())).toBe('3 hours ago')
  })

  it('returns days for timestamps a few days ago', () => {
    const d = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000)
    expect(formatRelative(d.toISOString())).toBe('2 days ago')
  })
})

describe('formatDateTime', () => {
  it('formats ISO date string to locale date', () => {
    const result = formatDateTime('2024-06-15T10:30:00Z')
    expect(typeof result).toBe('string')
    expect(result.length).toBeGreaterThan(0)
  })
})
