import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { CarFront, ChevronRight, MessageSquareText, Plus, QrCode, Search, X } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { PlateChip } from '@/components/shared/PlateChip'
import { buttonClasses } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { ErrorState } from '@/components/shared/ErrorState'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import { useAccountStatus } from '@/features/account/useAccountStatus'
import { InlineEmpty, VehicleAvatar } from '@/features/account/OwnerUI'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/cn'
import type { VehicleResponse, VehicleStatus } from '@/types'

export default function VehiclesPage() {
  const { t } = useTranslation()
  const status = useAccountStatus()
  const [search, setSearch] = useState('')
  const [tab, setTab] = useState<VehicleStatus>('ACTIVE')

  const vehiclesQuery = useQuery({
    queryKey: vehicleQueryKeys.list(tab),
    queryFn: () => vehicleApi.list(tab),
  })

  // Per-vehicle activity from data the shell already loads.
  const activity = useMemo(() => {
    const map = new Map<string, { total: number; unread: number }>()
    for (const c of status.conversations.data ?? []) {
      const a = map.get(c.vehicleId) ?? { total: 0, unread: 0 }
      a.total += 1
      if (c.unread) a.unread += 1
      map.set(c.vehicleId, a)
    }
    return map
  }, [status.conversations.data])
  const linked = useMemo(
    () => new Set((status.stickers.data ?? []).filter((s) => s.status === 'BOUND').map((s) => s.vehicle?.id)),
    [status.stickers.data],
  )

  const vehicles = useMemo(() => {
    const term = search.trim().toLowerCase()
    return (vehiclesQuery.data ?? [])
      .filter(
        (v) =>
          !term ||
          [v.nickname, v.brand, v.model, v.licensePlate, v.color].some((x) => (x ?? '').toLowerCase().includes(term)),
      )
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }, [vehiclesQuery.data, search])

  return (
    <div className="motion-stagger space-y-6">
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

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div role="group" aria-label={t('vehicle.status')} className="inline-flex self-start gap-1 rounded-xl bg-muted/60 p-1">
          {(['ACTIVE', 'ARCHIVED'] as VehicleStatus[]).map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => setTab(s)}
              aria-pressed={tab === s}
              className={cn(
                'h-9 px-4 rounded-lg text-sm font-semibold transition-colors',
                tab === s ? 'bg-surface text-heading shadow-sm' : 'text-muted-fg hover:text-foreground',
              )}
            >
              {s === 'ACTIVE' ? t('vehicle.active') : t('vehicle.archived')}
            </button>
          ))}
        </div>
        <div className="relative w-full sm:max-w-xs">
          <Search className="pointer-events-none absolute start-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-fg" aria-hidden />
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder={t('vehicle.searchPlaceholder')}
            aria-label={t('vehicle.searchPlaceholder')}
            className="w-full h-11 rounded-lg border border-input-border bg-surface ps-9 pe-10 text-sm placeholder:text-muted-fg focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent [&::-webkit-search-cancel-button]:hidden"
          />
          {search && (
            <button
              type="button"
              onClick={() => setSearch('')}
              aria-label={t('admin.list.clearSearch')}
              className="absolute end-1 top-1/2 -translate-y-1/2 h-9 w-9 rounded-md flex items-center justify-center text-muted-fg hover:bg-muted"
            >
              <X className="h-4 w-4" aria-hidden />
            </button>
          )}
        </div>
      </div>

      {vehiclesQuery.error && <ErrorState message={t('errors.generic')} onRetry={() => vehiclesQuery.refetch()} />}

      {vehiclesQuery.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-48 rounded-2xl border border-border bg-surface p-5 space-y-4">
              <div className="flex gap-3">
                <div className="h-12 w-12 rounded-xl bg-muted animate-pulse" />
                <div className="flex-1 space-y-2 pt-1">
                  <div className="h-4 w-2/3 rounded bg-muted animate-pulse" />
                  <div className="h-3 w-1/2 rounded bg-muted animate-pulse" />
                </div>
              </div>
              <div className="h-6 w-24 rounded bg-muted animate-pulse" />
            </div>
          ))}
        </div>
      ) : vehicles.length === 0 && !vehiclesQuery.error ? (
        <InlineEmpty
          icon={CarFront}
          title={search ? t('vehicle.noMatch') : tab === 'ARCHIVED' ? t('vehicle.emptyArchived') : t('vehicle.noVehicles')}
          hint={search ? t('admin.list.noResultsHint') : tab === 'ACTIVE' ? t('vehicle.noVehiclesHint') : undefined}
          action={
            search ? (
              <button type="button" onClick={() => setSearch('')} className={buttonClasses({ variant: 'outline', size: 'sm' })}>
                {t('admin.list.clearSearch')}
              </button>
            ) : tab === 'ACTIVE' ? (
              <Link to="/vehicles/new" className={buttonClasses()}>
                <Plus className="h-4 w-4" aria-hidden />
                {t('vehicle.add')}
              </Link>
            ) : undefined
          }
        />
      ) : (
        <ul className="motion-stagger grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {vehicles.map((v) => (
            <li key={v.id}>
              <VehicleTile vehicle={v} activity={activity.get(v.id)} hasSticker={linked.has(v.id)} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function VehicleTile({
  vehicle: v,
  activity,
  hasSticker,
}: {
  vehicle: VehicleResponse
  activity?: { total: number; unread: number }
  hasSticker: boolean
}) {
  const { t } = useTranslation()
  const title = v.nickname || [v.brand, v.model].filter(Boolean).join(' ') || v.licensePlate
  return (
    <Link
      to={`/vehicles/${v.id}`}
      className="hover-lift group h-full bg-surface border border-border rounded-2xl shadow-card hover:border-primary/40 flex flex-col focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
    >
      <div className="p-5 flex-1">
        <div className="flex items-start gap-3">
          <VehicleAvatar color={v.color} size="lg" />
          <div className="min-w-0 flex-1">
            <div className="flex items-start justify-between gap-2">
              <h3 className="font-bold text-heading truncate leading-snug">{title}</h3>
              {v.status === 'ARCHIVED' && <Badge tone="neutral">{t('vehicle.archived')}</Badge>}
            </div>
            <p className="text-sm text-muted-fg mt-1 truncate">
              {[v.nickname ? [v.brand, v.model].filter(Boolean).join(' ') : null, v.color].filter(Boolean).join(' · ') || '—'}
            </p>
          </div>
        </div>
        <div className="mt-4 flex items-center justify-between gap-3">
          <PlateChip plate={v.licensePlate} />
          <span className="text-xs text-muted-fg">{t('vehicle.addedOn', { date: formatDate(v.createdAt) })}</span>
        </div>
      </div>
      <div className="px-5 py-3 border-t border-border flex items-center gap-4 text-sm">
        <span className={cn('inline-flex items-center gap-1.5', hasSticker ? 'text-success' : 'text-muted-fg')}>
          <QrCode className="h-4 w-4" aria-hidden />
          {hasSticker ? t('dashboard.stickerLinked') : t('dashboard.noSticker')}
        </span>
        <span className="inline-flex items-center gap-1.5 text-muted-fg">
          <MessageSquareText className="h-4 w-4" aria-hidden />
          {activity?.total ?? 0}
          {activity && activity.unread > 0 && <Badge tone="warning">{t('admin.fleet.unreadCount', { count: activity.unread })}</Badge>}
        </span>
        <ChevronRight className="nudge ms-auto h-4 w-4 text-muted-fg group-hover:text-primary rtl:rotate-180" aria-hidden />
      </div>
    </Link>
  )
}
