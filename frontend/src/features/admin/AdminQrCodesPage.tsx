import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { QrCode, Info } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { adminApi, adminQueryKeys } from '@/services/adminApi'

export default function AdminQrCodesPage() {
  const { t } = useTranslation()

  const stats = useQuery({
    queryKey: adminQueryKeys.stats,
    queryFn: () => adminApi.stats.overview(),
  })

  if (stats.isError) return <ErrorState message={t('errors.generic')} onRetry={() => stats.refetch()} />

  const s = stats.data

  return (
    <div>
      <PageHeader title={t('admin.qrCodes')} subtitle={t('admin.subtitle')} />

      <div className="flex items-start gap-3 rounded-xl border border-primary/30 bg-primary-soft/50 p-4 mb-6">
        <Info className="h-4 w-4 text-primary shrink-0 mt-0.5" aria-hidden />
        <p className="text-sm text-foreground">{t('admin.qrNote')}</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
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
            <StatCard icon={<QrCode className="h-5 w-5" aria-hidden />} tone="bg-warning-soft text-warning" label={t('admin.statQr')} value={s.qrTotal} />
            <StatCard icon={<QrCode className="h-5 w-5" aria-hidden />} tone="bg-success-soft text-success" label={t('admin.statActiveQr')} value={s.qrActive} />
          </>
        )}
      </div>
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
