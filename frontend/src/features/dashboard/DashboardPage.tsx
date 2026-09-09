import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Car, MessageSquare, MailOpen, Plus, QrCode, Inbox, ArrowRight } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { buttonClasses } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { SkeletonGrid } from '@/components/shared/SkeletonGrid'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import type { ConversationStatus, ContactChannel } from '@/types'

const STATUS_TONE: Record<ConversationStatus, 'success' | 'warning' | 'danger' | 'neutral'> = {
  SENT: 'success',
  PENDING: 'warning',
  FAILED: 'danger',
  EXPIRED: 'neutral',
}

export default function DashboardPage() {
  const { t } = useTranslation()

  const vehiclesQuery = useQuery({
    queryKey: vehicleQueryKeys.list('ACTIVE'),
    queryFn: () => vehicleApi.list('ACTIVE'),
  })
  const conversationsQuery = useQuery({
    queryKey: conversationQueryKeys.list(),
    queryFn: () => conversationApi.list(),
  })

  const loading = vehiclesQuery.isPending || conversationsQuery.isPending
  const error = vehiclesQuery.error ?? conversationsQuery.error
  const vehicles = vehiclesQuery.data ?? []
  const conversations = conversationsQuery.data ?? []
  const unread = conversations.filter((c) => c.unread).length

  const recent = conversations.slice(0, 5)

  return (
    <div>
      <PageHeader
        title={t('dashboard.title')}
        subtitle={t('dashboard.subtitle')}
        actions={
          <Link to="/vehicles/new" className={buttonClasses()}>
            <Plus className="h-4 w-4" aria-hidden />
            {t('vehicle.add')}
          </Link>
        }
      />

      {error && <ErrorState message={t('errors.generic')} onRetry={() => vehiclesQuery.refetch()} />}

      {/* Stat cards */}
      <div className="grid gap-4 sm:grid-cols-3 mb-8">
        <StatCard
          icon={<Car className="h-5 w-5" aria-hidden />}
          tone="bg-primary-soft text-primary"
          label={t('dashboard.totalVehicles')}
          value={vehicles.length}
          loading={loading}
        />
        <StatCard
          icon={<MessageSquare className="h-5 w-5" aria-hidden />}
          tone="bg-success-soft text-success"
          label={t('dashboard.messages')}
          value={conversations.length}
          loading={loading}
        />
        <StatCard
          icon={<MailOpen className="h-5 w-5" aria-hidden />}
          tone="bg-warning-soft text-warning"
          label={t('dashboard.unread')}
          value={unread}
          loading={loading}
        />
      </div>

      {/* Quick actions */}
      <section className="mb-10">
        <h2 className="text-lg font-bold text-heading mb-3">{t('dashboard.quickActions')}</h2>
        <div className="grid gap-3 sm:grid-cols-3">
          <QuickAction
            to="/vehicles"
            icon={<Car className="h-5 w-5" aria-hidden />}
            title={t('dashboard.addVehicle')}
          />
          <QuickAction to="/vehicles" icon={<QrCode className="h-5 w-5" aria-hidden />} title={t('dashboard.generateQr')} />
          <QuickAction to="/messages" icon={<Inbox className="h-5 w-5" aria-hidden />} title={t('dashboard.viewMessages')} />
        </div>
      </section>

      {/* Recent conversations */}
      <section>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-lg font-bold text-heading">{t('dashboard.recentConversations')}</h2>
          {conversations.length > 0 && (
            <Link to="/messages" className="inline-flex items-center gap-1 text-sm font-semibold text-primary hover:text-primary-hover">
              {t('dashboard.viewAll')}
              <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
          )}
        </div>

        {loading ? (
          <SkeletonGrid count={3} />
        ) : recent.length === 0 ? (
          <div className="bg-surface border border-border rounded-lg">
            <EmptyState
              icon={<MessageSquare className="h-7 w-7" aria-hidden />}
              title={t('dashboard.noConversationsYet')}
              hint={t('dashboard.noConversationsWait')}
            />
          </div>
        ) : (
          <ul className="bg-surface border border-border rounded-lg divide-y divide-border">
            {recent.map((conv) => (
              <li key={conv.id}>
                <Link
                  to={`/messages/${conv.id}`}
                  className="flex items-center gap-4 p-4 hover:bg-muted transition-colors"
                >
                  <div className={`h-10 w-10 rounded-lg flex items-center justify-center shrink-0 ${channelTone(conv.channel)}`}>
                    <MessageSquare className="h-5 w-5" aria-hidden />
                  </div>
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className="font-semibold text-heading truncate">
                        {conv.vehicleNickname ?? t('vehicle.title')}
                      </p>
                      {conv.unread && <span className="h-2 w-2 rounded-full bg-primary shrink-0" aria-hidden />}
                    </div>
                    <p className="text-sm text-muted-fg truncate">
                      {conv.lastMessagePreview ?? t('message.pending')}
                    </p>
                  </div>
                  <div className="text-right shrink-0 space-y-1">
                    <Badge tone={STATUS_TONE[conv.status]}>{t(`message.${conv.status.toLowerCase()}`)}</Badge>
                    <p className="text-xs text-muted-fg">{formatDate(conv.createdAt)}</p>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <p className="text-sm text-muted-fg mt-8">{t('dashboard.scanStatNote')}</p>
    </div>
  )
}

function StatCard({
  icon,
  tone,
  label,
  value,
  loading,
}: {
  icon: React.ReactNode
  tone: string
  label: string
  value: number
  loading: boolean
}) {
  return (
    <div className="bg-surface border border-border rounded-lg p-5">
      <div className={`h-11 w-11 rounded-lg flex items-center justify-center mb-4 ${tone}`}>{icon}</div>
      {loading ? (
        <div className="h-8 w-16 rounded bg-muted animate-pulse" />
      ) : (
        <p className="text-3xl font-bold text-heading tabular-nums">{value}</p>
      )}
      <p className="text-sm text-muted-fg mt-1">{label}</p>
    </div>
  )
}

function QuickAction({ to, icon, title }: { to: string; icon: React.ReactNode; title: string }) {
  return (
    <Link
      to={to}
      className="group flex items-center gap-3 rounded-lg border border-border bg-surface p-4 hover:border-primary hover:shadow-card transition-all"
    >
      <span className="h-11 w-11 rounded-lg bg-primary-soft text-primary flex items-center justify-center group-hover:bg-primary group-hover:text-white transition-colors">
        {icon}
      </span>
      <span className="font-semibold text-heading">{title}</span>
    </Link>
  )
}

function channelTone(channel: ContactChannel) {
  return channel === 'WHATSAPP' ? 'bg-success-soft text-whatsapp' : 'bg-primary-soft text-sms'
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(iso))
}
