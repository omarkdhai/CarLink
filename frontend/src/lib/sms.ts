/**
 * The owner's SMS alert is always in Tunisian Derja (backend:
 * notification/sms/AlertSms.java): the reason line, then the visitor's note
 * exactly as typed. Arabic makes it a Unicode SMS — 70 characters in all — so
 * the backend sends, per reason, how many characters are left for the note.
 *
 * Characters are counted like the network does: UTF-16 units, so an emoji
 * takes 2.
 */

export interface NoteFit {
  /** Characters the note uses. */
  used: number
  /** Characters available for the note in the SMS. */
  max: number
  /** Longer than the SMS can carry: it will be shortened ("..."). */
  over: boolean
}

export function fitNote(note: string, max: number): NoteFit {
  const used = note.trim().length
  return { used, max, over: used > max }
}
