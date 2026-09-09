import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Car, Plus, CarFront } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { buttonClasses } from '@/components/ui/Button'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { SkeletonGrid } from '@/components/shared/SkeletonGrid'
import { Alert } from '@/components/ui/Alert'
import { VehicleCard } from '@/features/vehicles/VehicleCard'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import { cn } from '@/lib/cn'
import type { VehicleStatus } from '@/types'

type Tab = 'ACTIVE' | 'ALL' | 'ARCHIVED'
const TABS: Tab[] = ['ACTIVE', 'ALL', 'ARCHIVED']

export default function VehiclesPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<Tab>('ACTIVE')
  const [toast, setToast] = useState<string | null>(null)

  const statusFilter: VehicleStatus | 'ALL' = tab === 'ALL' ? 'ALL' : (tab as VehicleStatus)

  const vehiclesQuery = useQuery({
    queryKey: vehicleQueryKeys.list(statusFilter),
    queryFn: () => vehicleApi.list(statusFilter),
  })

  // Conversations (all) — used to compute per-vehicle message/unread counts.
  const conversationsQuery = useQuery({
    queryKey: conversationQueryKeys.list(),
    queryFn: () => conversationApi.list(),
  })

  const loading = vehiclesQuery.isPending || conversationsQuery.isPending
  const error = vehiclesQuery.error ?? conversationsQuery.error
  const vehicles = vehiclesQuery.data ?? []
  const conversations = conversationsQuery.data ?? []

  const counts = useCounts(conversations)
  const isEmpty = !loading && !error && vehicles.length === 0

  return (
    <div>
      <PageHeader
        title={t('vehicle.title')}
        subtitle={t('vehicle.subtitle')}
        actions={
          <Link to="/vehicles/new" className={buttonClasses()}>
            <Plus className="h-4 w-4" aria-hidden />
            {t('vehicle.add')}
          </Link>
        }
      />

      {toast && (
        <div className="mb-4">
          <Alert tone="error" title={t('common.error')}>
            {toast}
          </Alert>
        </div>
      )}

      {/* Status tabs */}
      <div className="inline-flex rounded-lg border border-border bg-surface p-1 mb-6" role="tablist" aria-label={t('vehicle.status')}>
        {TABS.map((value) => (
          <button
            key={value}
            role="tab"
            aria-selected={tab === value}
            onClick={() => setTab(value)}
            className={cn(
              'px-4 h-9 rounded-md text-sm font-semibold transition-colors touch-target',
              tab === value ? 'bg-primary text-white' : 'text-muted-fg hover:text-foreground',
            )}
          >
            {value === 'ACTIVE' ? t('vehicle.active') : value === 'ARCHIVED' ? t('vehicle.archived') : t('common.all')}
          </button>
        ))}
      </div>

      {error && <ErrorState message={t('errors.generic')} onRetry={() => vehiclesQuery.refetch()} />}

      {loading && <SkeletonGrid count={3} />}

      {isEmpty && (
        <div className="bg-surface border border-border rounded-lg">
          {tab === 'ACTIVE' ? (
            <EmptyState
              icon={<CarFront className="h-7 w-7" aria-hidden />}
              title={t('vehicle.noVehicles')}
              hint={t('vehicle.noVehiclesHint')}
              action={
                <Link to="/vehicles/new" className={buttonClasses()}>
                  <Plus className="h-4 w-4" aria-hidden />
                  {t('vehicle.add')}
                </Link>
              }
            />
          ) : (
            <EmptyState icon={<Car className="h-7 w-7" aria-hidden />} title={t('vehicle.emptyArchived')} />
          )}
        </div>
      )}

      {!loading && !error && vehicles.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {vehicles.map((vehicle) => (
            <VehicleCard
              key={vehicle.id}
              vehicle={vehicle}
              conversationCount={counts.get(vehicle.id)?.total}
              unreadCount={counts.get(vehicle.id)?.unread}
              onError={(m) => setToast(m)}
            />
          ))}
        </div>
      )}
    </div>
  )
}

function useCounts(conversations: Awaited<ReturnType<typeof conversationApi.list>>) {
  const map = new Map<string, { total: number; unread: number }>()
  for (const conv of conversations) {
    const entry = map.get(conv.vehicleId) ?? { total: 0, unread: 0 }
    entry.total += 1
    if (conv.unread) entry.unread += 1
    map.set(conv.vehicleId, entry)
  }
  return map
}