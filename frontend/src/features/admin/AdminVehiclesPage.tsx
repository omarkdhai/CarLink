import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Car, Info } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { adminApi, adminQueryKeys } from '@/services/adminApi'
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, Legend } from 'recharts'

export default function AdminVehiclesPage() {
  const { t } = useTranslation()

  const stats = useQuery({
    queryKey: adminQueryKeys.stats,
    queryFn: () => adminApi.stats.overview(),
  })

  if (stats.isError) return <ErrorState message={t('errors.generic')} onRetry={() => stats.refetch()} />

  const s = stats.data

  const data = s ? [
    { name: t('admin.statActiveVehicles'), value: s.vehiclesActive },
    { name: t('admin.statVehicles') + ' (inactive)', value: Math.max(0, s.vehiclesTotal - s.vehiclesActive) },
  ] : []

  return (
    <div>
      <PageHeader title={t('admin.vehicles')} subtitle={t('admin.subtitle')} />

      <div className="flex items-start gap-3 rounded-xl border border-primary/30 bg-primary-soft/50 p-4 mb-6">
        <Info className="h-4 w-4 text-primary shrink-0 mt-0.5" aria-hidden />
        <p className="text-sm text-foreground">{t('admin.vehiclesNote')}</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 mb-8">
        {stats.isPending || !s ? (
          Array.from({ length: 2 }).map((_, i) => (
            <div key={i} className="bg-surface border border-border rounded-lg p-5 space-y-3">
              <div className="h-11 w-11 rounded-lg bg-muted animate-pulse" />
              <div className="h-8 w-16 rounded bg-muted animate-pulse" />
              <div className="h-4 w-1/2 rounded bg-muted animate-pulse" />
            </div>
          ))
        ) : (
          <>
            <StatCard icon={<Car className="h-5 w-5" aria-hidden />} tone="bg-primary-soft text-primary" label={t('admin.statVehicles')} value={s.vehiclesTotal} />
            <StatCard icon={<Car className="h-5 w-5" aria-hidden />} tone="bg-success-soft text-success" label={t('admin.statActiveVehicles')} value={s.vehiclesActive} />
          </>
        )}
      </div>

      {s && (
        <div className="bg-surface border border-border rounded-xl p-5 shadow-sm">
          <p className="text-sm font-semibold text-heading mb-4">Active vs Inactive Vehicles</p>
          <ResponsiveContainer width="100%" height={260}>
            <PieChart>
              <Pie data={data} cx="50%" cy="50%" outerRadius={90} dataKey="value" label={({ name, value }) => `${name}: ${value}`}>
                <Cell fill="#2563eb" />
                <Cell fill="#94a3b8" />
              </Pie>
              <Tooltip contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: '8px' }} />
              <Legend />
            </PieChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  )
}

function StatCard({ icon, tone, label, value }: { icon: React.ReactNode; tone: string; label: string; value: number }) {
  return (
    <div className="bg-surface border border-border rounded-lg p-5">
      <div className={`h-11 w-11 rounded-lg flex items-center justify-center mb-4 ${tone}`}>{icon}</div>
      <p className="text-3xl font-bold text-heading tabular-nums">{value}</p>
      <p className="text-sm text-muted-fg mt-1">{label}</p>
    </div>
  )
}
