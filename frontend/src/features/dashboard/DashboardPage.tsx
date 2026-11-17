import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Car, Check, Mail, MailOpen, MessageSquare, Phone, Plus, QrCode, ShoppingBag, Link2 } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { PlateChip } from '@/components/shared/PlateChip'
import { buttonClasses } from '@/components/ui/Button'
import { ErrorState } from '@/components/shared/ErrorState'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import { useAccountStatus } from '@/features/account/useAccountStatus'
import { ConversationRow, InlineEmpty, SectionCard, StatTile, VehicleAvatar } from '@/features/account/OwnerUI'
import { cn } from '@/lib/cn'

export default function DashboardPage() {
  const { t } = useTranslation()
  const status = useAccountStatus()

  const vehiclesQuery = useQuery({
    queryKey: vehicleQueryKeys.list('ACTIVE'),
    queryFn: () => vehicleApi.list('ACTIVE'),
  })

  const conversations = status.conversations.data ?? []
  const vehicles = vehiclesQuery.data ?? []
  const loading = vehiclesQuery.isPending || status.conversations.isPending
  const error = vehiclesQuery.error ?? status.conversations.error
  const recent = conversations.slice(0, 5)
  const boundVehicleIds = new Set((status.stickers.data ?? []).filter((s) => s.status === 'BOUND').map((s) => s.vehicle?.id))

  return (
    <div className="space-y-6 sm:space-y-8">
      <PageHeader
        title={status.profile?.firstName ? t('dashboard.greeting', { name: status.profile.firstName }) : t('dashboard.title')}
        subtitle={t('dashboard.subtitle')}
        actions={
          <Link to="/vehicles/new" className={buttonClasses()}>
            <Plus className="h-4 w-4" aria-hidden />
            {t('vehicle.add')}
          </Link>
        }
      />

      {error && <ErrorState message={t('errors.generic')} onRetry={() => { vehiclesQuery.refetch(); status.conversations.refetch() }} />}

      <div className="grid grid-cols-3 gap-3 sm:gap-4">
        <StatTile to="/vehicles" icon={Car} tone="bg-primary-soft text-primary" value={vehicles.length} label={t('dashboard.totalVehicles')} loading={loading} />
        <StatTile to="/messages" icon={MessageSquare} tone="bg-success-soft text-success" value={conversations.length} label={t('dashboard.messages')} loading={loading} />
        <StatTile to="/messages" icon={MailOpen} tone="bg-warning-soft text-warning" value={status.unread} label={t('dashboard.unread')} loading={loading} />
      </div>

      {status.loaded && status.setupDone < status.setupTotal && <SetupChecklist status={status} />}

      <div className="grid gap-6 lg:grid-cols-3 lg:items-start">
        <SectionCard
          title={t('dashboard.recentConversations')}
          action={conversations.length > 0 ? { to: '/messages', label: t('dashboard.viewAll') } : undefined}
          className="lg:col-span-2"
          flush={!loading && recent.length > 0}
        >
          {loading ? (
            <RowsSkeleton />
          ) : recent.length === 0 ? (
            <InlineEmpty icon={MessageSquare} title={t('dashboard.noConversationsYet')} hint={t('dashboard.noConversationsWait')} />
          ) : (
            <ul className="divide-y divide-border border-t border-border">
              {recent.map((conv) => (
                <li key={conv.id}>
                  <ConversationRow conv={conv} />
                </li>
              ))}
            </ul>
          )}
        </SectionCard>

        <SectionCard
          title={t('nav.vehicles')}
          action={vehicles.length > 0 ? { to: '/vehicles', label: t('dashboard.viewAll') } : undefined}
        >
          {loading ? (
            <RowsSkeleton compact />
          ) : vehicles.length === 0 ? (
            <InlineEmpty
              icon={Car}
              title={t('vehicle.noVehicles')}
              action={
                <Link to="/vehicles/new" className={buttonClasses({ size: 'sm', variant: 'secondary' })}>
                  <Plus className="h-4 w-4" aria-hidden />
                  {t('vehicle.add')}
                </Link>
              }
            />
          ) : (
            <ul className="space-y-2">
              {vehicles.slice(0, 4).map((v) => (
                <li key={v.id}>
                  <Link
                    to={`/vehicles/${v.id}`}
                    className="flex items-center gap-3 rounded-xl border border-border p-3 hover:border-primary/40 hover:bg-primary-soft/30 transition-colors"
                  >
                    <VehicleAvatar color={v.color} />
                    <span className="min-w-0 flex-1">
                      <span className="block font-semibold text-heading truncate">
                        {v.nickname || [v.brand, v.model].filter(Boolean).join(' ') || v.licensePlate}
                      </span>
                      <span className="mt-1 flex items-center gap-2">
                        <PlateChip plate={v.licensePlate} />
                      </span>
                    </span>
                    <span
                      className={cn(
                        'shrink-0 h-8 w-8 rounded-full flex items-center justify-center',
                        boundVehicleIds.has(v.id) ? 'bg-success-soft text-success' : 'bg-muted text-muted-fg',
                      )}
                      title={boundVehicleIds.has(v.id) ? t('dashboard.stickerLinked') : t('dashboard.noSticker')}
                    >
                      <QrCode className="h-4 w-4" aria-hidden />
                      <span className="sr-only">
                        {boundVehicleIds.has(v.id) ? t('dashboard.stickerLinked') : t('dashboard.noSticker')}
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </SectionCard>
      </div>
    </div>
  )
}

/** Getting-started steps; hidden once everything is done. */
function SetupChecklist({ status }: { status: ReturnType<typeof useAccountStatus> }) {
  const { t } = useTranslation()
  const { steps } = status
  const items = [
    { done: steps.hasSticker, icon: ShoppingBag, key: 'order', to: '/order' },
    { done: steps.stickerLinked, icon: Link2, key: 'activate', to: '/stickers' },
    { done: steps.hasPhone, icon: Phone, key: 'phone', to: '/profile' },
    { done: steps.emailVerified, icon: Mail, key: 'email', to: '/profile' },
  ]
  const pct = Math.round((status.setupDone / status.setupTotal) * 100)

  return (
    <SectionCard
      title={t('dashboard.setup.title')}
      description={status.reachable ? t('dashboard.setup.almost') : t('dashboard.setup.notReachable')}
      className={cn(!status.reachable && 'border-warning/40')}
    >
      <div className="flex items-center gap-3 mb-5">
        <div className="h-2 flex-1 rounded-full bg-muted overflow-hidden" role="progressbar" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100}>
          <div className="h-full rounded-full bg-primary transition-[width] duration-500" style={{ width: `${pct}%` }} />
        </div>
        <span className="text-sm font-semibold text-heading tabular-nums">
          {status.setupDone}/{status.setupTotal}
        </span>
      </div>
      {/* Remaining steps as actionable cards… */}
      <ol className="grid gap-3 sm:grid-cols-2">
        {items
          .filter((i) => !i.done)
          .map(({ icon: Icon, key, to }) => (
            <li key={key}>
              <Link
                to={to}
                className="h-full flex items-start gap-3 rounded-xl border border-border p-4 transition-colors hover:border-primary/40 hover:bg-primary-soft/30"
              >
                <span className="h-10 w-10 shrink-0 rounded-xl bg-primary-soft text-primary flex items-center justify-center" aria-hidden>
                  <Icon className="h-5 w-5" />
                </span>
                <span className="min-w-0">
                  <span className="block font-semibold text-heading">{t(`dashboard.setup.${key}`)}</span>
                  <span className="block text-sm text-muted-fg mt-1 leading-relaxed">{t(`dashboard.setup.${key}Hint`)}</span>
                  <span className="sr-only">{t('dashboard.setup.todo')}</span>
                </span>
              </Link>
            </li>
          ))}
      </ol>
      {/* …finished ones as compact chips. */}
      {status.setupDone > 0 && (
        <ul className="mt-4 flex flex-wrap gap-2" aria-label={t('dashboard.setup.done')}>
          {items
            .filter((i) => i.done)
            .map(({ key }) => (
              <li key={key} className="inline-flex items-center gap-1.5 h-8 px-3 rounded-full bg-success-soft text-success text-xs font-semibold">
                <Check className="h-3.5 w-3.5" aria-hidden />
                {t(`dashboard.setup.${key}`)}
              </li>
            ))}
        </ul>
      )}
    </SectionCard>
  )
}

function RowsSkeleton({ compact }: { compact?: boolean }) {
  return (
    <div className={compact ? 'space-y-2' : 'divide-y divide-border border-t border-border'} aria-busy="true">
      {[0, 1, 2].map((i) => (
        <div key={i} className={cn('flex items-center gap-3', compact ? 'rounded-xl border border-border p-3' : 'px-5 py-4')}>
          <div className="h-10 w-10 rounded-xl bg-muted animate-pulse" />
          <div className="flex-1 space-y-2">
            <div className="h-3.5 w-2/3 rounded bg-muted animate-pulse" />
            <div className="h-3 w-1/3 rounded bg-muted animate-pulse" />
          </div>
        </div>
      ))}
    </div>
  )
}
