import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowUpDown, CheckCircle2, ChevronLeft, ChevronRight, SearchX, Search, X } from 'lucide-react'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/cn'

/*
 * Shared building blocks for the admin list pages (users, vehicles, QR codes,
 * orders, contacts): URL-backed filters, a debounced search toolbar, status
 * tabs, a table that becomes a card list on small screens, pagination, empty
 * results, a detail dialog and a success toast. Colours come from the theme
 * tokens; headings use the display font (Bricolage Grotesque) via h1/h2.
 */

// ---------- state ----------

/**
 * List filters live in the URL (`?q=&status=&sort=&page=`) so views can be
 * shared, deep-linked from the dashboard and survive a reload. The search box
 * updates instantly; the `q` param is written after a 300 ms pause.
 */
export function useListParams(defaults: { sort?: string } = {}) {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const status = params.get('status') ?? ''
  const sort = params.get('sort') ?? defaults.sort ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0)
  const [input, setInput] = useState(q)

  const update = useCallback(
    (patch: Record<string, string | number | null>) =>
      setParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          for (const [k, v] of Object.entries(patch)) {
            if (v === null || v === '' || (k === 'page' && v === 0)) next.delete(k)
            else next.set(k, String(v))
          }
          return next
        },
        { replace: true },
      ),
    [setParams],
  )

  // Debounce the search box into the URL.
  useEffect(() => {
    if (input.trim() === q) return
    const id = window.setTimeout(() => update({ q: input.trim(), page: 0 }), 300)
    return () => window.clearTimeout(id)
  }, [input, q, update])

  // Keep the box in sync when the URL changes from outside (back button, links).
  useEffect(() => {
    setInput((current) => (current.trim() === q ? current : q))
  }, [q])

  return {
    q,
    input,
    setInput,
    status,
    setStatus: (v: string) => update({ status: v, page: 0 }),
    sort,
    setSort: (v: string) => update({ sort: v === defaults.sort ? null : v, page: 0 }),
    page,
    setPage: (p: number) => update({ page: p }),
    hasFilters: Boolean(q || status),
    clear: () => {
      setInput('')
      update({ q: null, status: null, page: 0 })
    },
  }
}

// ---------- toolbar ----------

export function SearchBox({
  value,
  onChange,
  placeholder,
}: {
  value: string
  onChange: (v: string) => void
  placeholder: string
}) {
  const { t } = useTranslation()
  return (
    <div className="relative w-full sm:max-w-sm">
      <Search className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-fg" aria-hidden />
      <input
        type="search"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="w-full h-11 rounded-lg border border-input-border bg-surface ps-9 pe-10 text-sm placeholder:text-muted-fg focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent [&::-webkit-search-cancel-button]:hidden"
      />
      {value && (
        <button
          type="button"
          onClick={() => onChange('')}
          aria-label={t('admin.list.clearSearch')}
          className="absolute end-1 top-1/2 -translate-y-1/2 h-9 w-9 rounded-md flex items-center justify-center text-muted-fg hover:text-foreground hover:bg-muted"
        >
          <X className="h-4 w-4" aria-hidden />
        </button>
      )}
    </div>
  )
}

export function SortSelect({
  value,
  onChange,
  options,
}: {
  value: string
  onChange: (v: string) => void
  options: { value: string; label: string }[]
}) {
  const { t } = useTranslation()
  return (
    <label className="relative inline-flex items-center">
      <span className="sr-only">{t('admin.list.sortBy')}</span>
      <ArrowUpDown className="pointer-events-none absolute start-3 h-4 w-4 text-muted-fg" aria-hidden />
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-11 rounded-lg border border-input-border bg-surface ps-9 pe-8 text-sm font-medium text-foreground hover:border-primary/50 focus:outline-none focus:ring-2 focus:ring-primary"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  )
}

export interface TabOption {
  value: string
  label: string
  count?: number
  /** Dot colour class (e.g. `bg-success`) shown before the label. */
  dot?: string
}

/** Segmented status filter with optional counts; scrolls horizontally on phones. */
export function StatusTabs({
  value,
  onChange,
  options,
  label,
}: {
  value: string
  onChange: (v: string) => void
  options: TabOption[]
  label: string
}) {
  return (
    <div className="-mx-4 px-4 sm:mx-0 sm:px-0 overflow-x-auto">
      <div role="group" aria-label={label} className="inline-flex gap-1 rounded-xl bg-muted/60 p-1">
        {options.map((o) => {
          const active = value === o.value
          return (
            <button
              key={o.value || 'all'}
              type="button"
              onClick={() => onChange(o.value)}
              aria-pressed={active}
              className={cn(
                'inline-flex items-center gap-2 h-9 px-3 rounded-lg text-sm font-medium whitespace-nowrap transition-colors',
                active ? 'bg-surface text-heading shadow-sm' : 'text-muted-fg hover:text-foreground',
              )}
            >
              {o.dot && <span className={cn('h-2 w-2 rounded-full', o.dot)} aria-hidden />}
              {o.label}
              {o.count !== undefined && (
                <span
                  className={cn(
                    'min-w-6 rounded-full px-1.5 text-xs tabular-nums',
                    active ? 'bg-primary-soft text-primary' : 'bg-surface/70 text-muted-fg',
                  )}
                >
                  {o.count}
                </span>
              )}
            </button>
          )
        })}
      </div>
    </div>
  )
}

export function Toolbar({ children }: { children: ReactNode }) {
  return <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center mb-4">{children}</div>
}

// ---------- data list ----------

export interface Column<T> {
  key: string
  header: string
  cell: (row: T) => ReactNode
  className?: string
  /** Right-align (numbers). */
  numeric?: boolean
}

/**
 * Table on md+ screens, tappable cards below. Rows open on click, Enter or
 * Space; action buttons inside must call `e.stopPropagation()`.
 */
export function DataList<T>({
  rows,
  columns,
  rowKey,
  onOpen,
  card,
  loading,
  empty,
  rowLabel,
}: {
  rows: T[]
  columns: Column<T>[]
  rowKey: (row: T) => string
  onOpen?: (row: T) => void
  card: (row: T) => ReactNode
  loading: boolean
  empty: ReactNode
  rowLabel?: (row: T) => string
}) {
  if (loading) return <ListSkeleton columns={columns.length} />
  if (rows.length === 0) return <>{empty}</>

  const keyOpen = (row: T) => (e: React.KeyboardEvent) => {
    if (!onOpen || e.target !== e.currentTarget) return
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      onOpen(row)
    }
  }

  return (
    <>
      <div className="hidden md:block bg-surface border border-border rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/40">
                {columns.map((c) => (
                  <th
                    key={c.key}
                    scope="col"
                    className={cn(
                      'px-4 py-3 text-xs font-semibold uppercase tracking-wide text-muted-fg whitespace-nowrap',
                      c.numeric ? 'text-end' : 'text-start',
                    )}
                  >
                    {c.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {rows.map((row) => (
                <tr
                  key={rowKey(row)}
                  tabIndex={onOpen ? 0 : undefined}
                  aria-label={rowLabel?.(row)}
                  onClick={onOpen ? () => onOpen(row) : undefined}
                  onKeyDown={keyOpen(row)}
                  className={cn(
                    'transition-colors',
                    onOpen && 'cursor-pointer hover:bg-primary-soft/40 focus-visible:outline-none focus-visible:bg-primary-soft/60',
                  )}
                >
                  {columns.map((c) => (
                    <td key={c.key} className={cn('px-4 py-3 align-middle', c.numeric && 'text-end tabular-nums', c.className)}>
                      {c.cell(row)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <ul className="md:hidden space-y-2">
        {rows.map((row) => (
          <li
            key={rowKey(row)}
            tabIndex={onOpen ? 0 : undefined}
            aria-label={rowLabel?.(row)}
            onClick={onOpen ? () => onOpen(row) : undefined}
            onKeyDown={keyOpen(row)}
            className={cn(
              'bg-surface border border-border rounded-xl p-4',
              onOpen && 'cursor-pointer active:bg-muted/40 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary',
            )}
          >
            {card(row)}
          </li>
        ))}
      </ul>
    </>
  )
}

function ListSkeleton({ columns }: { columns: number }) {
  const { t } = useTranslation()
  return (
    <div className="bg-surface border border-border rounded-xl overflow-hidden" aria-busy="true" aria-live="polite">
      <span className="sr-only">{t('common.loading')}</span>
      <div className="hidden md:block h-11 bg-muted/40 border-b border-border" />
      <div className="divide-y divide-border">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="flex items-center gap-4 px-4 py-4">
            <div className="h-9 w-9 rounded-full bg-muted animate-pulse shrink-0" />
            <div className="flex-1 grid gap-2 md:grid-cols-4">
              {Array.from({ length: Math.min(columns, 4) }).map((__, j) => (
                <div key={j} className={cn('h-3.5 rounded bg-muted animate-pulse', j === 0 ? 'w-3/4' : 'w-1/2', j > 0 && 'hidden md:block')} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export function EmptyResults({
  icon,
  title,
  hint,
  onClear,
}: {
  icon?: ReactNode
  title: string
  hint?: string
  onClear?: () => void
}) {
  const { t } = useTranslation()
  return (
    <div className="bg-surface border border-dashed border-border rounded-xl px-6 py-14 text-center">
      <div className="mx-auto mb-4 h-12 w-12 rounded-full bg-muted flex items-center justify-center text-muted-fg">
        {icon ?? <SearchX className="h-6 w-6" aria-hidden />}
      </div>
      <p className="font-semibold text-heading">{title}</p>
      {hint && <p className="text-sm text-muted-fg mt-1 max-w-sm mx-auto">{hint}</p>}
      {onClear && (
        <Button variant="outline" size="sm" className="mt-5" onClick={onClear}>
          {t('admin.list.clearFilters')}
        </Button>
      )}
    </div>
  )
}

export function Pagination({
  page,
  size,
  total,
  totalPages,
  onPage,
}: {
  page: number
  size: number
  total: number
  totalPages: number
  onPage: (p: number) => void
}) {
  const { t, i18n } = useTranslation()
  if (total === 0) return null
  const fmt = new Intl.NumberFormat(i18n.language)
  const from = page * size + 1
  const to = Math.min(total, (page + 1) * size)
  return (
    <nav aria-label={t('admin.list.pagination')} className="flex items-center justify-between gap-3 mt-4">
      <p className="text-sm text-muted-fg">
        {t('admin.list.showing', { from: fmt.format(from), to: fmt.format(to), total: fmt.format(total) })}
      </p>
      {totalPages > 1 && (
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>
            <ChevronLeft className="h-4 w-4 rtl:rotate-180" aria-hidden />
            <span className="hidden sm:inline">{t('admin.list.prev')}</span>
            <span className="sr-only sm:hidden">{t('admin.list.prev')}</span>
          </Button>
          <span className="text-sm text-muted-fg tabular-nums whitespace-nowrap">
            {page + 1} / {totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
            <span className="hidden sm:inline">{t('admin.list.next')}</span>
            <span className="sr-only sm:hidden">{t('admin.list.next')}</span>
            <ChevronRight className="h-4 w-4 rtl:rotate-180" aria-hidden />
          </Button>
        </div>
      )}
    </nav>
  )
}

// ---------- cells ----------

const AVATAR_TONES = [
  'bg-primary-soft text-primary',
  'bg-accent-soft text-accent',
  'bg-success-soft text-success',
  'bg-warning-soft text-warning',
]

/** Initials avatar; the tone is stable per name. */
export function Avatar({ name, className }: { name: string; className?: string }) {
  const initials =
    name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0])
      .join('')
      .toUpperCase() || '?'
  const tone = AVATAR_TONES[[...name].reduce((s, c) => s + c.charCodeAt(0), 0) % AVATAR_TONES.length]
  return (
    <span
      aria-hidden
      className={cn('h-9 w-9 shrink-0 rounded-full flex items-center justify-center text-xs font-bold', tone, className)}
    >
      {initials}
    </span>
  )
}

/** Two-line identity cell: strong primary line, muted secondary line. */
export function Identity({
  primary,
  secondary,
  avatar,
  mono,
}: {
  primary: ReactNode
  secondary?: ReactNode
  avatar?: string
  mono?: boolean
}) {
  return (
    <div className="flex items-center gap-3 min-w-0">
      {avatar !== undefined && <Avatar name={avatar} />}
      <div className="min-w-0">
        <p className={cn('font-semibold text-heading truncate', mono && 'font-mono text-xs')}>{primary}</p>
        {secondary && <p className="text-xs text-muted-fg truncate">{secondary}</p>}
      </div>
    </div>
  )
}

export function Muted({ children = '—' }: { children?: ReactNode }) {
  return <span className="text-muted-fg">{children}</span>
}

/** Licence plate styled like a physical plate for fast scanning. */
export function PlateChip({ plate }: { plate: string }) {
  return (
    <span className="inline-flex items-center rounded-md border-2 border-heading/80 bg-surface px-2 py-0.5 font-mono text-xs font-bold tracking-wider text-heading whitespace-nowrap">
      {plate}
    </span>
  )
}

// ---------- detail dialog ----------

/**
 * Bottom sheet on phones, centred dialog on larger screens. Closes on Escape
 * or backdrop click, locks page scroll, moves focus in and restores it on close.
 */
export function DetailDialog({
  open,
  onClose,
  title,
  badges,
  subtitle,
  children,
  footer,
  blockEscape,
}: {
  open: boolean
  onClose: () => void
  title: ReactNode
  badges?: ReactNode
  subtitle?: ReactNode
  children: ReactNode
  footer?: ReactNode
  /** True while a nested confirm dialog owns Escape. */
  blockEscape?: boolean
}) {
  const { t } = useTranslation()
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    const previous = document.activeElement as HTMLElement | null
    closeRef.current?.focus()
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = prevOverflow
      previous?.focus?.()
    }
  }, [open])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !blockEscape) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, blockEscape, onClose])

  if (!open) return null
  return createPortal(
    <div className="fixed inset-0 z-50 overflow-y-auto" role="dialog" aria-modal="true" aria-labelledby="admin-detail-title">
      <button
        className="fixed inset-0 bg-heading/40 backdrop-blur-[1px] cursor-default dialog-backdrop-enter"
        onClick={onClose}
        aria-label={t('common.close')}
        tabIndex={-1}
      />
      <div className="relative min-h-full flex items-end sm:items-center justify-center p-0 sm:p-6">
        <div className="relative w-full sm:max-w-2xl bg-surface rounded-t-2xl sm:rounded-2xl shadow-pop border border-border flex flex-col max-h-[92vh] dialog-panel-enter">
          <div className="sm:hidden mx-auto mt-2 h-1.5 w-10 rounded-full bg-border" aria-hidden />
          <div className="shrink-0 flex items-start justify-between gap-3 px-5 pt-4 pb-4 sm:pt-5 border-b border-border">
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h2 id="admin-detail-title" className="text-xl font-bold text-heading break-words">
                  {title}
                </h2>
                {badges}
              </div>
              {subtitle && <div className="text-xs text-muted-fg mt-1">{subtitle}</div>}
            </div>
            <button
              ref={closeRef}
              onClick={onClose}
              aria-label={t('common.close')}
              className="h-10 w-10 shrink-0 rounded-lg flex items-center justify-center text-muted-fg hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <X className="h-5 w-5" aria-hidden />
            </button>
          </div>
          <div className="flex-1 min-h-0 overflow-y-auto p-5 space-y-6">{children}</div>
          {footer && (
            <div className="shrink-0 border-t border-border p-4 sm:p-5 flex flex-wrap items-center justify-end gap-2 bg-muted/20 sm:rounded-b-2xl">
              {footer}
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body,
  )
}

export function DetailSection({ icon: Icon, title, children }: { icon: typeof X; title: string; children: ReactNode }) {
  return (
    <section>
      <h3 className="text-sm font-semibold text-heading mb-3 flex items-center gap-2">
        <Icon className="h-4 w-4 text-primary" aria-hidden />
        {title}
      </h3>
      {children}
    </section>
  )
}

/** Label/value pairs on a soft panel; `full` spans both columns. */
export function InfoGrid({ items }: { items: { label: string; value: ReactNode; full?: boolean }[] }) {
  return (
    <dl className="grid sm:grid-cols-2 gap-px rounded-xl border border-border bg-border overflow-hidden">
      {items.map((it) => (
        <div key={it.label} className={cn('bg-surface px-4 py-3 min-w-0', it.full && 'sm:col-span-2')}>
          <dt className="text-xs font-medium text-muted-fg">{it.label}</dt>
          <dd className="text-sm text-foreground mt-0.5 break-words">{it.value}</dd>
        </div>
      ))}
    </dl>
  )
}

export function MetricTiles({ items }: { items: { icon: typeof X; label: string; value: ReactNode }[] }) {
  return (
    <div className="grid grid-cols-3 gap-3">
      {items.map(({ icon: Icon, label, value }) => (
        <div key={label} className="rounded-xl border border-border p-3">
          <Icon className="h-4 w-4 text-primary mb-1.5" aria-hidden />
          <p className="font-display text-2xl font-bold text-heading tabular-nums leading-none">{value}</p>
          <p className="text-xs text-muted-fg mt-1">{label}</p>
        </div>
      ))}
    </div>
  )
}

// ---------- toast ----------

/** Minimal success toast for completed admin actions (polite live region). */
export function useToast() {
  const [message, setMessage] = useState<string | null>(null)
  useEffect(() => {
    if (!message) return
    const id = window.setTimeout(() => setMessage(null), 3500)
    return () => window.clearTimeout(id)
  }, [message])
  const node = createPortal(
    <div className="fixed bottom-4 inset-x-4 sm:inset-x-auto sm:end-6 z-[60] flex justify-center sm:justify-end pointer-events-none" role="status" aria-live="polite">
      {message && (
        <div className="pointer-events-auto flex items-center gap-2 rounded-xl bg-heading text-white px-4 py-3 text-sm font-medium shadow-pop dialog-panel-enter">
          <CheckCircle2 className="h-4 w-4 text-success" aria-hidden />
          {message}
        </div>
      )}
    </div>,
    document.body,
  )
  return { show: setMessage, node }
}

// ---------- formatting ----------

export function useAdminFormat() {
  const { i18n } = useTranslation()
  const locale = i18n.language
  return {
    date: (iso: string) => new Date(iso).toLocaleDateString(locale, { dateStyle: 'medium' }),
    time: (iso: string) => new Date(iso).toLocaleTimeString(locale, { timeStyle: 'short' }),
    dateTime: (iso: string) => new Date(iso).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' }),
    num: (v: number) => new Intl.NumberFormat(locale).format(v),
    money: (v: number | string, currency = 'TND') =>
      `${new Intl.NumberFormat(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(v))} ${currency}`,
  }
}

/** Two-line date cell: date strong, time muted. */
export function DateCell({ iso }: { iso: string | null | undefined }) {
  const f = useAdminFormat()
  if (!iso) return <Muted />
  return (
    <div className="whitespace-nowrap">
      <p className="text-foreground">{f.date(iso)}</p>
      <p className="text-xs text-muted-fg">{f.time(iso)}</p>
    </div>
  )
}
