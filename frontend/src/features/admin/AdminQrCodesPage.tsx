import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Ban, CarFront, Mail, Package, QrCode, User } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { adminApi, adminQueryKeys } from '@/services/adminApi'
import { http } from '@/services/apiClient'
import { Button } from '@/components/ui/Button'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import type { AdminStickerView, StickerStatus } from '@/types'
import {
  DataList,
  DateCell,
  DetailDialog,
  DetailSection,
  EmptyResults,
  Identity,
  InfoGrid,
  Muted,
  Pagination,
  PlateChip,
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

const STATUS_TONE: Record<StickerStatus, BadgeTone> = {
  UNBOUND: 'warning',
  BOUND: 'success',
  DEACTIVATED: 'neutral',
}

const STATUS_DOT: Record<StickerStatus, string> = {
  UNBOUND: 'bg-warning',
  BOUND: 'bg-success',
  DEACTIVATED: 'bg-muted-fg',
}

/** Short, human-readable sticker handle (first UUID block). */
function shortId(s: AdminStickerView) {
  return `#${s.id.slice(0, 8).toUpperCase()}`
}

function vehicleTitle(v: NonNullable<AdminStickerView['vehicle']>) {
  return v.nickname || [v.brand, v.model].filter(Boolean).join(' ') || v.licensePlate
}

function ownerName(o: NonNullable<AdminStickerView['owner']>) {
  return `${o.firstName} ${o.lastName}`.trim()
}

export default function AdminQrCodesPage() {
  const { t } = useTranslation()
  const f = useAdminFormat()
  const qc = useQueryClient()
  const toast = useToast()
  const list = useListParams({ sort: 'createdAt,desc' })
  const [selected, setSelected] = useState<AdminStickerView | null>(null)
  const [confirmDeactivate, setConfirmDeactivate] = useState<AdminStickerView | null>(null)

  const q = useQuery({
    queryKey: ['admin', 'stickers', list.page, list.q, list.status, list.sort],
    queryFn: () => adminApi.stickers.list(list.page, PAGE_SIZE, list.q, list.status, list.sort),
    placeholderData: keepPreviousData,
  })

  const counts = useQuery({
    queryKey: adminQueryKeys.stickerCounts,
    queryFn: () => adminApi.stickers.counts(),
  })

  const mut = useMutation({
    mutationFn: (s: AdminStickerView) => adminApi.stickers.deactivate(s.id),
    onSuccess: (_d, s) => {
      qc.invalidateQueries({ queryKey: ['admin', 'stickers'] })
      qc.invalidateQueries({ queryKey: ['admin', 'vehicles'] })
      qc.invalidateQueries({ queryKey: ['admin', 'stats'] })
      setConfirmDeactivate(null)
      setSelected(null)
      toast.show(t('admin.stickers.deactivated', { id: shortId(s) }))
    },
  })

  if (q.isError) return <ErrorState message={http.humanizeError(q.error)} onRetry={() => q.refetch()} />

  const statusLabel = (s: StickerStatus) => t(`stickers.status.${s}`)
  const c = counts.data
  const total = c ? c.UNBOUND + c.BOUND + c.DEACTIVATED : undefined

  const idCell = (s: AdminStickerView) => (
    <div className="flex items-center gap-3">
      <span className="h-9 w-9 shrink-0 rounded-lg bg-primary-soft text-primary flex items-center justify-center" aria-hidden>
        <QrCode className="h-4 w-4" />
      </span>
      <div>
        <p className="font-mono text-xs font-bold text-heading">{shortId(s)}</p>
        {s.legacy && <p className="text-xs text-muted-fg">{t('admin.stickers.legacy')}</p>}
      </div>
    </div>
  )

  const deactivateButton = (s: AdminStickerView) =>
    s.status === 'BOUND' ? (
      <Button
        size="sm"
        variant="ghost"
        className="text-destructive! hover:bg-destructive-soft"
        disabled={mut.isPending}
        onClick={(e) => {
          e.stopPropagation()
          setConfirmDeactivate(s)
        }}
      >
        <Ban className="h-4 w-4" aria-hidden />
        {t('admin.stickers.deactivate')}
      </Button>
    ) : null

  const columns: Column<AdminStickerView>[] = [
    { key: 'id', header: t('admin.stickers.sticker'), cell: idCell },
    {
      key: 'order',
      header: t('admin.stickers.order'),
      cell: (s) => (s.order ? <Identity primary={s.order.reference} secondary={s.order.customerName} /> : <Muted />),
    },
    {
      key: 'owner',
      header: t('admin.fleet.owner'),
      cell: (s) =>
        s.owner ? (
          <Identity avatar={ownerName(s.owner)} primary={ownerName(s.owner)} secondary={s.owner.email} />
        ) : (
          <Muted>{t('admin.stickers.unclaimed')}</Muted>
        ),
    },
    {
      key: 'vehicle',
      header: t('admin.fleet.vehicle'),
      cell: (s) =>
        s.vehicle ? (
          <div className="space-y-1">
            <p className="font-medium text-heading truncate">{vehicleTitle(s.vehicle)}</p>
            <PlateChip plate={s.vehicle.licensePlate} />
          </div>
        ) : (
          <Muted />
        ),
    },
    { key: 'status', header: t('vehicle.status'), cell: (s) => <Badge tone={STATUS_TONE[s.status]}>{statusLabel(s.status)}</Badge> },
    { key: 'created', header: t('admin.stickers.created'), cell: (s) => <DateCell iso={s.createdAt} /> },
    { key: 'linked', header: t('admin.stickers.linked'), cell: (s) => <DateCell iso={s.boundAt} /> },
    { key: 'actions', header: '', className: 'text-end', cell: deactivateButton },
  ]

  const data = q.data
  const stickers = data?.content ?? []

  return (
    <div>
      <PageHeader title={t('admin.qrCodes')} subtitle={t('admin.stickers.subtitle')} />

      <Toolbar>
        <SearchBox value={list.input} onChange={list.setInput} placeholder={t('admin.stickers.searchPlaceholder')} />
        <StatusTabs
          label={t('vehicle.status')}
          value={list.status}
          onChange={list.setStatus}
          options={[
            { value: '', label: t('common.all'), count: total },
            ...(['BOUND', 'UNBOUND', 'DEACTIVATED'] as StickerStatus[]).map((s) => ({
              value: s,
              label: statusLabel(s),
              count: c?.[s],
              dot: STATUS_DOT[s],
            })),
          ]}
        />
        <div className="sm:ms-auto">
          <SortSelect
            value={list.sort}
            onChange={list.setSort}
            options={[
              { value: 'createdAt,desc', label: t('vehicle.sortNewest') },
              { value: 'createdAt,asc', label: t('vehicle.sortOldest') },
            ]}
          />
        </div>
      </Toolbar>

      <div className={q.isPlaceholderData ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
        <DataList
          rows={stickers}
          columns={columns}
          rowKey={(s) => s.id}
          rowLabel={(s) => `${shortId(s)}, ${statusLabel(s.status)}`}
          onOpen={setSelected}
          loading={q.isPending}
          empty={
            <EmptyResults
              icon={<QrCode className="h-6 w-6" aria-hidden />}
              title={t('admin.stickers.empty')}
              hint={list.hasFilters ? t('admin.list.noResultsHint') : undefined}
              onClear={list.hasFilters ? list.clear : undefined}
            />
          }
          card={(s) => (
            <div className="space-y-3">
              <div className="flex items-start justify-between gap-3">
                {idCell(s)}
                <Badge tone={STATUS_TONE[s.status]}>{statusLabel(s.status)}</Badge>
              </div>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div className="min-w-0">
                  <p className="text-xs text-muted-fg">{t('admin.stickers.order')}</p>
                  <p className="font-medium truncate">{s.order?.reference ?? '—'}</p>
                </div>
                <div className="min-w-0">
                  <p className="text-xs text-muted-fg">{t('admin.fleet.owner')}</p>
                  <p className="font-medium truncate">{s.owner ? ownerName(s.owner) : t('admin.stickers.unclaimed')}</p>
                </div>
              </div>
              {(s.vehicle || s.status === 'BOUND') && (
                <div className="flex items-center justify-between gap-2">
                  {s.vehicle ? <PlateChip plate={s.vehicle.licensePlate} /> : <span />}
                  {deactivateButton(s)}
                </div>
              )}
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
        blockEscape={Boolean(confirmDeactivate)}
        title={selected ? <span className="font-mono">{shortId(selected)}</span> : ''}
        badges={
          selected && (
            <>
              <Badge tone={STATUS_TONE[selected.status]}>{statusLabel(selected.status)}</Badge>
              {selected.legacy && <Badge tone="neutral">{t('admin.stickers.legacy')}</Badge>}
            </>
          )
        }
        subtitle={selected && <span className="font-mono break-all">{selected.id}</span>}
        footer={
          selected && (
            <>
              {mut.isError && <p className="text-sm text-destructive me-auto">{http.humanizeError(mut.error)}</p>}
              {selected.owner && (
                <a href={`mailto:${selected.owner.email}`} className="me-auto inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline">
                  <Mail className="h-4 w-4" aria-hidden />
                  {t('admin.fleet.emailOwner')}
                </a>
              )}
              {selected.status === 'BOUND' && (
                <Button size="sm" variant="danger" disabled={mut.isPending} onClick={() => setConfirmDeactivate(selected)}>
                  <Ban className="h-4 w-4" aria-hidden />
                  {t('admin.stickers.deactivate')}
                </Button>
              )}
            </>
          )
        }
      >
        {selected && (
          <>
            <Timeline
              steps={[
                { label: t('admin.stickers.created'), at: selected.createdAt, done: true },
                { label: t('admin.stickers.linked'), at: selected.boundAt, done: Boolean(selected.boundAt) },
                ...(selected.deactivatedAt
                  ? [{ label: t('stickers.deactivatedAt'), at: selected.deactivatedAt, done: true, danger: true }]
                  : []),
              ]}
              format={f.dateTime}
            />
            <DetailSection icon={Package} title={t('admin.stickers.order')}>
              {selected.order ? (
                <InfoGrid
                  items={[
                    { label: t('admin.stickers.reference'), value: <span className="font-mono">{selected.order.reference}</span> },
                    { label: t('admin.stickers.buyer'), value: selected.order.customerName },
                    { label: t('admin.stickers.orderedAt'), value: f.dateTime(selected.order.createdAt) },
                    { label: t('vehicle.status'), value: t(`admin.dash.orderStatus.${selected.order.status}`) },
                  ]}
                />
              ) : (
                <p className="text-sm text-muted-fg">{t('admin.stickers.noOrder')}</p>
              )}
            </DetailSection>
            <DetailSection icon={User} title={t('admin.fleet.owner')}>
              {selected.owner ? (
                <div className="flex items-center gap-3 rounded-xl border border-border p-4">
                  <Identity avatar={ownerName(selected.owner)} primary={ownerName(selected.owner) || '—'} secondary={selected.owner.email} />
                  <Badge tone={selected.owner.active ? 'success' : 'neutral'} className="ms-auto">
                    {selected.owner.active ? t('admin.active') : t('admin.inactive')}
                  </Badge>
                </div>
              ) : (
                <p className="text-sm text-muted-fg">{t('admin.stickers.noOwner')}</p>
              )}
            </DetailSection>
            <DetailSection icon={CarFront} title={t('admin.fleet.vehicle')}>
              {selected.vehicle ? (
                <InfoGrid
                  items={[
                    { label: t('vehicle.nickname'), value: vehicleTitle(selected.vehicle) },
                    { label: t('vehicle.licensePlate'), value: <PlateChip plate={selected.vehicle.licensePlate} /> },
                    { label: t('vehicle.brandModel'), value: [selected.vehicle.brand, selected.vehicle.model].filter(Boolean).join(' ') || <Muted /> },
                    {
                      label: t('vehicle.status'),
                      value: (
                        <Badge tone={selected.vehicle.status === 'ACTIVE' ? 'success' : 'neutral'}>
                          {selected.vehicle.status === 'ACTIVE' ? t('vehicle.active') : t('vehicle.archived')}
                        </Badge>
                      ),
                    },
                  ]}
                />
              ) : (
                <p className="text-sm text-muted-fg">{t('stickers.notLinked')}</p>
              )}
            </DetailSection>
          </>
        )}
      </DetailDialog>

      <ConfirmDialog
        open={Boolean(confirmDeactivate)}
        title={t('admin.stickers.deactivate')}
        description={t('admin.stickers.deactivateConfirm', {
          id: confirmDeactivate ? shortId(confirmDeactivate).slice(1) : '',
          vehicle: confirmDeactivate?.vehicle ? vehicleTitle(confirmDeactivate.vehicle) : '—',
        })}
        confirmLabel={t('admin.stickers.deactivate')}
        tone="danger"
        loading={mut.isPending}
        onConfirm={() => confirmDeactivate && mut.mutate(confirmDeactivate)}
        onCancel={() => setConfirmDeactivate(null)}
      />
      {toast.node}
    </div>
  )
}

/** Horizontal lifecycle: issued → linked (→ released). */
function Timeline({
  steps,
  format,
}: {
  steps: { label: string; at: string | null; done: boolean; danger?: boolean }[]
  format: (iso: string) => string
}) {
  return (
    <ol className="grid gap-3" style={{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }}>
      {steps.map((s, i) => (
        <li key={s.label} className="relative">
          <div className="flex items-center gap-2 mb-2">
            <span
              className={
                s.danger
                  ? 'h-3 w-3 rounded-full bg-destructive'
                  : s.done
                    ? 'h-3 w-3 rounded-full bg-success'
                    : 'h-3 w-3 rounded-full border-2 border-border bg-surface'
              }
              aria-hidden
            />
            {i < steps.length - 1 && <span className="h-0.5 flex-1 bg-border" aria-hidden />}
          </div>
          <p className="text-xs font-medium text-heading">{s.label}</p>
          <p className="text-xs text-muted-fg">{s.at ? format(s.at) : '—'}</p>
        </li>
      ))}
    </ol>
  )
}
