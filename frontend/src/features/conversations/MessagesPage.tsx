import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { MessageSquare, ShieldCheck } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import { useAccountStatus } from '@/features/account/useAccountStatus'
import { ConversationRow, InlineEmpty } from '@/features/account/OwnerUI'
import { cn } from '@/lib/cn'
import type { ConversationSummaryResponse } from '@/types'

type Filter = 'ALL' | 'UNREAD'

function dayGroup(iso: string): 'today' | 'yesterday' | 'earlier' {
  const d = new Date(iso)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)
  if (d.toDateString() === today.toDateString()) return 'today'
  if (d.toDateString() === yesterday.toDateString()) return 'yesterday'
  return 'earlier'
}

export default function MessagesPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const status = useAccountStatus()
  const [filter, setFilter] = useState<Filter>('ALL')
  const [vehicle, setVehicle] = useState('')

  const markRead = useMutation({
    mutationFn: (id: string) => conversationApi.markRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: conversationQueryKeys.all }),
  })

  const all = status.conversations.data ?? []
  const vehicles = useMemo(() => {
    const m = new Map<string, string>()
    for (const c of all) m.set(c.vehicleId, c.vehicleNickname ?? t('message.conversation'))
    return [...m.entries()]
  }, [all, t])

  const groups = useMemo(() => {
    const list = all
      .filter((c) => (filter === 'UNREAD' ? c.unread : true))
      .filter((c) => (vehicle ? c.vehicleId === vehicle : true))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    const out: Record<'today' | 'yesterday' | 'earlier', ConversationSummaryResponse[]> = { today: [], yesterday: [], earlier: [] }
    for (const c of list) out[dayGroup(c.createdAt)].push(c)
    return out
  }, [all, filter, vehicle])

  const shown = groups.today.length + groups.yesterday.length + groups.earlier.length
  const loading = status.conversations.isPending
  const error = status.conversations.error

  return (
    <div className="space-y-6">
      <PageHeader title={t('message.title')} subtitle={t('message.subtitle')} />

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div role="group" aria-label={t('message.filter')} className="inline-flex self-start gap-1 rounded-xl bg-muted/60 p-1">
          {(['ALL', 'UNREAD'] as Filter[]).map((f) => {
            const count = f === 'ALL' ? all.length : status.unread
            return (
              <button
                key={f}
                type="button"
                onClick={() => setFilter(f)}
                aria-pressed={filter === f}
                className={cn(
                  'inline-flex items-center gap-2 h-9 px-4 rounded-lg text-sm font-semibold transition-colors',
                  filter === f ? 'bg-surface text-heading shadow-sm' : 'text-muted-fg hover:text-foreground',
                )}
              >
                {f === 'ALL' ? t('common.all') : t('message.unreadFilter')}
                <span className={cn('min-w-6 rounded-full px-1.5 text-xs tabular-nums', filter === f ? 'bg-primary-soft text-primary' : 'bg-surface/70')}>
                  {count}
                </span>
              </button>
            )
          })}
        </div>
        {vehicles.length > 1 && (
          <label className="flex items-center gap-2 text-sm">
            <span className="text-muted-fg">{t('message.vehicle')}</span>
            <select
              value={vehicle}
              onChange={(e) => setVehicle(e.target.value)}
              className="h-11 rounded-lg border border-input-border bg-surface px-3 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">{t('message.allVehicles')}</option>
              {vehicles.map(([id, name]) => (
                <option key={id} value={id}>
                  {name}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {error && <ErrorState message={t('errors.generic')} onRetry={() => status.conversations.refetch()} />}

      {loading ? (
        <div className="bg-surface border border-border rounded-2xl divide-y divide-border" aria-busy="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex items-center gap-3 px-5 py-4">
              <div className="h-10 w-10 rounded-xl bg-muted animate-pulse" />
              <div className="flex-1 space-y-2">
                <div className="h-3.5 w-1/2 rounded bg-muted animate-pulse" />
                <div className="h-3 w-3/4 rounded bg-muted animate-pulse" />
              </div>
            </div>
          ))}
        </div>
      ) : shown === 0 && !error ? (
        <InlineEmpty
          icon={MessageSquare}
          title={filter === 'UNREAD' ? t('message.allRead') : t('message.empty')}
          hint={filter === 'UNREAD' ? undefined : t('message.emptyHint')}
        />
      ) : (
        <div className="space-y-6">
          {(['today', 'yesterday', 'earlier'] as const).map(
            (g) =>
              groups[g].length > 0 && (
                <section key={g} aria-labelledby={`group-${g}`}>
                  <h2 id={`group-${g}`} className="text-xs font-semibold uppercase tracking-wide text-muted-fg mb-2 px-1">
                    {t(`message.groups.${g}`)}
                  </h2>
                  <ul className="bg-surface border border-border rounded-2xl shadow-card divide-y divide-border overflow-hidden">
                    {groups[g].map((conv) => (
                      <li key={conv.id}>
                        <ConversationRow conv={conv} onOpen={() => conv.unread && markRead.mutate(conv.id)} />
                      </li>
                    ))}
                  </ul>
                </section>
              ),
          )}
        </div>
      )}

      <p className="flex items-start gap-2 text-sm text-muted-fg leading-relaxed max-w-prose">
        <ShieldCheck className="h-4 w-4 mt-0.5 shrink-0 text-success" aria-hidden />
        {t('message.anonymousHint')}
      </p>
    </div>
  )
}
