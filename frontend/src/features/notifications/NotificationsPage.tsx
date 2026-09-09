import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Bell, MessageCircle, Smartphone } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import { formatRelative } from '@/lib/format'
import { cn } from '@/lib/cn'
import type { ContactChannel } from '@/types'

/**
 * Notifications. The backend has no standalone notifications endpoint —
 * notifications are delivered in-app as unread conversations, so this page
 * surfaces them from the owner's conversations.
 */
export default function NotificationsPage() {
  const { t } = useTranslation()

  const conversationsQuery = useQuery({
    queryKey: conversationQueryKeys.list(),
    queryFn: () => conversationApi.list(),
  })

  const loading = conversationsQuery.isPending
  const error = conversationsQuery.error
  const notifications = (conversationsQuery.data ?? [])
    .filter((c) => c.unread)
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())

  return (
    <div>
      <PageHeader title={t('notifications.title')} subtitle={t('notifications.subtitle')} />

      {error && <ErrorState message={t('errors.generic')} onRetry={() => conversationsQuery.refetch()} />}

      {loading ? (
        <div className="bg-surface border border-border rounded-lg divide-y divide-border">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex items-center gap-4 p-4">
              <div className="h-11 w-11 rounded-xl bg-muted animate-pulse" />
              <div className="flex-1 space-y-2">
                <div className="h-4 w-2/3 rounded bg-muted animate-pulse" />
                <div className="h-3 w-1/2 rounded bg-muted animate-pulse" />
              </div>
            </div>
          ))}
        </div>
      ) : notifications.length === 0 ? (
        <div className="bg-surface border border-border rounded-lg">
          <EmptyState
            icon={<Bell className="h-7 w-7" aria-hidden />}
            title={t('notifications.empty')}
            hint={t('notifications.emptyHint')}
          />
        </div>
      ) : (
        <ul className="bg-surface border border-border rounded-lg divide-y divide-border">
          {notifications.map((n) => (
            <li key={n.id}>
              <Link to={`/messages/${n.id}`} className="flex items-center gap-4 p-4 hover:bg-muted transition-colors">
                <div className={cn('h-11 w-11 rounded-xl flex items-center justify-center shrink-0', tone(n.channel))}>
                  {n.channel === 'WHATSAPP' ? (
                    <MessageCircle className="h-5 w-5" aria-hidden />
                  ) : (
                    <Smartphone className="h-5 w-5" aria-hidden />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="font-semibold text-foreground truncate">
                    {n.vehicleNickname ?? t('message.conversation')}
                  </p>
                  <p className="text-sm text-muted-fg truncate">{n.lastMessagePreview ?? t('message.pending')}</p>
                </div>
                <p className="text-xs text-muted-fg shrink-0">{formatRelative(n.createdAt)}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function tone(channel: ContactChannel) {
  return channel === 'WHATSAPP' ? 'bg-success-soft text-whatsapp' : 'bg-primary-soft text-sms'
}
