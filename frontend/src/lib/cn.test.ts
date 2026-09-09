import { cn } from './cn'

describe('cn', () => {
  it('concatenates class names', () => {
    expect(cn('text-red-500', 'text-blue-500')).toBe('text-red-500 text-blue-500')
  })

  it('handles conditional classes', () => {
    expect(cn('base', false && 'hidden', 'extra')).toBe('base extra')
  })

  it('returns empty string for no input', () => {
    expect(cn()).toBe('')
  })

  it('filters falsy values', () => {
    expect(cn('a', null, undefined, '', false, 'b')).toBe('a b')
  })
})
