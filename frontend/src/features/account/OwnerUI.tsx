import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowRight, CarFront, ChevronRight, MessageSquareText } from 'lucide-react'
import { useCarColor } from '@/hooks/useCarColor'
import { ConversationStatusBadge } from '@/features/conversations/ConversationBadges'
import { formatRelative } from '@/lib/format'
import { CountUp } from '@/components/shared/CountUp'
import { cn } from '@/lib/cn'
import type { ConversationSummaryResponse } from '@/types'

/*
 * Shared owner-app building blocks. Size scale used everywhere:
 *   icon tiles 40px (h-10) with 20px icons · inline icons 16px · avatars 40/56px
 *   card padding 20px (24px on sm+) · section gap 24-32px · list rows ≥ 64px.
 */

/** A titled surface for a page section, with an optional "see all" link. */
export function SectionCard({
  title,
  description,
  action,
  icon: Icon,
  iconTone = 'bg-primary-soft text-primary',
  flush,
  children,
  className,
}: {
  title: string
  description?: string
  action?: { to: string; label: string }
  /** Optional 40px icon tile beside the title. */
  icon?: typeof CarFront
  iconTone?: string
  /** Body without side/bottom padding (edge-to-edge lists). */
  flush?: boolean
  children: ReactNode
  className?: string
}) {
  return (
    <section className={cn('bg-surface border border-border rounded-2xl shadow-card overflow-hidden', className)}>
      <div className="flex items-start justify-between gap-4 px-5 sm:px-6 pt-5 sm:pt-6 pb-4">
        <div className="min-w-0 flex items-start gap-3">
          {Icon && (
            <span className={cn('h-10 w-10 shrink-0 rounded-xl flex items-center justify-center', iconTone)} aria-hidden>
              <Icon className="h-5 w-5" />
            </span>
          )}
          <div className="min-w-0">
            <h2 className="text-lg font-bold text-heading leading-snug">{title}</h2>
            {description && <p className="text-sm text-muted-fg mt-1 leading-relaxed">{description}</p>}
          </div>
        </div>
        {action && (
          <Link
            to={action.to}
            className="group shrink-0 inline-flex items-center gap-1 h-9 px-2 -me-2 rounded-lg text-sm font-semibold text-primary hover:bg-primary-soft"
          >
            {action.label}
            <ArrowRight className="nudge h-4 w-4 rtl:rotate-180" aria-hidden />
          </Link>
        )}
      </div>
      <div className={flush ? undefined : 'px-5 sm:px-6 pb-5 sm:pb-6'}>{children}</div>
    </section>
  )
}

/** Clickable metric tile. */
export function StatTile({
  to,
  icon: Icon,
  tone,
  value,
  label,
  loading,
}: {
  to: string
  icon: typeof CarFront
  tone: string
  value: number
  label: string
  loading?: boolean
}) {
  return (
    <Link
      to={to}
      className="hover-lift group bg-surface border border-border rounded-2xl p-4 sm:p-5 shadow-card hover:border-primary/40 flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4 min-w-0"
    >
      <span className={cn('h-10 w-10 sm:h-12 sm:w-12 shrink-0 rounded-xl flex items-center justify-center', tone)}>
        <Icon className="h-5 w-5 sm:h-6 sm:w-6" aria-hidden />
      </span>
      <span className="min-w-0">
        {loading ? (
          <span className="block h-7 w-10 rounded bg-muted animate-pulse" />
        ) : (
          <span className="block font-display text-2xl sm:text-3xl font-bold text-heading tabular-nums leading-none">
            <CountUp value={value} />
          </span>
        )}
        <span className="block text-xs sm:text-sm text-muted-fg mt-1.5 leading-snug">{label}</span>
      </span>
    </Link>
  )
}

/** Car-coloured avatar tile for a vehicle. */
export function VehicleAvatar({ color, size = 'md' }: { color: string | null; size?: 'md' | 'lg' }) {
  const c = useCarColor(color)
  return (
    <span
      className={cn('shrink-0 rounded-xl flex items-center justify-center', size === 'lg' ? 'h-12 w-12' : 'h-10 w-10')}
      style={{ backgroundColor: c.soft, color: c.main }}
      aria-hidden
    >
      <CarFront className={size === 'lg' ? 'h-6 w-6' : 'h-5 w-5'} />
    </span>
  )
}

/** One conversation in a list: vehicle, preview, time, unread state. */
export function ConversationRow({
  conv,
  onOpen,
}: {
  conv: ConversationSummaryResponse
  onOpen?: () => void
}) {
  const { t } = useTranslation()
  return (
    <Link
      to={`/messages/${conv.id}`}
      onClick={onOpen}
      className={cn(
        'flex items-center gap-3 sm:gap-4 px-4 sm:px-5 py-4 min-h-16 transition-colors hover:bg-muted/50 focus-visible:outline-none focus-visible:bg-primary-soft/50',
        conv.unread && 'bg-primary-soft/30',
      )}
    >
      <span
        className={cn(
          'relative h-10 w-10 shrink-0 rounded-xl flex items-center justify-center',
          conv.unread ? 'bg-primary text-white' : 'bg-primary-soft text-primary',
        )}
        aria-hidden
      >
        <MessageSquareText className="h-5 w-5" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex items-center gap-2">
          <span className={cn('truncate', conv.unread ? 'font-bold text-heading' : 'font-semibold text-foreground')}>
            {conv.vehicleNickname ?? t('message.conversation')}
          </span>
          {conv.unread && <span className="sr-only">{t('message.unread')}</span>}
        </span>
        <span className={cn('block truncate text-sm mt-0.5', conv.unread ? 'text-foreground' : 'text-muted-fg')}>
          {conv.lastMessagePreview ?? t('message.pending')}
        </span>
      </span>
      <span className="shrink-0 flex flex-col items-end gap-1.5">
        <span className="text-xs text-muted-fg whitespace-nowrap">{formatRelative(conv.createdAt)}</span>
        {conv.status !== 'SENT' ? (
          <ConversationStatusBadge status={conv.status} />
        ) : (
          <ChevronRight className="h-4 w-4 text-muted-fg rtl:rotate-180" aria-hidden />
        )}
      </span>
    </Link>
  )
}

/** Dashed empty state used inside section cards. */
export function InlineEmpty({ icon: Icon, title, hint, action }: { icon: typeof CarFront; title: string; hint?: string; action?: ReactNode }) {
  return (
    <div className="rounded-xl border border-dashed border-border px-5 py-10 text-center">
      <span className="mx-auto mb-3 h-12 w-12 rounded-full bg-muted text-muted-fg flex items-center justify-center">
        <Icon className="h-6 w-6" aria-hidden />
      </span>
      <p className="font-semibold text-heading">{title}</p>
      {hint && <p className="text-sm text-muted-fg mt-1.5 max-w-sm mx-auto leading-relaxed">{hint}</p>}
      {action && <div className="mt-5 flex justify-center">{action}</div>}
    </div>
  )
}
