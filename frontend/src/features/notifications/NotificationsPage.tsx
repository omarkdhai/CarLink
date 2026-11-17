import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowRight, BellOff, KeyRound, MailWarning, PhoneOff } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { useAccountStatus, type AccountNotice } from '@/features/account/useAccountStatus'
import { ConversationRow, InlineEmpty } from '@/features/account/OwnerUI'
import { cn } from '@/lib/cn'

const NOTICE: Record<AccountNotice, { icon: typeof PhoneOff; to: string; tone: string; urgent?: boolean }> = {
  addPhone: { icon: PhoneOff, to: '/profile', tone: 'bg-destructive-soft text-destructive', urgent: true },
  activateSticker: { icon: KeyRound, to: '/stickers', tone: 'bg-warning-soft text-warning' },
  verifyEmail: { icon: MailWarning, to: '/profile', tone: 'bg-primary-soft text-primary' },
}

/**
 * Notifications. The backend has no standalone notifications endpoint, so
 * this feed combines account issues that need action with unread messages.
 */
export default function NotificationsPage() {
  const { t } = useTranslation()
  const status = useAccountStatus()

  const loading = status.conversations.isPending
  const error = status.conversations.error
  const unread = (status.conversations.data ?? [])
    .filter((c) => c.unread)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const nothing = !loading && !error && unread.length === 0 && status.notices.length === 0

  return (
    <div className="motion-stagger space-y-6 sm:space-y-8">
      <PageHeader title={t('notifications.title')} subtitle={t('notifications.subtitle')} />

      {error && <ErrorState message={t('errors.generic')} onRetry={() => status.conversations.refetch()} />}

      {status.notices.length > 0 && (
        <section aria-labelledby="notices-title" className="space-y-3">
          <h2 id="notices-title" className="text-xs font-semibold uppercase tracking-wide text-muted-fg px-1">
            {t('notifications.actionNeeded')}
          </h2>
          <ul className="motion-stagger space-y-3">
            {status.notices.map((n) => {
              const { icon: Icon, to, tone, urgent } = NOTICE[n]
              return (
                <li key={n}>
                  <Link
                    to={to}
                    className={cn(
                      'hover-lift group flex items-start gap-4 rounded-2xl border bg-surface p-4 sm:p-5 shadow-card',
                      urgent ? 'border-destructive/30' : 'border-border',
                    )}
                  >
                    <span className={cn('h-10 w-10 shrink-0 rounded-xl flex items-center justify-center', tone)} aria-hidden>
                      <Icon className="h-5 w-5" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block font-semibold text-heading">{t(`notifications.notice.${n}`)}</span>
                      <span className="block text-sm text-muted-fg mt-1 leading-relaxed">{t(`notifications.notice.${n}Hint`)}</span>
                      <span className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-primary">
                        {t(`notifications.notice.${n}Cta`)}
                        <ArrowRight className="nudge h-4 w-4 rtl:rotate-180" aria-hidden />
                      </span>
                    </span>
                  </Link>
                </li>
              )
            })}
          </ul>
        </section>
      )}

      {loading ? (
        <div className="bg-surface border border-border rounded-2xl divide-y divide-border" aria-busy="true">
          {[0, 1].map((i) => (
            <div key={i} className="flex items-center gap-3 px-5 py-4">
              <div className="h-10 w-10 rounded-xl bg-muted animate-pulse" />
              <div className="flex-1 space-y-2">
                <div className="h-3.5 w-1/2 rounded bg-muted animate-pulse" />
                <div className="h-3 w-3/4 rounded bg-muted animate-pulse" />
              </div>
            </div>
          ))}
        </div>
      ) : (
        unread.length > 0 && (
          <section aria-labelledby="new-messages-title" className="space-y-3">
            <h2 id="new-messages-title" className="text-xs font-semibold uppercase tracking-wide text-muted-fg px-1">
              {t('notifications.newMessages', { count: unread.length })}
            </h2>
            <ul className="motion-stagger bg-surface border border-border rounded-2xl shadow-card divide-y divide-border overflow-hidden">
              {unread.map((c) => (
                <li key={c.id}>
                  <ConversationRow conv={c} />
                </li>
              ))}
            </ul>
          </section>
        )
      )}

      {nothing && <InlineEmpty icon={BellOff} title={t('notifications.caughtUp')} hint={t('notifications.emptyHint')} />}
    </div>
  )
}
