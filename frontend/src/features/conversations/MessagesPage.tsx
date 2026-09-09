import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MessageSquare, MessageCircle, Smartphone } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { SkeletonGrid } from '@/components/shared/SkeletonGrid'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import { ConversationStatusBadge } from '@/features/conversations/ConversationBadges'
import { cn } from '@/lib/cn'
import { formatRelative } from '@/lib/format'
import type { ContactChannel } from '@/types'

type Filter = 'ALL' | 'UNREAD' | 'WHATSAPP' | 'SMS'
const FILTERS: Filter[] = ['ALL', 'UNREAD', 'WHATSAPP', 'SMS']

export default function MessagesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState<Filter>('ALL')

  const conversationsQuery = useQuery({
    queryKey: conversationQueryKeys.list(),
    queryFn: () => conversationApi.list(),
  })

  const markRead = useMutation({
    mutationFn: (id: string) => conversationApi.markRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: conversationQueryKeys.all }),
  })

  const all = conversationsQuery.data ?? []
  const conversations = all
    .filter((c) => (filter === 'UNREAD' ? c.unread : true))
    .filter((c) => (filter === 'WHATSAPP' || filter === 'SMS' ? c.channel === filter : true))
    .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())

  const loading = conversationsQuery.isPending
  const error = conversationsQuery.error
  const isEmpty = !loading && !error && conversations.length === 0

  function open(convId: string, unread: boolean) {
    if (unread) markRead.mutate(convId)
    navigate(`/messages/${convId}`)
  }

  return (
    <div>
      <PageHeader title={t('message.title')} subtitle={t('message.subtitle')} />

      {/* Filters */}
      <div className="inline-flex rounded-lg border border-border bg-surface p-1 mb-6" role="tablist" aria-label={t('message.channel')}>
        {FILTERS.map((f) => (
          <button
            key={f}
            role="tab"
            aria-selected={filter === f}
            onClick={() => setFilter(f)}
            className={cn(
              'px-4 h-9 rounded-md text-sm font-semibold transition-colors touch-target',
              filter === f ? 'bg-primary text-white' : 'text-muted-fg hover:text-foreground',
            )}
          >
            {filterLabel(f)}
          </button>
        ))}
      </div>

      {error && <ErrorState message={t('errors.generic')} onRetry={() => conversationsQuery.refetch()} />}
      {loading && <SkeletonGrid count={3} />}

      {isEmpty && (
        <div className="bg-surface border border-border rounded-lg">
          <EmptyState
            icon={<MessageSquare className="h-7 w-7" aria-hidden />}
            title={t('message.empty')}
            hint={t('message.emptyHint')}
          />
        </div>
      )}

      {!loading && !error && conversations.length > 0 && (
        <ul className="bg-surface border border-border rounded-lg divide-y divide-border">
          {conversations.map((conv) => (
            <li key={conv.id}>
              <Link
                to={`/messages/${conv.id}`}
                onClick={() => open(conv.id, conv.unread)}
                className="flex items-center gap-4 p-4 hover:bg-muted transition-colors"
              >
                <div className={cn('h-11 w-11 rounded-xl flex items-center justify-center shrink-0', channelTone(conv.channel))}>
                  {conv.channel === 'WHATSAPP' ? (
                    <MessageCircle className="h-5 w-5" aria-hidden />
                  ) : (
                    <Smartphone className="h-5 w-5" aria-hidden />
                  )}
                </div>

                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2">
                    <p className={cn('truncate', conv.unread ? 'font-bold text-heading' : 'font-semibold text-foreground')}>
                      {conv.vehicleNickname ?? t('message.conversation')}
                    </p>
                    {conv.unread && <span className="h-2 w-2 rounded-full bg-primary shrink-0" aria-hidden />}
                  </div>
                  <p className={cn('truncate text-sm', conv.unread ? 'text-foreground' : 'text-muted-fg')}>
                    {conv.lastMessagePreview ?? t('message.pending')}
                  </p>
                </div>

                <div className="text-right shrink-0 space-y-1.5">
                  <ConversationStatusBadge status={conv.status} />
                  <p className="text-xs text-muted-fg">{formatRelative(conv.createdAt)}</p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )

  function filterLabel(f: Filter) {
    switch (f) {
      case 'ALL':
        return t('common.all')
      case 'UNREAD':
        return t('message.unread')
      case 'WHATSAPP':
        return t('contact.whatsapp')
      case 'SMS':
        return t('contact.sms')
    }
  }
}

function channelTone(channel: ContactChannel) {
  return channel === 'WHATSAPP' ? 'bg-success-soft text-whatsapp' : 'bg-primary-soft text-sms'
}
