import { describe, expect, it } from 'vitest'
import { fitNote } from './sms'

// Same counting as AlertSmsTest on the backend (UTF-16 units, emoji = 2).
describe('fitNote', () => {
  it('counts a note in any language against the room left in the SMS', () => {
    expect(fitNote(' Je suis au café ', 49)).toEqual({ used: 15, max: 49, over: false })
    expect(fitNote('نستنى فيك', 49)).toEqual({ used: 9, max: 49, over: false })
  })

  it('counts an emoji as two characters', () => {
    expect(fitNote('ok 👍', 4)).toEqual({ used: 5, max: 4, over: true })
  })

  it('flags a note longer than the room left', () => {
    expect(fitNote('a'.repeat(36), 35).over).toBe(true)
    expect(fitNote('a'.repeat(35), 35).over).toBe(false)
  })
})
