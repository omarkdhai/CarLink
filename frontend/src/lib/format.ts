/** Localized relative time ("3 min ago") for recent timestamps. */
export function formatRelative(iso: string): string {
  const then = new Date(iso).getTime()
  const diff = Date.now() - then
  const min = 60_000
  const hour = 3_600_000
  const day = 86_400_000
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' })

  if (diff < min) return rtf.format(-Math.round(diff / 1000), 'second')
  if (diff < hour) return rtf.format(-Math.round(diff / min), 'minute')
  if (diff < day) return rtf.format(-Math.round(diff / hour), 'hour')
  if (diff < 7 * day) return rtf.format(-Math.round(diff / day), 'day')
  return new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short' }).format(new Date(iso))
}

/** Full localized date + time. */
export function formatDateTime(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}
