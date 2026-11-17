import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowDownRight, ArrowUpRight, Minus, RefreshCw } from 'lucide-react'
import { ResponsiveContainer } from 'recharts'
import { cn } from '@/lib/cn'
import type { DashboardDays, DashboardKpi } from '@/types'

/** Chart palette, aligned with the theme tokens in index.css. */
export const C = {
  primary: '#2563eb',
  accent: '#ea580c',
  success: '#10b981',
  warning: '#f59e0b',
  danger: '#dc2626',
  violet: '#8b5cf6',
  slate: '#94a3b8',
}
export const PALETTE = [C.primary, C.accent, C.success, C.violet, C.warning, C.danger, C.slate]

export const gridProps = { strokeDasharray: '3 3', stroke: C.slate, strokeOpacity: 0.25, vertical: false }
export const axisProps = { tick: { fontSize: 11, fill: C.slate }, tickLine: false, axisLine: false }
export const tooltipProps = {
  contentStyle: {
    backgroundColor: 'var(--color-surface)',
    border: '1px solid var(--color-border)',
    borderRadius: 8,
    fontSize: 12,
  },
  cursor: { fill: C.slate, fillOpacity: 0.08 },
}

// ---------- formatting ----------

export function useFormatters() {
  const { i18n } = useTranslation()
  const locale = i18n.language
  const num = new Intl.NumberFormat(locale, { maximumFractionDigits: 1 })
  const money = new Intl.NumberFormat(locale, { minimumFractionDigits: 0, maximumFractionDigits: 2 })
  const compact = new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 })
  return {
    num: (v: number | string) => num.format(Number(v)),
    tnd: (v: number | string) => `${money.format(Number(v))} TND`,
    compact: (v: number | string) => compact.format(Number(v)),
    pct: (v: number) => `${num.format(v)}%`,
    /** Bucket label for a YYYY-MM-DD start date at the given granularity. */
    bucket: (iso: string, granularity: 'day' | 'week' | 'month') => {
      const [y, m, d] = iso.split('-').map(Number)
      const date = new Date(y, m - 1, d)
      return granularity === 'month'
        ? date.toLocaleDateString(locale, { month: 'short', year: '2-digit' })
        : date.toLocaleDateString(locale, { day: 'numeric', month: 'short' })
    },
    dateTime: (iso: string) =>
      new Date(iso).toLocaleString(locale, { dateStyle: 'medium', timeStyle: 'short' }),
  }
}

/** % change vs the previous window; null when there is nothing to compare. */
export function delta(k: DashboardKpi): number | null {
  if (k.previous === 0) return k.value === 0 ? 0 : null
  return ((k.value - k.previous) / k.previous) * 100
}

// ---------- building blocks ----------

export function RangePicker({ value, onChange }: { value: DashboardDays; onChange: (d: DashboardDays) => void }) {
  const { t } = useTranslation()
  const options: DashboardDays[] = [7, 30, 90, 365]
  return (
    <div role="group" aria-label={t('admin.dash.range')} className="inline-flex rounded-lg border border-border bg-surface p-0.5">
      {options.map((d) => (
        <button
          key={d}
          onClick={() => onChange(d)}
          aria-pressed={value === d}
          className={cn(
            'px-3 h-8 rounded-md text-sm font-medium transition-colors',
            value === d ? 'bg-primary text-white shadow-sm' : 'text-muted-fg hover:text-foreground',
          )}
        >
          {t(`admin.dash.ranges.${d}`)}
        </button>
      ))}
    </div>
  )
}

/** Live badge with its own 1s ticker so the page itself does not re-render. */
export function LiveIndicator({
  updatedAt,
  fetching,
  onRefresh,
}: {
  updatedAt: number
  fetching: boolean
  onRefresh: () => void
}) {
  const { t } = useTranslation()
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [])
  const seconds = Math.max(0, Math.round((now - updatedAt) / 1000))
  return (
    <div className="flex items-center gap-2">
      <span className="inline-flex items-center gap-1.5 text-xs text-muted-fg whitespace-nowrap" aria-live="polite">
        <span className="relative flex h-2 w-2">
          <span className="absolute inline-flex h-full w-full rounded-full bg-success opacity-60 animate-ping" />
          <span className="relative inline-flex h-2 w-2 rounded-full bg-success" />
        </span>
        {t('admin.dash.live')} ·{' '}
        {seconds < 5 ? t('admin.dash.justNow') : t('admin.dash.updatedAgo', { seconds })}
      </span>
      <button
        onClick={onRefresh}
        aria-label={t('admin.dash.refresh')}
        title={t('admin.dash.refresh')}
        className="h-8 w-8 rounded-lg border border-border bg-surface flex items-center justify-center text-muted-fg hover:text-foreground"
      >
        <RefreshCw className={cn('h-4 w-4', fetching && 'animate-spin')} aria-hidden />
      </button>
    </div>
  )
}

export function KpiCard({
  icon: Icon,
  tone,
  label,
  value,
  kpi,
  hint,
}: {
  icon: typeof RefreshCw
  tone: string
  label: string
  value: string
  kpi: DashboardKpi
  hint?: string
}) {
  const { t } = useTranslation()
  const d = delta(kpi)
  const up = d !== null && d > 0
  const down = d !== null && d < 0
  return (
    <div className="bg-surface border border-border rounded-xl p-4 flex flex-col gap-3">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm text-muted-fg">{label}</p>
        <div className={cn('h-8 w-8 rounded-lg flex items-center justify-center', tone)}>
          <Icon className="h-4 w-4" aria-hidden />
        </div>
      </div>
      <p className="text-2xl font-bold text-heading tabular-nums">{value}</p>
      <div className="flex items-center gap-1.5 text-xs">
        {d === null ? (
          <span className="inline-flex items-center gap-0.5 font-medium text-primary">
            <ArrowUpRight className="h-3.5 w-3.5" aria-hidden />
            {t('admin.dash.new')}
          </span>
        ) : (
          <span
            className={cn(
              'inline-flex items-center gap-0.5 font-medium tabular-nums',
              up && 'text-success',
              down && 'text-destructive',
              !up && !down && 'text-muted-fg',
            )}
          >
            {up ? <ArrowUpRight className="h-3.5 w-3.5" aria-hidden /> : down ? <ArrowDownRight className="h-3.5 w-3.5" aria-hidden /> : <Minus className="h-3.5 w-3.5" aria-hidden />}
            {Math.abs(d).toFixed(d !== 0 && Math.abs(d) < 10 ? 1 : 0)}%
          </span>
        )}
        <span className="text-muted-fg truncate">{t('admin.dash.vsPrevious')}</span>
      </div>
      {hint && <p className="text-xs text-muted-fg -mt-1">{hint}</p>}
    </div>
  )
}

export function AttentionCard({
  icon: Icon,
  label,
  value,
  detail,
  to,
  alert,
}: {
  icon: typeof RefreshCw
  label: string
  value: string
  detail?: string
  to?: string
  alert: boolean
}) {
  const body = (
    <div
      className={cn(
        'h-full rounded-xl border p-4 flex items-center gap-3 transition-colors',
        alert ? 'border-warning/40 bg-warning-soft' : 'border-border bg-surface',
        to && 'hover:border-primary/50',
      )}
    >
      <div
        className={cn(
          'h-10 w-10 shrink-0 rounded-lg flex items-center justify-center',
          alert ? 'bg-warning/15 text-warning' : 'bg-muted text-muted-fg',
        )}
      >
        <Icon className="h-5 w-5" aria-hidden />
      </div>
      <div className="min-w-0">
        <p className="text-xl font-bold text-heading tabular-nums leading-tight">{value}</p>
        <p className="text-xs text-muted-fg truncate">{label}</p>
        {detail && <p className="text-xs font-medium text-foreground truncate">{detail}</p>}
      </div>
    </div>
  )
  return to ? (
    <Link to={to} className="block focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded-xl">
      {body}
    </Link>
  ) : (
    body
  )
}

export function ChartCard({
  title,
  subtitle,
  empty,
  height = 260,
  className,
  children,
  footer,
}: {
  title: string
  subtitle?: string
  empty?: boolean
  height?: number
  className?: string
  children: React.ReactElement
  footer?: React.ReactNode
}) {
  const { t } = useTranslation()
  return (
    <div className={cn('bg-surface border border-border rounded-xl p-5 flex flex-col', className)}>
      <div className="mb-4">
        <p className="text-sm font-semibold text-heading">{title}</p>
        {subtitle && <p className="text-xs text-muted-fg mt-0.5">{subtitle}</p>}
      </div>
      {empty ? (
        <div className="flex items-center justify-center text-sm text-muted-fg" style={{ height }}>
          {t('admin.dash.noData')}
        </div>
      ) : (
        <ResponsiveContainer width="100%" height={height}>
          {children}
        </ResponsiveContainer>
      )}
      {footer}
    </div>
  )
}

/** Labelled horizontal bar, used for rankings and the sticker funnel. */
export function BarRow({
  label,
  value,
  max,
  color,
  right,
}: {
  label: string
  value: number
  max: number
  color: string
  right: string
}) {
  const width = max > 0 ? Math.max(2, (value / max) * 100) : 0
  return (
    <div>
      <div className="flex items-center justify-between gap-2 text-xs mb-1">
        <span className="text-foreground truncate">{label}</span>
        <span className="text-muted-fg tabular-nums whitespace-nowrap">{right}</span>
      </div>
      <div className="h-2 rounded-full bg-muted overflow-hidden">
        <div className="h-full rounded-full transition-[width] duration-700" style={{ width: `${width}%`, backgroundColor: color }} />
      </div>
    </div>
  )
}

export function SectionTitle({ children }: { children: React.ReactNode }) {
  return <h2 className="text-xs font-semibold uppercase tracking-wide text-muted-fg mb-3 mt-8">{children}</h2>
}
