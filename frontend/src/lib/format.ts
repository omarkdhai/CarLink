import i18n from '@/lib/i18n'

/** The UI language (not the browser's), so dates follow the language switcher. */
function locale(): string {
  return i18n.language || 'fr'
}

/** Localized relative time ("il y a 3 min") for recent timestamps. */
export function formatRelative(iso: string): string {
  const then = new Date(iso).getTime()
  const diff = Date.now() - then
  const min = 60_000
  const hour = 3_600_000
  const day = 86_400_000
  const rtf = new Intl.RelativeTimeFormat(locale(), { numeric: 'auto' })

  if (diff < min) return rtf.format(0, 'second')
  if (diff < hour) return rtf.format(-Math.round(diff / min), 'minute')
  if (diff < day) return rtf.format(-Math.round(diff / hour), 'hour')
  if (diff < 7 * day) return rtf.format(-Math.round(diff / day), 'day')
  return new Intl.DateTimeFormat(locale(), { day: 'numeric', month: 'short' }).format(new Date(iso))
}

/** Full localized date + time. */
export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

/** Localized date only ("16 nov. 2026"). */
export function formatDate(iso: string): string {
  return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium' }).format(new Date(iso))
}

/** True when the timestamp falls on the user's current calendar day. */
export function isToday(iso: string): boolean {
  return new Date(iso).toDateString() === new Date().toDateString()
}
