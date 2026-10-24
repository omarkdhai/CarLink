import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MessageSquare, Smartphone } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { SkeletonGrid } from '@/components/shared/SkeletonGrid'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import { ConversationStatusBadge } from '@/features/conversations/ConversationBadges'
import { cn } from '@/lib/cn'
import { formatRelative } from '@/lib/format'
import type { ContactChannel } from '@/types'

type Filter = 'ALL' | 'UNREAD' | 'SMS'
const FILTERS: Filter[] = ['ALL', 'UNREAD', 'SMS']

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
    .filter((c) => (filter === 'SMS' ? c.channel === filter : true))
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
      <div className="mb-6">
        <PageHeader title={t('message.title')} subtitle={t('message.subtitle')} />
      </div>

      {/* Filters - mobile scrollable */}
      <div className="mb-6 -mx-3 px-3 sm:mx-0 sm:px-0 overflow-x-auto">
        <div className="inline-flex rounded-xl border border-border bg-surface p-1 min-w-max" role="tablist" aria-label={t('message.channel')}>
          {FILTERS.map((f) => (
            <button
              key={f}
              role="tab"
              aria-selected={filter === f}
              onClick={() => setFilter(f)}
              className={cn(
                'px-4 h-10 rounded-lg text-sm font-semibold transition-colors touch-target whitespace-nowrap',
                filter === f ? 'bg-primary text-white shadow-sm' : 'text-muted-fg hover:text-foreground hover:bg-muted/50',
              )}
            >
              {filterLabel(f)}
            </button>
          ))}
        </div>
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
        <ul className="bg-surface border border-border rounded-xl shadow-sm divide-y divide-border overflow-hidden">
          {conversations.map((conv) => (
            <li key={conv.id}>
              <Link
                to={`/messages/${conv.id}`}
                onClick={() => open(conv.id, conv.unread)}
                className="flex items-center gap-3 sm:gap-4 p-3 sm:p-4 hover:bg-muted/50 transition-colors touch-target"
              >
                <div className={cn('h-10 w-10 sm:h-11 sm:w-11 rounded-xl flex items-center justify-center shrink-0', channelTone(conv.channel))}>
                  <Smartphone className="h-4 w-4 sm:h-5 sm:w-5" aria-hidden />
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
      case 'SMS':
        return t('contact.sms')
    }
  }
}

function channelTone(_channel: ContactChannel) {
  return 'bg-primary-soft text-sms'
}
