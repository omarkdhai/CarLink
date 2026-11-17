import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import {
  Area,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ComposedChart,
  Legend,
  Line,
  Pie,
  PieChart,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  Banknote,
  CarFront,
  Flag,
  MessageSquare,
  MessageSquareWarning,
  Package,
  QrCode,
  Receipt,
  ShoppingCart,
  Sticker,
  UserPlus,
  Link2,
} from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { adminApi, adminQueryKeys } from '@/services/adminApi'
import type { DashboardDays, OrderStatus } from '@/types'
import {
  AttentionCard,
  BarRow,
  C,
  ChartCard,
  KpiCard,
  LiveIndicator,
  PALETTE,
  RangePicker,
  SectionTitle,
  axisProps,
  gridProps,
  tooltipProps,
  useFormatters,
} from './overview/parts'

/** Dashboard refresh cadence while the tab is visible. */
const REFRESH_MS = 30_000

const ORDER_TONE: Record<OrderStatus, BadgeTone> = {
  PLACED: 'warning',
  DELIVERED: 'success',
  CANCELLED: 'danger',
}

const ORDER_COLOR: Record<string, string> = {
  PLACED: C.warning,
  DELIVERED: C.success,
  CANCELLED: C.danger,
}

export default function AdminOverviewPage() {
  const { t } = useTranslation()
  const f = useFormatters()
  const [days, setDays] = useState<DashboardDays>(30)

  const q = useQuery({
    queryKey: adminQueryKeys.dashboard(days),
    queryFn: () => adminApi.stats.dashboard(days),
    placeholderData: keepPreviousData,
    refetchInterval: REFRESH_MS,
    refetchOnWindowFocus: true,
  })

  const header = (
    <PageHeader
      title={t('admin.overview')}
      subtitle={t('admin.dash.subtitle')}
      actions={
        <div className="flex flex-wrap items-center justify-end gap-3">
          {q.data && <LiveIndicator updatedAt={q.dataUpdatedAt} fetching={q.isFetching} onRefresh={() => q.refetch()} />}
          <RangePicker value={days} onChange={setDays} />
        </div>
      }
    />
  )

  if (q.isError && !q.data) {
    return (
      <div>
        {header}
        <ErrorState message={t('errors.generic')} onRetry={() => q.refetch()} />
      </div>
    )
  }

  if (!q.data) {
    return (
      <div>
        {header}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {Array.from({ length: 12 }).map((_, i) => (
            <div key={i} className={`bg-surface border border-border rounded-xl p-4 space-y-3 ${i >= 8 ? 'h-64 lg:col-span-2' : ''}`}>
              <div className="h-4 w-1/2 rounded bg-muted animate-pulse" />
              <div className="h-7 w-24 rounded bg-muted animate-pulse" />
              <div className="h-3 w-2/3 rounded bg-muted animate-pulse" />
            </div>
          ))}
        </div>
      </div>
    )
  }

  const d = q.data
  const k = d.kpis
  const g = d.granularity
  const label = (iso: string) => f.bucket(iso, g)

  const sales = d.sales.map((p) => ({ ...p, revenue: Number(p.revenue), label: label(p.bucket) }))
  const growth = d.growth.map((p) => ({ ...p, label: label(p.bucket) }))
  const contacts = d.contacts.map((p) => ({ ...p, label: label(p.bucket) }))
  const hours = d.contactsByHour.map((n, h) => ({ hour: `${String(h).padStart(2, '0')}h`, n }))
  const packages = d.packageMix.map((s) => ({ ...s, label: t(`admin.dash.packages.${s.name}`, s.name) }))
  const statuses = d.ordersByStatus.map((s) => ({ ...s, label: t(`admin.dash.orderStatus.${s.name}`, s.name) }))
  const governorates = d.governorates.map((s) => ({
    ...s,
    amountN: Number(s.amount),
    label: t(`order.governorates.${s.name}`, s.name.replace(/_/g, ' ')),
  }))
  const reasonsTotal = d.contactReasons.reduce((sum, r) => sum + r.count, 0)
  const funnelMax = Math.max(1, d.stickers.issued)
  const activationRate = d.stickers.issued ? (d.stickers.everActivated / d.stickers.issued) * 100 : 0
  const periodHint = t(`admin.dash.periodHint.${g}`)
  const rangeLabel = t(`admin.dash.kpiTitle.${days}`)

  const hasSales = sales.some((p) => p.orders > 0)
  const hasGrowth = growth.some((p) => p.users + p.vehicles + p.activations > 0)
  const hasContacts = contacts.some((p) => p.sent + p.failed + p.other > 0)

  return (
    <div className={q.isPlaceholderData ? 'opacity-70 transition-opacity' : 'transition-opacity'}>
      {header}

      {/* Needs attention */}
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4">
        <AttentionCard
          icon={Package}
          label={t('admin.dash.pendingOrders')}
          value={f.num(d.snapshot.ordersPending)}
          detail={d.snapshot.ordersPending ? f.tnd(d.snapshot.ordersPendingValue) : undefined}
          to="/admin/orders?status=PLACED"
          alert={d.snapshot.ordersPending > 0}
        />
        <AttentionCard
          icon={Flag}
          label={t('admin.dash.openReports')}
          value={f.num(d.snapshot.reportsOpen)}
          to="/admin/reports"
          alert={d.snapshot.reportsOpen > 0}
        />
        <AttentionCard
          icon={MessageSquareWarning}
          label={t('admin.dash.failedContacts')}
          value={f.num(d.snapshot.contactsFailed)}
          alert={d.snapshot.contactsFailed > 0}
        />
        <AttentionCard
          icon={QrCode}
          label={t('admin.dash.unclaimedStickers')}
          value={f.num(d.stickers.unbound)}
          to="/admin/qr-codes?status=UNBOUND"
          alert={false}
        />
      </div>

      {/* KPIs */}
      <SectionTitle>{t(`admin.dash.kpiTitle.${days}`)}</SectionTitle>
      <div className="grid gap-3 grid-cols-2 lg:grid-cols-4">
        <KpiCard
          icon={Banknote}
          tone="bg-success-soft text-success"
          label={t('admin.dash.kpi.revenue')}
          value={f.tnd(k.revenue.value)}
          kpi={k.revenue}
          hint={t('admin.dash.kpi.delivered', { amount: f.tnd(k.deliveredRevenue.value) })}
        />
        <KpiCard
          icon={ShoppingCart}
          tone="bg-primary-soft text-primary"
          label={t('admin.dash.kpi.orders')}
          value={f.num(k.orders.value)}
          kpi={k.orders}
        />
        <KpiCard
          icon={Receipt}
          tone="bg-accent-soft text-accent"
          label={t('admin.dash.kpi.aov')}
          value={f.tnd(k.averageOrderValue.value)}
          kpi={k.averageOrderValue}
        />
        <KpiCard
          icon={Sticker}
          tone="bg-warning-soft text-warning"
          label={t('admin.dash.kpi.stickersSold')}
          value={f.num(k.stickersSold.value)}
          kpi={k.stickersSold}
        />
        <KpiCard
          icon={Link2}
          tone="bg-success-soft text-success"
          label={t('admin.dash.kpi.activations')}
          value={f.num(k.activations.value)}
          kpi={k.activations}
          hint={t('admin.dash.kpi.activationRate', { rate: f.pct(activationRate) })}
        />
        <KpiCard
          icon={UserPlus}
          tone="bg-primary-soft text-primary"
          label={t('admin.dash.kpi.newUsers')}
          value={f.num(k.newUsers.value)}
          kpi={k.newUsers}
          hint={t('admin.dash.kpi.totalActive', {
            total: f.num(d.snapshot.usersTotal),
            active: f.num(d.snapshot.usersActive),
          })}
        />
        <KpiCard
          icon={CarFront}
          tone="bg-accent-soft text-accent"
          label={t('admin.dash.kpi.newVehicles')}
          value={f.num(k.newVehicles.value)}
          kpi={k.newVehicles}
          hint={t('admin.dash.kpi.totalActive', {
            total: f.num(d.snapshot.vehiclesTotal),
            active: f.num(d.snapshot.vehiclesActive),
          })}
        />
        <KpiCard
          icon={MessageSquare}
          tone="bg-primary-soft text-primary"
          label={t('admin.dash.kpi.contacts')}
          value={f.num(k.contacts.value)}
          kpi={k.contacts}
          hint={t('admin.dash.kpi.deliveryRate', { rate: f.pct(k.deliveryRate.value) })}
        />
      </div>

      {/* Sales */}
      <SectionTitle>{t('admin.dash.sections.sales')}</SectionTitle>
      <div className="grid gap-4 lg:grid-cols-3">
        <ChartCard
          title={t('admin.dash.charts.revenueOrders')}
          subtitle={periodHint}
          empty={!hasSales}
          className="lg:col-span-2"
          height={280}
        >
          <ComposedChart data={sales} margin={{ top: 5, right: 5, left: -10, bottom: 0 }}>
            <defs>
              <linearGradient id="revFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={C.primary} stopOpacity={0.3} />
                <stop offset="100%" stopColor={C.primary} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid {...gridProps} />
            <XAxis dataKey="label" {...axisProps} minTickGap={16} />
            <YAxis yAxisId="rev" {...axisProps} tickFormatter={f.compact} />
            <YAxis yAxisId="orders" orientation="right" {...axisProps} allowDecimals={false} />
            <Tooltip
              {...tooltipProps}
              formatter={(value, name) =>
                name === 'revenue'
                  ? [f.tnd(Number(value)), t('admin.dash.kpi.revenue')]
                  : [f.num(Number(value)), t(name === 'orders' ? 'admin.dash.kpi.orders' : 'admin.dash.kpi.stickersSold')]
              }
            />
            <Legend
              iconType="circle"
              wrapperStyle={{ fontSize: 12 }}
              formatter={(name) =>
                t(name === 'revenue' ? 'admin.dash.kpi.revenue' : name === 'orders' ? 'admin.dash.kpi.orders' : 'admin.dash.kpi.stickersSold')
              }
            />
            <Bar yAxisId="orders" dataKey="orders" fill={C.accent} radius={[4, 4, 0, 0]} maxBarSize={28} />
            <Area yAxisId="rev" type="linear" dataKey="revenue" stroke={C.primary} strokeWidth={2} fill="url(#revFill)" />
            <Line yAxisId="orders" type="linear" dataKey="stickers" stroke={C.success} strokeWidth={2} dot={false} />
          </ComposedChart>
        </ChartCard>

        <ChartCard
          title={t('admin.dash.charts.packageMix')}
          subtitle={t('admin.dash.charts.packageMixHint')}
          empty={packages.length === 0}
          height={180}
          footer={
            <ul className="mt-4 space-y-2">
              {packages.map((p, i) => (
                <li key={p.name} className="flex items-center justify-between gap-2 text-sm">
                  <span className="flex items-center gap-2 min-w-0">
                    <span className="h-2.5 w-2.5 rounded-full shrink-0" style={{ backgroundColor: PALETTE[i % PALETTE.length] }} />
                    <span className="truncate">{p.label}</span>
                  </span>
                  <span className="text-muted-fg tabular-nums whitespace-nowrap">
                    {f.num(p.count)} · {f.tnd(p.amount)}
                  </span>
                </li>
              ))}
            </ul>
          }
        >
          <PieChart>
            <Pie data={packages} dataKey="count" nameKey="label" innerRadius={50} outerRadius={80} paddingAngle={2} stroke="none">
              {packages.map((p, i) => (
                <Cell key={p.name} fill={PALETTE[i % PALETTE.length]} />
              ))}
            </Pie>
            <Tooltip {...tooltipProps} formatter={(v) => f.num(Number(v))} />
          </PieChart>
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-3 mt-4">
        <ChartCard
          title={t('admin.dash.charts.governorates')}
          subtitle={t('admin.dash.charts.governoratesHint')}
          empty={governorates.length === 0}
          className="lg:col-span-2"
          height={Math.max(160, governorates.length * 34)}
        >
          <BarChart data={governorates} layout="vertical" margin={{ top: 0, right: 10, left: 10, bottom: 0 }}>
            <CartesianGrid {...gridProps} horizontal={false} vertical />
            <XAxis type="number" {...axisProps} tickFormatter={f.compact} />
            <YAxis type="category" dataKey="label" {...axisProps} width={90} />
            <Tooltip
              {...tooltipProps}
              formatter={(v, _n, item) => [
                `${f.tnd(Number(v))} · ${f.num((item.payload as { count: number }).count)} ${t('admin.dash.ordersUnit')}`,
                t('admin.dash.kpi.revenue'),
              ]}
            />
            <Bar dataKey="amountN" fill={C.primary} radius={[0, 4, 4, 0]} maxBarSize={22} />
          </BarChart>
        </ChartCard>

        <ChartCard title={t('admin.dash.charts.orderStatus')} subtitle={rangeLabel} empty={statuses.length === 0} height={200}
          footer={
            <ul className="mt-4 space-y-2">
              {statuses.map((s) => (
                <li key={s.name} className="flex items-center justify-between gap-2 text-sm">
                  <span className="flex items-center gap-2">
                    <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: ORDER_COLOR[s.name] ?? C.slate }} />
                    {s.label}
                  </span>
                  <span className="text-muted-fg tabular-nums">{f.num(s.count)} · {f.tnd(s.amount)}</span>
                </li>
              ))}
            </ul>
          }
        >
          <PieChart>
            <Pie data={statuses} dataKey="count" nameKey="label" innerRadius={55} outerRadius={85} paddingAngle={2} stroke="none">
              {statuses.map((s) => (
                <Cell key={s.name} fill={ORDER_COLOR[s.name] ?? C.slate} />
              ))}
            </Pie>
            <Tooltip {...tooltipProps} formatter={(v) => f.num(Number(v))} />
          </PieChart>
        </ChartCard>
      </div>

      {/* Growth */}
      <SectionTitle>{t('admin.dash.sections.growth')}</SectionTitle>
      <div className="grid gap-4 lg:grid-cols-3">
        <ChartCard
          title={t('admin.dash.charts.growth')}
          subtitle={periodHint}
          empty={!hasGrowth}
          className="lg:col-span-2"
          height={260}
        >
          <BarChart data={growth} margin={{ top: 5, right: 5, left: -20, bottom: 0 }}>
            <CartesianGrid {...gridProps} />
            <XAxis dataKey="label" {...axisProps} minTickGap={16} />
            <YAxis {...axisProps} allowDecimals={false} />
            <Tooltip
              {...tooltipProps}
              formatter={(v, name) => [f.num(Number(v)), t(`admin.dash.series.${String(name)}`)]}
            />
            <Legend iconType="circle" wrapperStyle={{ fontSize: 12 }} formatter={(name) => t(`admin.dash.series.${String(name)}`)} />
            <Bar dataKey="users" fill={C.primary} radius={[3, 3, 0, 0]} maxBarSize={14} />
            <Bar dataKey="vehicles" fill={C.accent} radius={[3, 3, 0, 0]} maxBarSize={14} />
            <Bar dataKey="activations" fill={C.success} radius={[3, 3, 0, 0]} maxBarSize={14} />
          </BarChart>
        </ChartCard>

        <div className="bg-surface border border-border rounded-xl p-5 flex flex-col">
          <p className="text-sm font-semibold text-heading">{t('admin.dash.charts.funnel')}</p>
          <p className="text-xs text-muted-fg mt-0.5 mb-5">{t('admin.dash.charts.funnelHint')}</p>
          <div className="space-y-4">
            <BarRow label={t('admin.dash.funnel.issued')} value={d.stickers.issued} max={funnelMax} color={C.primary} right={f.num(d.stickers.issued)} />
            <BarRow
              label={t('admin.dash.funnel.activated')}
              value={d.stickers.everActivated}
              max={funnelMax}
              color={C.success}
              right={`${f.num(d.stickers.everActivated)} · ${f.pct(activationRate)}`}
            />
            <BarRow label={t('admin.dash.funnel.live')} value={d.stickers.bound} max={funnelMax} color={C.accent} right={f.num(d.stickers.bound)} />
          </div>
          <div className="mt-auto pt-5 grid grid-cols-2 gap-3 text-center">
            <div className="rounded-lg bg-muted/50 p-3">
              <p className="text-lg font-bold text-heading tabular-nums">{f.num(d.stickers.unbound)}</p>
              <p className="text-xs text-muted-fg">{t('stickers.status.UNBOUND')}</p>
            </div>
            <div className="rounded-lg bg-muted/50 p-3">
              <p className="text-lg font-bold text-heading tabular-nums">{f.num(d.stickers.deactivated)}</p>
              <p className="text-xs text-muted-fg">{t('stickers.status.DEACTIVATED')}</p>
            </div>
          </div>
        </div>
      </div>

      {/* Contacts */}
      <SectionTitle>{t('admin.dash.sections.contacts')}</SectionTitle>
      <div className="grid gap-4 lg:grid-cols-3">
        <ChartCard
          title={t('admin.dash.charts.contacts')}
          subtitle={periodHint}
          empty={!hasContacts}
          className="lg:col-span-2"
          height={240}
        >
          <BarChart data={contacts} margin={{ top: 5, right: 5, left: -20, bottom: 0 }}>
            <CartesianGrid {...gridProps} />
            <XAxis dataKey="label" {...axisProps} minTickGap={16} />
            <YAxis {...axisProps} allowDecimals={false} />
            <Tooltip {...tooltipProps} formatter={(v, name) => [f.num(Number(v)), t(`admin.dash.series.${String(name)}`)]} />
            <Legend iconType="circle" wrapperStyle={{ fontSize: 12 }} formatter={(name) => t(`admin.dash.series.${String(name)}`)} />
            <Bar dataKey="sent" stackId="c" fill={C.success} maxBarSize={28} />
            <Bar dataKey="failed" stackId="c" fill={C.danger} maxBarSize={28} />
            <Bar dataKey="other" stackId="c" fill={C.slate} radius={[4, 4, 0, 0]} maxBarSize={28} />
          </BarChart>
        </ChartCard>

        <div className="bg-surface border border-border rounded-xl p-5">
          <p className="text-sm font-semibold text-heading">{t('admin.dash.charts.reasons')}</p>
          <p className="text-xs text-muted-fg mt-0.5 mb-5">{rangeLabel}</p>
          {d.contactReasons.length === 0 ? (
            <p className="text-sm text-muted-fg py-10 text-center">{t('admin.dash.noData')}</p>
          ) : (
            <div className="space-y-3">
              {d.contactReasons.map((r, i) => (
                <BarRow
                  key={r.name}
                  label={t(`contact.reasons.${r.name}`, r.name)}
                  value={r.count}
                  max={d.contactReasons[0].count}
                  color={PALETTE[i % PALETTE.length]}
                  right={`${f.num(r.count)} · ${f.pct((r.count / reasonsTotal) * 100)}`}
                />
              ))}
            </div>
          )}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2 mt-4">
        <ChartCard
          title={t('admin.dash.charts.hours')}
          subtitle={t('admin.dash.charts.hoursHint', { tz: d.timeZone })}
          empty={!hasContacts}
          height={220}
        >
          <BarChart data={hours} margin={{ top: 5, right: 5, left: -20, bottom: 0 }}>
            <CartesianGrid {...gridProps} />
            <XAxis dataKey="hour" {...axisProps} interval={2} />
            <YAxis {...axisProps} allowDecimals={false} />
            <Tooltip {...tooltipProps} formatter={(v) => [f.num(Number(v)), t('admin.dash.kpi.contacts')]} />
            <Bar dataKey="n" fill={C.violet} radius={[3, 3, 0, 0]} />
          </BarChart>
        </ChartCard>

        {/* Recent orders */}
        <div className="bg-surface border border-border rounded-xl p-5">
          <div className="flex items-center justify-between mb-3">
            <p className="text-sm font-semibold text-heading">{t('admin.dash.recentOrders')}</p>
            <Link to="/admin/orders" className="text-xs font-medium text-primary hover:underline">
              {t('admin.dash.viewAll')}
            </Link>
          </div>
          {d.recentOrders.length === 0 ? (
            <p className="text-sm text-muted-fg py-10 text-center">{t('admin.dash.noData')}</p>
          ) : (
            <ul className="divide-y divide-border">
              {d.recentOrders.map((o) => (
                <li key={o.reference} className="py-2.5 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-heading truncate">
                      {o.reference} · <span className="font-normal">{o.customerName}</span>
                    </p>
                    <p className="text-xs text-muted-fg truncate">
                      {t(`order.governorates.${o.governorate}`, o.governorate)} · {f.dateTime(o.createdAt)}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <span className="text-sm tabular-nums text-heading">{f.tnd(o.totalAmount)}</span>
                    <Badge tone={ORDER_TONE[o.status]}>{t(`admin.dash.orderStatus.${o.status}`)}</Badge>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}
