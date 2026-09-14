import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, MessageCircle, Smartphone, Clock, Lock } from 'lucide-react'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import { ChannelBadge, ConversationStatusBadge } from '@/features/conversations/ConversationBadges'
import { ErrorState } from '@/components/shared/ErrorState'
import { formatDateTime, formatRelative } from '@/lib/format'
import type { ConversationDetailResponse } from '@/types'

export default function ConversationDetailPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams<{ id: string }>()
  const queryClient = useQueryClient()

  const detail = useQuery({
    queryKey: conversationQueryKeys.detail(id),
    queryFn: () => conversationApi.get(id),
    enabled: Boolean(id),
    retry: false,
  })

  const markRead = useMutation({
    mutationFn: (convId: string) => conversationApi.markRead(convId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: conversationQueryKeys.all }),
  })

  // Auto-mark read when an unread conversation is opened.
  useEffect(() => {
    if (detail.data?.unread) {
      markRead.mutate(detail.data.id)
      queryClient.setQueryData<ConversationDetailResponse>(conversationQueryKeys.detail(id), (prev) =>
        prev ? { ...prev, unread: false } : prev,
      )
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [detail.data?.id, detail.data?.unread])

  if (detail.isPending) {
    return (
      <div className="max-w-3xl mx-auto">
        <div className="bg-surface border border-border rounded-lg p-8 space-y-4">
          <div className="h-5 w-1/3 rounded bg-muted animate-pulse" />
          <div className="h-4 w-1/2 rounded bg-muted animate-pulse" />
          <div className="h-24 rounded-lg bg-muted animate-pulse" />
          <div className="h-24 rounded-lg bg-muted animate-pulse" />
        </div>
      </div>
    )
  }

  if (detail.isError) {
    return <ErrorState message={t('errors.notFound')} />
  }

  const conv = detail.data!
  const channelLabel = t(`contact.${conv.channel.toLowerCase()}`)

  return (
    <div className="max-w-3xl mx-auto">
      <Link
        to="/messages"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-fg hover:text-primary mb-4 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        {t('message.title')}
      </Link>

      {/* Conversation header */}
      <div className="bg-surface border border-border rounded-lg p-5 mb-4">
        <div className="flex flex-col sm:flex-row sm:items-center gap-3 justify-between">
          <div className="flex items-center gap-3 min-w-0">
            <div className="h-11 w-11 rounded-xl bg-primary-soft text-primary flex items-center justify-center shrink-0">
              {conv.channel === 'WHATSAPP' ? (
                <MessageCircle className="h-5 w-5" aria-hidden />
              ) : (
                <Smartphone className="h-5 w-5" aria-hidden />
              )}
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <p className="font-bold text-heading">{conv.vehicleNickname ?? t('message.conversation')}</p>
                <ChannelBadge channel={conv.channel} />
                <ConversationStatusBadge status={conv.status} />
              </div>
              <p className="text-sm text-muted-fg mt-1 flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5" aria-hidden />
                {t('message.expires')} {formatRelative(conv.expiresAt)}
              </p>
            </div>
          </div>
        </div>

        {conv.status === 'EXPIRED' && (
          <p className="mt-4 text-sm text-warning bg-warning-soft border border-warning/30 rounded-lg px-4 py-3">
            {t('message.conversationExpiredHint')}
          </p>
        )}
      </div>

      {/* Thread */}
      <div className="bg-surface border border-border rounded-lg p-5 space-y-3">
        <p className="text-xs font-bold text-heading uppercase tracking-wide mb-2">{t('message.from')}</p>
        {conv.messages.length === 0 ? (
          <p className="text-sm text-muted-fg">{t('message.pending')}</p>
        ) : (
          conv.messages.map((m) => (
            <div key={m.id} className="max-w-[85%] rounded-2xl rounded-tl-md bg-primary-soft text-foreground px-4 py-3">
              <p className="text-[15px] leading-relaxed whitespace-pre-wrap break-words">{m.content}</p>
              <p className="text-[11px] text-muted-fg mt-1.5">{formatDateTime(m.createdAt)}</p>
            </div>
          ))
        )}
      </div>

      {/* Anonymous reply note */}
      <div className="mt-4 flex items-start gap-3 rounded-xl border border-border bg-muted p-4">
        <Lock className="h-4 w-4 text-muted-fg shrink-0 mt-0.5" aria-hidden />
        <p className="text-sm text-muted-fg">{t('message.anonymousHint', { channel: channelLabel })}</p>
      </div>
    </div>
  )
}
