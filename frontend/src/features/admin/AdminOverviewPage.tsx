import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Users, Car, QrCode, Flag, ScrollText, ShoppingCart, DollarSign, TrendingUp, Activity } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { adminApi, adminQueryKeys } from '@/services/adminApi'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  LineChart, Line, PieChart, Pie, Cell, Legend,
} from 'recharts'

const COLORS = ['#2563eb', '#ea580c', '#22c55e', '#f59e0b', '#ef4444', '#8b5cf6']

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
  const convData = [
    { name: t('admin.statConversations'), value: s.conversationsTotal },
    { name: t('admin.statestPending'), value: s.conversationsPending },
    { name: t('admin.statSent'), value: s.conversationsSent },
    { name: t('admin.statFailed'), value: s.conversationsFailed },
    { name: t('admin.statExpired'), value: s.conversationsExpired },
  ]
  const orderData = [
    { name: 'Placed', value: s.ordersPlaced },
    { name: 'Delivered', value: s.ordersDelivered },
    { name: 'Cancelled', value: s.ordersCancelled },
  ]

  return (
    <div>
      <PageHeader title={t('admin.overview')} subtitle={t('admin.subtitle')} />

      <Section title={t('admin.statUsers')}>
        <Stat icon={<Users className="h-5 w-5" />} tone="bg-primary-soft text-primary" label={t('admin.statUsers')} value={s.usersTotal} />
        <Stat icon={<Users className="h-5 w-5" />} tone="bg-success-soft text-success" label={t('admin.statActiveUsers')} value={s.usersActive} />
        <Stat icon={<Users className="h-5 w-5" />} tone="bg-accent-soft text-accent" label={t('admin.statAdmins')} value={s.usersAdmins} />
        <Stat icon={<Activity className="h-5 w-5" />} tone="bg-primary-soft text-primary" label="Conversion Rate" value={Number(s.conversionRate || 0).toFixed(1) + '%'} />
      </Section>

      <Section title={t('admin.statVehicles')}>
        <Stat icon={<Car className="h-5 w-5" />} tone="bg-primary-soft text-primary" label={t('admin.statVehicles')} value={s.vehiclesTotal} />
        <Stat icon={<Car className="h-5 w-5" />} tone="bg-success-soft text-success" label={t('admin.statActiveVehicles')} value={s.vehiclesActive} />
        <Stat icon={<QrCode className="h-5 w-5" />} tone="bg-warning-soft text-warning" label={t('admin.statQr')} value={s.qrTotal} />
        <Stat icon={<QrCode className="h-5 w-5" />} tone="bg-success-soft text-success" label={t('admin.statActiveQr')} value={s.qrActive} />
      </Section>

      <Section title="Sales & Revenue">
        <Stat icon={<ShoppingCart className="h-5 w-5" />} tone="bg-primary-soft text-primary" label="Total Orders" value={s.ordersTotal} />
        <Stat icon={<DollarSign className="h-5 w-5" />} tone="bg-success-soft text-success" label="Total Revenue (TND)" value={Number(s.totalRevenue || 0).toFixed(2)} />
        <Stat icon={<TrendingUp className="h-5 w-5" />} tone="bg-warning-soft text-warning" label="Revenue This Month (TND)" value={Number(s.revenueThisMonth || 0).toFixed(2)} />
        <Stat icon={<ScrollText className="h-5 w-5" />} tone="bg-primary-soft text-primary" label={t('admin.statMessages')} value={s.messagesTotal} />
      </Section>

      {s.monthlySales && s.monthlySales.length > 0 && (
        <section className="mb-8">
          <h2 className="text-lg font-bold text-heading mb-3">Revenue & Orders Trend</h2>
          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard title="Monthly Revenue (TND)">
              <LineChart data={s.monthlySales}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: '8px' }} />
                <Line type="monotone" dataKey="revenue" stroke="#2563eb" strokeWidth={2} dot={{ fill: '#2563eb' }} />
              </LineChart>
            </ChartCard>
            <ChartCard title="Monthly Orders">
              <BarChart data={s.monthlySales}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                <XAxis dataKey="month" tick={{ fontSize: 12 }} />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: '8px' }} />
                <Bar dataKey="orders" fill="#ea580c" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ChartCard>
          </div>
        </section>
      )}

      <section className="mb-8">
        <h2 className="text-lg font-bold text-heading mb-3">Status Distribution</h2>
        <div className="grid gap-4 lg:grid-cols-2">
          <ChartCard title="Conversations">
            <PieChart>
              <Pie data={convData} cx="50%" cy="50%" outerRadius={90} dataKey="value" label={({ name, value }) => `${name}: ${value}`}>
                {convData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: '8px' }} />
              <Legend />
            </PieChart>
          </ChartCard>
          <ChartCard title="Orders">
            <PieChart>
              <Pie data={orderData} cx="50%" cy="50%" outerRadius={90} dataKey="value" label={({ name, value }) => `${name}: ${value}`}>
                {orderData.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: '8px' }} />
              <Legend />
            </PieChart>
          </ChartCard>
        </div>
      </section>

      {s.salesByGovernorate && s.salesByGovernorate.length > 0 && (
        <section className="mb-8">
          <h2 className="text-lg font-bold text-heading mb-3">Stickers Sold by Governorate</h2>
          <div className="grid gap-4 lg:grid-cols-2">
            <ChartCard title="Orders by Governorate">
              <BarChart data={s.salesByGovernorate.slice(0, 8)} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                <XAxis type="number" tick={{ fontSize: 12 }} />
                <YAxis dataKey="governorate" type="category" tick={{ fontSize: 11 }} width={100} tickFormatter={(v: string) => v.replace(/_/g, ' ')} />
                <Tooltip contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: '8px' }} />
                <Bar dataKey="count" fill="#2563eb" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ChartCard>
            <ChartCard title="Share by Governorate">
              <PieChart>
                <Pie data={s.salesByGovernorate.slice(0, 6)} cx="50%" cy="50%" outerRadius={90} dataKey="count" nameKey="governorate" label={({ governorate, count }: any) => `${String(governorate).replace(/_/g, ' ')}: ${count}`}>
                  {s.salesByGovernorate.slice(0, 6).map((_: any, i: number) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
                </Pie>
                <Tooltip contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: '8px' }} />
              </PieChart>
            </ChartCard>
          </div>
        </section>
      )}

      {s.dailyRegistrations && s.dailyRegistrations.length > 0 && (
        <section className="mb-8">
          <h2 className="text-lg font-bold text-heading mb-3">New Registrations (Last 30 Days)</h2>
<div className="bg-surface border border-border rounded-xl p-5 shadow-sm hover:shadow-md transition-shadow">
            <ResponsiveContainer width="100%" height={250}>
              <LineChart data={s.dailyRegistrations}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip contentStyle={{ backgroundColor: 'var(--color-surface)', border: '1px solid var(--color-border)', borderRadius: '8px' }} />
                <Line type="monotone" dataKey="count" stroke="#22c55e" strokeWidth={2} dot={{ fill: '#22c55e', r: 3 }} />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </section>
      )}

      <Section title={t('admin.statReportsOpen')}>
        <Stat icon={<Flag className="h-5 w-5" />} tone="bg-destructive-soft text-destructive" label={t('admin.reportStatus.OPEN')} value={s.reportsOpen} />
        <Stat icon={<Flag className="h-5 w-5" />} tone="bg-warning-soft text-warning" label={t('admin.reportStatus.REVIEWED')} value={s.reportsReviewed} />
        <Stat icon={<Flag className="h-5 w-5" />} tone="bg-success-soft text-success" label={t('admin.reportStatus.CLOSED')} value={s.reportsClosed} />
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

function Stat({ icon, tone, label, value }: { icon: React.ReactNode; tone: string; label: string; value: number | string }) {
  return (
    <div className="bg-surface border border-border rounded-lg p-5">
      <div className={`h-11 w-11 rounded-lg flex items-center justify-center mb-4 ${tone}`}>{icon}</div>
      <p className="text-3xl font-bold text-heading tabular-nums">{value}</p>
      <p className="text-sm text-muted-fg mt-1">{label}</p>
    </div>
  )
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-surface border border-border rounded-lg p-5">
      <p className="text-sm font-semibold text-heading mb-4">{title}</p>
      <ResponsiveContainer width="100%" height={260}>{children as any}</ResponsiveContainer>
    </div>
  )
}
