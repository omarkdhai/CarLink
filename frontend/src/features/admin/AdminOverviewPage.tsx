import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Users, Car, QrCode, MessageSquare, Flag, ScrollText } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { adminApi, adminQueryKeys } from '@/services/adminApi'

export default function AdminOverviewPage() {
  const { t } = useTranslation()

  const stats = useQuery({
    queryKey: adminQueryKeys.stats,
    queryFn: () => adminApi.stats.overview(),
  })

  if (stats.isError) {
    return <ErrorState message={t('errors.generic')} onRetry={() => stats.refetch()} />
  }

  if (stats.isPending || !stats.data) {
    return (
      <div>
        <PageHeader title={t('admin.overview')} subtitle={t('admin.subtitle')} />
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="bg-surface border border-border rounded-lg p-5 space-y-3">
              <div className="h-11 w-11 rounded-lg bg-muted animate-pulse" />
              <div className="h-8 w-16 rounded bg-muted animate-pulse" />
              <div className="h-4 w-1/2 rounded bg-muted animate-pulse" />
            </div>
          ))}
        </div>
      </div>
    )
  }

  const s = stats.data

  return (
    <div>
      <PageHeader title={t('admin.overview')} subtitle={t('admin.subtitle')} />

      {/* Users */}
      <Section title={t('admin.statUsers')}>
        <Stat icon={<Users className="h-5 w-5" aria-hidden />} tone="bg-primary-soft text-primary" label={t('admin.statUsers')} value={s.usersTotal} />
        <Stat icon={<Users className="h-5 w-5" aria-hidden />} tone="bg-success-soft text-success" label={t('admin.statActiveUsers')} value={s.usersActive} />
        <Stat icon={<Users className="h-5 w-5" aria-hidden />} tone="bg-accent-soft text-accent" label={t('admin.statAdmins')} value={s.usersAdmins} />
      </Section>

      {/* Vehicles + QR */}
      <Section title={t('admin.statVehicles')}>
        <Stat icon={<Car className="h-5 w-5" aria-hidden />} tone="bg-primary-soft text-primary" label={t('admin.statVehicles')} value={s.vehiclesTotal} />
        <Stat icon={<Car className="h-5 w-5" aria-hidden />} tone="bg-success-soft text-success" label={t('admin.statActiveVehicles')} value={s.vehiclesActive} />
        <Stat icon={<QrCode className="h-5 w-5" aria-hidden />} tone="bg-warning-soft text-warning" label={t('admin.statQr')} value={s.qrTotal} />
        <Stat icon={<QrCode className="h-5 w-5" aria-hidden />} tone="bg-success-soft text-success" label={t('admin.statActiveQr')} value={s.qrActive} />
      </Section>

      {/* Conversations + messages */}
      <Section title={t('admin.statConversations')}>
        <Stat icon={<MessageSquare className="h-5 w-5" aria-hidden />} tone="bg-primary-soft text-primary" label={t('admin.statConversations')} value={s.conversationsTotal} />
        <Stat icon={<MessageSquare className="h-5 w-5" aria-hidden />} tone="bg-warning-soft text-warning" label={t('admin.statestPending')} value={s.conversationsPending} />
        <Stat icon={<MessageSquare className="h-5 w-5" aria-hidden />} tone="bg-success-soft text-success" label={t('admin.statSent')} value={s.conversationsSent} />
        <Stat icon={<MessageSquare className="h-5 w-5" aria-hidden />} tone="bg-destructive-soft text-destructive" label={t('admin.statFailed')} value={s.conversationsFailed} />
        <Stat icon={<MessageSquare className="h-5 w-5" aria-hidden />} tone="bg-muted text-muted-fg" label={t('admin.statExpired')} value={s.conversationsExpired} />
        <Stat icon={<ScrollText className="h-5 w-5" aria-hidden />} tone="bg-primary-soft text-primary" label={t('admin.statMessages')} value={s.messagesTotal} />
      </Section>

      {/* Reports */}
      <Section title={t('admin.statReportsOpen')}>
        <Stat icon={<Flag className="h-5 w-5" aria-hidden />} tone="bg-destructive-soft text-destructive" label={t('admin.reportStatus.OPEN')} value={s.reportsOpen} />
        <Stat icon={<Flag className="h-5 w-5" aria-hidden />} tone="bg-warning-soft text-warning" label={t('admin.reportStatus.REVIEWED')} value={s.reportsReviewed} />
        <Stat icon={<Flag className="h-5 w-5" aria-hidden />} tone="bg-success-soft text-success" label={t('admin.reportStatus.CLOSED')} value={s.reportsClosed} />
      </Section>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="mb-8">
      <h2 className="text-lg font-bold text-heading mb-3">{title}</h2>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{children}</div>
    </section>
  )
}

function Stat({
  icon,
  tone,
  label,
  value,
}: {
  icon: React.ReactNode
  tone: string
  label: string
  value: number
}) {
  return (
    <div className="bg-surface border border-border rounded-lg p-5">
      <div className={`h-11 w-11 rounded-lg flex items-center justify-center mb-4 ${tone}`}>{icon}</div>
      <p className="text-3xl font-bold text-heading tabular-nums">{value}</p>
      <p className="text-sm text-muted-fg mt-1">{label}</p>
    </div>
  )
}
