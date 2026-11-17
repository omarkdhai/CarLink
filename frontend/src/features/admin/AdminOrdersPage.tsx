import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Ban, CheckCircle2, Mail, MapPin, Package, Phone, ShoppingCart, StickyNote, User } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { adminApi } from '@/services/adminApi'
import { http } from '@/services/apiClient'
import { Button } from '@/components/ui/Button'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import type { AdminOrderView, OrderStatus } from '@/types'
import {
  DataList,
  DateCell,
  DetailDialog,
  DetailSection,
  EmptyResults,
  Identity,
  InfoGrid,
  Pagination,
  SearchBox,
  SortSelect,
  StatusTabs,
  Toolbar,
  useAdminFormat,
  useListParams,
  useToast,
  type Column,
} from './components/AdminList'

const PAGE_SIZE = 30

const STATUS_TONE: Record<OrderStatus, BadgeTone> = {
  PLACED: 'warning',
  DELIVERED: 'success',
  CANCELLED: 'danger',
}

const STATUS_DOT: Record<OrderStatus, string> = {
  PLACED: 'bg-warning',
  DELIVERED: 'bg-success',
  CANCELLED: 'bg-destructive',
}

type Change = { order: AdminOrderView; status: OrderStatus }

/** Flat delivery fee = total minus the item subtotals (the API does not send it separately). */
function deliveryFee(o: AdminOrderView) {
  const items = o.items.reduce((sum, i) => sum + Number(i.subtotal), 0)
  return Math.round((Number(o.totalAmount) - items) * 100) / 100
}

export default function AdminOrdersPage() {
  const { t } = useTranslation()
  const f = useAdminFormat()
  const qc = useQueryClient()
  const toast = useToast()
  const list = useListParams({ sort: 'createdAt,desc' })
  const [selected, setSelected] = useState<AdminOrderView | null>(null)
  const [confirmCancel, setConfirmCancel] = useState<AdminOrderView | null>(null)

  const q = useQuery({
    queryKey: ['admin', 'orders', list.page, list.q, list.status, list.sort],
    queryFn: () => adminApi.orders.list(list.page, PAGE_SIZE, list.q, list.status, list.sort),
    placeholderData: keepPreviousData,
  })

  const mut = useMutation({
    mutationFn: ({ order, status }: Change) => adminApi.orders.updateStatus(order.reference, status),
    onSuccess: (_d, { order, status }) => {
      qc.invalidateQueries({ queryKey: ['admin', 'orders'] })
      qc.invalidateQueries({ queryKey: ['admin', 'stats'] })
      setConfirmCancel(null)
      setSelected(null)
      toast.show(t(status === 'DELIVERED' ? 'admin.orders.deliveredToast' : 'admin.orders.cancelledToast', { ref: order.reference }))
    },
  })

  if (q.isError) return <ErrorState message={http.humanizeError(q.error)} onRetry={() => q.refetch()} />

  const statusLabel = (s: OrderStatus) => t(`admin.dash.orderStatus.${s}`)
  const governorate = (g: string) => t(`order.governorates.${g}`, g.replace(/_/g, ' '))
  const packageLabel = (p: string) => t(`admin.dash.packages.${p}`, p)

  const rowActions = (o: AdminOrderView) =>
    o.status === 'PLACED' ? (
      <div className="flex justify-end gap-1">
        <Button
          size="sm"
          variant="secondary"
          disabled={mut.isPending}
          onClick={(e) => {
            e.stopPropagation()
            mut.mutate({ order: o, status: 'DELIVERED' })
          }}
        >
          <CheckCircle2 className="h-4 w-4" aria-hidden />
          {t('admin.orders.deliver')}
        </Button>
        <Button
          size="sm"
          variant="ghost"
          aria-label={t('admin.orders.cancelOrder')}
          title={t('admin.orders.cancelOrder')}
          className="px-2 hover:text-destructive! hover:bg-destructive-soft"
          disabled={mut.isPending}
          onClick={(e) => {
            e.stopPropagation()
            setConfirmCancel(o)
          }}
        >
          <Ban className="h-4 w-4" aria-hidden />
        </Button>
      </div>
    ) : null

  const columns: Column<AdminOrderView>[] = [
    {
      key: 'ref',
      header: t('admin.stickers.reference'),
      cell: (o) => (
        <Identity
          primary={<span className="font-mono">{o.reference}</span>}
          secondary={o.items.map((i) => `${packageLabel(i.stickerPackage)} ×${i.quantity}`).join(', ')}
        />
      ),
    },
    { key: 'customer', header: t('admin.orders.customer'), cell: (o) => <Identity avatar={o.customerName} primary={o.customerName} secondary={o.mobile} /> },
    {
      key: 'gov',
      header: t('admin.orders.governorate'),
      cell: (o) => (
        <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
          <MapPin className="h-3.5 w-3.5 text-muted-fg" aria-hidden />
          {governorate(o.governorate)}
        </span>
      ),
    },
    { key: 'stickers', header: t('admin.fleet.stickers'), numeric: true, cell: (o) => f.num(o.stickerCount) },
    {
      key: 'total',
      header: t('admin.orders.total'),
      numeric: true,
      cell: (o) => <span className="font-semibold text-heading whitespace-nowrap">{f.money(o.totalAmount, o.currency)}</span>,
    },
    { key: 'status', header: t('vehicle.status'), cell: (o) => <Badge tone={STATUS_TONE[o.status]}>{statusLabel(o.status)}</Badge> },
    { key: 'created', header: t('admin.orders.placedOn'), cell: (o) => <DateCell iso={o.createdAt} /> },
    { key: 'actions', header: '', className: 'text-end', cell: rowActions },
  ]

  const data = q.data
  const orders = data?.content ?? []

  return (
    <div>
      <PageHeader title={t('nav.adminOrders')} subtitle={t('admin.orders.subtitle')} />

      <Toolbar>
        <SearchBox value={list.input} onChange={list.setInput} placeholder={t('admin.orders.searchPlaceholder')} />
        <StatusTabs
          label={t('vehicle.status')}
          value={list.status}
          onChange={list.setStatus}
          options={[
            { value: '', label: t('common.all') },
            ...(['PLACED', 'DELIVERED', 'CANCELLED'] as OrderStatus[]).map((s) => ({ value: s, label: statusLabel(s), dot: STATUS_DOT[s] })),
          ]}
        />
        <div className="sm:ms-auto">
          <SortSelect
            value={list.sort}
            onChange={list.setSort}
            options={[
              { value: 'createdAt,desc', label: t('vehicle.sortNewest') },
              { value: 'createdAt,asc', label: t('vehicle.sortOldest') },
              { value: 'totalAmount,desc', label: t('admin.orders.sortTotal') },
            ]}
          />
        </div>
      </Toolbar>

      <div className={q.isPlaceholderData ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
        <DataList
          rows={orders}
          columns={columns}
          rowKey={(o) => o.reference}
          rowLabel={(o) => `${o.reference}, ${o.customerName}, ${statusLabel(o.status)}`}
          onOpen={setSelected}
          loading={q.isPending}
          empty={
            <EmptyResults
              icon={<ShoppingCart className="h-6 w-6" aria-hidden />}
              title={t('admin.orders.empty')}
              hint={list.hasFilters ? t('admin.list.noResultsHint') : undefined}
              onClear={list.hasFilters ? list.clear : undefined}
            />
          }
          card={(o) => (
            <div className="space-y-3">
              <div className="flex items-start justify-between gap-3">
                <Identity primary={<span className="font-mono">{o.reference}</span>} secondary={f.dateTime(o.createdAt)} />
                <Badge tone={STATUS_TONE[o.status]}>{statusLabel(o.status)}</Badge>
              </div>
              <div className="flex items-center justify-between gap-3 text-sm">
                <span className="truncate">{o.customerName} · {governorate(o.governorate)}</span>
                <span className="font-semibold text-heading whitespace-nowrap">{f.money(o.totalAmount, o.currency)}</span>
              </div>
              {o.status === 'PLACED' && rowActions(o)}
            </div>
          )}
        />
      </div>

      {data && (
        <Pagination page={list.page} size={PAGE_SIZE} total={data.totalElements} totalPages={data.totalPages} onPage={list.setPage} />
      )}

      <DetailDialog
        open={Boolean(selected)}
        onClose={() => setSelected(null)}
        blockEscape={Boolean(confirmCancel)}
        title={selected ? <span className="font-mono">{selected.reference}</span> : ''}
        badges={selected && <Badge tone={STATUS_TONE[selected.status]}>{statusLabel(selected.status)}</Badge>}
        subtitle={selected && f.dateTime(selected.createdAt)}
        footer={
          selected && (
            <>
              {mut.isError && <p className="text-sm text-destructive me-auto">{http.humanizeError(mut.error)}</p>}
              <div className="me-auto">
                <p className="text-xs text-muted-fg">{t('admin.orders.totalCod', { count: selected.stickerCount })}</p>
                <p className="font-display text-2xl font-bold text-heading tabular-nums">{f.money(selected.totalAmount, selected.currency)}</p>
              </div>
              {selected.status === 'PLACED' && (
                <>
                  <Button size="sm" variant="outline" disabled={mut.isPending} onClick={() => setConfirmCancel(selected)}>
                    <Ban className="h-4 w-4" aria-hidden />
                    {t('admin.orders.cancelOrder')}
                  </Button>
                  <Button size="sm" loading={mut.isPending} onClick={() => mut.mutate({ order: selected, status: 'DELIVERED' })}>
                    <CheckCircle2 className="h-4 w-4" aria-hidden />
                    {t('admin.orders.markDelivered')}
                  </Button>
                </>
              )}
            </>
          )
        }
      >
        {selected && (
          <>
            <DetailSection icon={User} title={t('admin.orders.customerDelivery')}>
              <InfoGrid
                items={[
                  { label: t('admin.orders.customer'), value: selected.customerName },
                  {
                    label: t('admin.orders.phone'),
                    value: (
                      <a href={`tel:${selected.mobile}`} className="inline-flex items-center gap-1.5 text-primary hover:underline">
                        <Phone className="h-3.5 w-3.5" aria-hidden />
                        {selected.mobile}
                      </a>
                    ),
                  },
                  {
                    label: t('auth.email'),
                    value: (
                      <a href={`mailto:${selected.email}`} className="inline-flex items-center gap-1.5 text-primary hover:underline break-all">
                        <Mail className="h-3.5 w-3.5 shrink-0" aria-hidden />
                        {selected.email}
                      </a>
                    ),
                  },
                  { label: t('admin.orders.governorate'), value: governorate(selected.governorate) },
                  { label: t('admin.orders.address'), value: selected.deliveryAddress, full: true },
                ]}
              />
            </DetailSection>

            {selected.deliveryNotes && (
              <DetailSection icon={StickyNote} title={t('admin.orders.notes')}>
                <div className="rounded-xl border border-warning/30 bg-warning-soft p-4 text-sm whitespace-pre-wrap">{selected.deliveryNotes}</div>
              </DetailSection>
            )}

            <DetailSection icon={Package} title={t('admin.orders.items')}>
              <ul className="rounded-xl border border-border divide-y divide-border">
                {selected.items.map((it, idx) => (
                  <li key={idx} className="flex items-center justify-between gap-3 px-4 py-3">
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-heading">
                        {packageLabel(it.stickerPackage)} × {it.quantity}
                      </p>
                      <p className="text-xs text-muted-fg">
                        {t('admin.orders.perPack', { count: it.stickersPerPack })} · {f.money(it.unitPrice, selected.currency)}
                      </p>
                    </div>
                    <span className="text-sm font-semibold text-heading tabular-nums whitespace-nowrap">{f.money(it.subtotal, selected.currency)}</span>
                  </li>
                ))}
                {deliveryFee(selected) > 0 && (
                  <li className="flex items-center justify-between gap-3 px-4 py-3 bg-muted/30">
                    <p className="text-sm text-muted-fg">{t('admin.orders.delivery')}</p>
                    <span className="text-sm text-muted-fg tabular-nums whitespace-nowrap">{f.money(deliveryFee(selected), selected.currency)}</span>
                  </li>
                )}
              </ul>
            </DetailSection>
          </>
        )}
      </DetailDialog>

      <ConfirmDialog
        open={Boolean(confirmCancel)}
        title={t('admin.orders.cancelOrder')}
        description={t('admin.orders.cancelConfirm', { ref: confirmCancel?.reference ?? '', customer: confirmCancel?.customerName ?? '' })}
        confirmLabel={t('admin.orders.cancelOrder')}
        tone="danger"
        loading={mut.isPending}
        onConfirm={() => confirmCancel && mut.mutate({ order: confirmCancel, status: 'CANCELLED' })}
        onCancel={() => setConfirmCancel(null)}
      />
      {toast.node}
    </div>
  )
}
