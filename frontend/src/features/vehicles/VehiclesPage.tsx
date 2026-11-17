import { useState, useMemo } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Plus, CarFront } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { buttonClasses } from '@/components/ui/Button'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { SkeletonGrid } from '@/components/shared/SkeletonGrid'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { Input } from '@/components/ui/Input'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import type { VehicleResponse, VehicleStatus } from '@/types'

function formatDate(value: string, locale: string) {
  return new Date(value).toLocaleString(locale, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

// Map vehicle status to badge tone – reuse pattern from orders page
const STATUS_TONE: Record<VehicleStatus, BadgeTone> = {
  ACTIVE: 'success',
  ARCHIVED: 'danger',
}

export default function VehiclesPage() {
  const { t, i18n } = useTranslation()
  const [search, setSearch] = useState('')
  const [status, setStatus] = useState<VehicleStatus | ''>('')
  const [sort, setSort] = useState('createdAt,desc')

  const vehiclesQuery = useQuery({
    queryKey: vehicleQueryKeys.list(status || undefined),
    queryFn: () => vehicleApi.list(status || undefined),
  })

  const loading = vehiclesQuery.isPending
  const error = vehiclesQuery.error
  const vehicles = useMemo(() => vehiclesQuery.data ?? [], [vehiclesQuery.data])

  // Apply client‑side search & sorting
  const filtered = useMemo(() => {
    let list = vehicles
    if (search) {
      const term = search.toLowerCase()
      list = list.filter(v =>
        (v.nickname ?? '').toLowerCase().includes(term) ||
        (v.brand ?? '').toLowerCase().includes(term) ||
        (v.model ?? '').toLowerCase().includes(term) ||
        v.licensePlate.toLowerCase().includes(term)
      )
    }
    const [field, dir] = sort.split(',') as [keyof VehicleResponse, 'asc' | 'desc']
    list = [...list].sort((a, b) => {
      const av = a[field] as any
      const bv = b[field] as any
      if (av < bv) return dir === 'asc' ? -1 : 1
      if (av > bv) return dir === 'asc' ? 1 : -1
      return 0
    })
    return list
  }, [vehicles, search, sort])

  return (
    <div>
      <PageHeader
        title={t('vehicle.title')}
        subtitle={t('vehicle.subtitle')}
        actions={
          <Link
            to="/vehicles/new"
            className={buttonClasses({ size: 'sm' }) + ' sm:h-10 sm:px-4'}
          >
            <Plus className="h-4 w-4" aria-hidden />
            <span className="hidden sm:inline">{t('vehicle.add')}</span>
          </Link>
        }
      />

      {/* Filters */}
      <div className="flex flex-wrap gap-2 mb-4 items-center">
        <Input
          value={search}
          onChange={e => setSearch(e.target.value)}
          placeholder={t('vehicle.searchPlaceholder')}
          className="flex-1 max-w-sm"
        />
        <select
          value={status}
          onChange={e => setStatus(e.target.value as VehicleStatus | '')}
          className="px-3 py-2 border border-input-border rounded-lg bg-surface hover:border-primary/50 focus-visible:border-primary"
        >
          <option value="">{t('vehicle.allStatuses')}</option>
          {['ACTIVE', 'ARCHIVED'].map(s => (
            <option key={s} value={s}>
              {s === 'ACTIVE' ? t('vehicle.active') : t('vehicle.archived')}
            </option>
          ))}
        </select>
        <select
          value={sort}
          onChange={e => setSort(e.target.value)}
          className="px-3 py-2 border border-input-border rounded-lg bg-surface hover:border-primary/50 focus-visible:border-primary"
        >
          <option value="createdAt,desc">{t('vehicle.sortNewest')}</option>
          <option value="createdAt,asc">{t('vehicle.sortOldest')}</option>
        </select>
      </div>

      {error && <ErrorState message={t('errors.generic')} onRetry={() => vehiclesQuery.refetch()} />}
      {loading && <SkeletonGrid count={3} />}

      {filtered.length === 0 && !loading && !error && status === 'ARCHIVED' && !search && (
        <EmptyState icon={<CarFront className="h-7 w-7" aria-hidden />} title={t('vehicle.emptyArchived')} />
      )}

      {filtered.length === 0 && !loading && !error && search && (
        <EmptyState icon={<CarFront className="h-7 w-7" aria-hidden />} title={t('vehicle.noMatch')} />
      )}

      {filtered.length === 0 && !loading && !error && status !== 'ARCHIVED' && !search && (
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
      )}

      {filtered.length > 0 && (
        <div className="overflow-x-auto bg-surface border border-border rounded-xl">
          <table className="w-full text-sm">
            <thead className="bg-muted/40">
              <tr>
                <th className="text-start p-3">{t('vehicle.nickname')}</th>
                <th className="text-start p-3">{t('vehicle.brandModel')}</th>
                <th className="text-start p-3">{t('vehicle.licensePlate')}</th>
                <th className="text-start p-3">{t('vehicle.color')}</th>
                <th className="text-start p-3">{t('vehicle.status')}</th>
                <th className="text-start p-3">{t('vehicle.created')}</th>
                <th className="text-start p-3">{t('vehicle.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((v, i) => (
                <tr key={v.id} className="border-t border-border hover:bg-muted/30" style={{ '--stagger': i } as React.CSSProperties}>
                  <td className="p-3 font-medium text-heading whitespace-nowrap">
                    {v.nickname ?? `${v.brand ?? ''} ${v.model ?? ''}`.trim()}
                  </td>
                  <td className="p-3 whitespace-nowrap">
                    {v.brand ?? ''} {v.model ?? ''}
                  </td>
                  <td className="p-3 whitespace-nowrap">
                    {v.licensePlate}
                  </td>
                  <td className="p-3 whitespace-nowrap">
                    {v.color ?? '—'}
                  </td>
                  <td className="p-3 whitespace-nowrap">
                    <Badge tone={STATUS_TONE[v.status] ?? 'neutral'}>{v.status === 'ACTIVE' ? t('vehicle.active') : t('vehicle.archived')}</Badge>
                  </td>
                  <td className="p-3 whitespace-nowrap text-muted-fg">
                    {formatDate(v.createdAt, i18n.language)}
                  </td>
                  <td className="p-3 whitespace-nowrap">
                    <Link
                      to={`/vehicles/${v.id}`}
                      className={buttonClasses({ size: 'sm', variant: 'outline' })}
                    >
                      {t('common.view')}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
