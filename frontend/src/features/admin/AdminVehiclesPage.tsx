import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Archive, ArchiveRestore, CarFront, Mail, MessageSquare, QrCode, User } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { adminApi } from '@/services/adminApi'
import { http } from '@/services/apiClient'
import { Button } from '@/components/ui/Button'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import type { AdminVehicleView, VehicleStatus } from '@/types'
import {
  DataList,
  DateCell,
  DetailDialog,
  DetailSection,
  EmptyResults,
  Identity,
  InfoGrid,
  MetricTiles,
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

const STATUS_TONE: Record<VehicleStatus, BadgeTone> = {
  ACTIVE: 'success',
  ARCHIVED: 'neutral',
}

function vehicleTitle(v: AdminVehicleView) {
  return v.nickname || [v.brand, v.model].filter(Boolean).join(' ') || v.licensePlate
}

function ownerName(v: AdminVehicleView) {
  return `${v.owner.firstName} ${v.owner.lastName}`.trim()
}

export default function AdminVehiclesPage() {
  const { t } = useTranslation()
  const f = useAdminFormat()
  const qc = useQueryClient()
  const toast = useToast()
  const list = useListParams({ sort: 'createdAt,desc' })
  const [selected, setSelected] = useState<AdminVehicleView | null>(null)
  const [confirmArchive, setConfirmArchive] = useState<AdminVehicleView | null>(null)

  const q = useQuery({
    queryKey: ['admin', 'vehicles', list.page, list.q, list.status, list.sort],
    queryFn: () => adminApi.vehicles.list(list.page, PAGE_SIZE, list.q, list.status, list.sort),
    placeholderData: keepPreviousData,
  })

  const mut = useMutation({
    mutationFn: ({ v, action }: { v: AdminVehicleView; action: 'archive' | 'restore' }) =>
      action === 'archive' ? adminApi.vehicles.archive(v.id) : adminApi.vehicles.restore(v.id),
    onSuccess: (_d, { v, action }) => {
      qc.invalidateQueries({ queryKey: ['admin', 'vehicles'] })
      qc.invalidateQueries({ queryKey: ['admin', 'stats'] })
      qc.invalidateQueries({ queryKey: ['admin', 'users'] })
      setConfirmArchive(null)
      setSelected(null)
      toast.show(t(action === 'archive' ? 'admin.fleet.archived' : 'admin.fleet.restored', { vehicle: vehicleTitle(v) }))
    },
  })

  if (q.isError) return <ErrorState message={http.humanizeError(q.error)} onRetry={() => q.refetch()} />

  const statusLabel = (s: VehicleStatus) => (s === 'ACTIVE' ? t('vehicle.active') : t('vehicle.archived'))
  const archiveOrRestore = (v: AdminVehicleView) =>
    v.status === 'ACTIVE' ? setConfirmArchive(v) : mut.mutate({ v, action: 'restore' })

  const actionButton = (v: AdminVehicleView) => (
    <Button
      size="sm"
      variant="ghost"
      disabled={mut.isPending}
      onClick={(e) => {
        e.stopPropagation()
        archiveOrRestore(v)
      }}
    >
      {v.status === 'ACTIVE' ? <Archive className="h-4 w-4" aria-hidden /> : <ArchiveRestore className="h-4 w-4" aria-hidden />}
      {v.status === 'ACTIVE' ? t('vehicle.archive') : t('admin.fleet.restore')}
    </Button>
  )

  const messagesCell = (v: AdminVehicleView) => (
    <span className="inline-flex items-center gap-2 tabular-nums">
      {f.num(v.conversationCount)}
      {v.unreadCount > 0 && <Badge tone="warning">{t('admin.fleet.unreadCount', { count: v.unreadCount })}</Badge>}
    </span>
  )

  const columns: Column<AdminVehicleView>[] = [
    {
      key: 'vehicle',
      header: t('admin.fleet.vehicle'),
      cell: (v) => (
        <Identity
          primary={vehicleTitle(v)}
          secondary={v.nickname ? [v.brand, v.model, v.color].filter(Boolean).join(' · ') : v.color ?? undefined}
        />
      ),
    },
    {
      key: 'plate',
      header: t('vehicle.licensePlate'),
      cell: (v) => <PlateChip plate={v.licensePlate} />,
    },
    { key: 'owner', header: t('admin.fleet.owner'), cell: (v) => <Identity avatar={ownerName(v)} primary={ownerName(v)} secondary={v.owner.email} /> },
    { key: 'stickers', header: t('admin.fleet.stickers'), numeric: true, cell: (v) => f.num(v.stickerCount) },
    { key: 'messages', header: t('admin.fleet.messages'), cell: messagesCell },
    { key: 'status', header: t('vehicle.status'), cell: (v) => <Badge tone={STATUS_TONE[v.status]}>{statusLabel(v.status)}</Badge> },
    { key: 'created', header: t('vehicle.created'), cell: (v) => <DateCell iso={v.createdAt} /> },
    { key: 'actions', header: '', className: 'text-end', cell: actionButton },
  ]

  const data = q.data
  const vehicles = data?.content ?? []

  return (
    <div>
      <PageHeader title={t('admin.vehicles')} subtitle={t('admin.fleet.subtitle')} />

      <Toolbar>
        <SearchBox value={list.input} onChange={list.setInput} placeholder={t('admin.fleet.searchPlaceholder')} />
        <StatusTabs
          label={t('vehicle.status')}
          value={list.status}
          onChange={list.setStatus}
          options={[
            { value: '', label: t('common.all') },
            { value: 'ACTIVE', label: t('vehicle.active'), dot: 'bg-success' },
            { value: 'ARCHIVED', label: t('vehicle.archived'), dot: 'bg-muted-fg' },
          ]}
        />
        <div className="sm:ms-auto">
          <SortSelect
            value={list.sort}
            onChange={list.setSort}
            options={[
              { value: 'createdAt,desc', label: t('vehicle.sortNewest') },
              { value: 'createdAt,asc', label: t('vehicle.sortOldest') },
              { value: 'updatedAt,desc', label: t('admin.fleet.sortUpdated') },
              { value: 'licensePlate,asc', label: t('admin.fleet.sortPlate') },
            ]}
          />
        </div>
      </Toolbar>

      <div className={q.isPlaceholderData ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
        <DataList
          rows={vehicles}
          columns={columns}
          rowKey={(v) => v.id}
          rowLabel={(v) => `${vehicleTitle(v)}, ${v.licensePlate}`}
          onOpen={setSelected}
          loading={q.isPending}
          empty={
            <EmptyResults
              icon={<CarFront className="h-6 w-6" aria-hidden />}
              title={list.hasFilters ? t('vehicle.noMatch') : t('admin.fleet.empty')}
              hint={list.hasFilters ? t('admin.list.noResultsHint') : undefined}
              onClear={list.hasFilters ? list.clear : undefined}
            />
          }
          card={(v) => (
            <div className="space-y-3">
              <div className="flex items-start justify-between gap-3">
                <Identity primary={vehicleTitle(v)} secondary={[v.brand, v.model].filter(Boolean).join(' ')} />
                <Badge tone={STATUS_TONE[v.status]}>{statusLabel(v.status)}</Badge>
              </div>
              <div className="flex items-center justify-between gap-3">
                <PlateChip plate={v.licensePlate} />
                <span className="text-xs text-muted-fg">{f.date(v.createdAt)}</span>
              </div>
              <div className="flex items-center gap-2 text-sm min-w-0">
                <User className="h-4 w-4 text-muted-fg shrink-0" aria-hidden />
                <span className="truncate">{ownerName(v)}</span>
              </div>
              <div className="flex items-center justify-between gap-2 text-sm">
                <span className="inline-flex items-center gap-3 text-muted-fg">
                  <span className="inline-flex items-center gap-1"><QrCode className="h-4 w-4" aria-hidden />{f.num(v.stickerCount)}</span>
                  <span className="inline-flex items-center gap-1"><MessageSquare className="h-4 w-4" aria-hidden />{messagesCell(v)}</span>
                </span>
                {actionButton(v)}
              </div>
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
        blockEscape={Boolean(confirmArchive)}
        title={selected ? vehicleTitle(selected) : ''}
        badges={selected && <Badge tone={STATUS_TONE[selected.status]}>{statusLabel(selected.status)}</Badge>}
        subtitle={selected && <PlateChip plate={selected.licensePlate} />}
        footer={
          selected && (
            <>
              {mut.isError && <p className="text-sm text-destructive me-auto">{http.humanizeError(mut.error)}</p>}
              <a href={`mailto:${selected.owner.email}`} className="me-auto inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline">
                <Mail className="h-4 w-4" aria-hidden />
                {t('admin.fleet.emailOwner')}
              </a>
              {selected.status === 'ACTIVE' ? (
                <Button size="sm" variant="outline" disabled={mut.isPending} onClick={() => setConfirmArchive(selected)}>
                  <Archive className="h-4 w-4" aria-hidden />
                  {t('vehicle.archive')}
                </Button>
              ) : (
                <Button size="sm" loading={mut.isPending} onClick={() => mut.mutate({ v: selected, action: 'restore' })}>
                  <ArchiveRestore className="h-4 w-4" aria-hidden />
                  {t('admin.fleet.restore')}
                </Button>
              )}
            </>
          )
        }
      >
        {selected && (
          <>
            <MetricTiles
              items={[
                { icon: QrCode, label: t('admin.fleet.stickers'), value: f.num(selected.stickerCount) },
                { icon: MessageSquare, label: t('admin.fleet.messages'), value: f.num(selected.conversationCount) },
                { icon: Mail, label: t('common.unread'), value: f.num(selected.unreadCount) },
              ]}
            />
            <DetailSection icon={CarFront} title={t('vehicle.details')}>
              <InfoGrid
                items={[
                  { label: t('vehicle.nickname'), value: selected.nickname || <Muted /> },
                  { label: t('vehicle.licensePlate'), value: selected.licensePlate },
                  { label: t('vehicle.brand'), value: selected.brand || <Muted /> },
                  { label: t('vehicle.model'), value: selected.model || <Muted /> },
                  { label: t('vehicle.color'), value: selected.color || <Muted /> },
                  { label: t('admin.fleet.lastContact'), value: selected.lastContactAt ? f.dateTime(selected.lastContactAt) : <Muted>{t('admin.fleet.never')}</Muted> },
                  { label: t('vehicle.created'), value: f.dateTime(selected.createdAt) },
                  { label: t('admin.fleet.updated'), value: f.dateTime(selected.updatedAt) },
                ]}
              />
            </DetailSection>
            <DetailSection icon={User} title={t('admin.fleet.owner')}>
              <div className="flex items-center gap-3 rounded-xl border border-border p-4">
                <Identity avatar={ownerName(selected)} primary={ownerName(selected) || '—'} secondary={selected.owner.email} />
                <Badge tone={selected.owner.active ? 'success' : 'neutral'} className="ms-auto">
                  {selected.owner.active ? t('admin.active') : t('admin.inactive')}
                </Badge>
              </div>
            </DetailSection>
          </>
        )}
      </DetailDialog>

      <ConfirmDialog
        open={Boolean(confirmArchive)}
        title={t('vehicle.archive')}
        description={t('admin.fleet.archiveConfirm', {
          vehicle: confirmArchive ? vehicleTitle(confirmArchive) : '',
          owner: confirmArchive ? ownerName(confirmArchive) : '',
        })}
        confirmLabel={t('vehicle.archive')}
        tone="danger"
        loading={mut.isPending}
        onConfirm={() => confirmArchive && mut.mutate({ v: confirmArchive, action: 'archive' })}
        onCancel={() => setConfirmArchive(null)}
      />
      {toast.node}
    </div>
  )
}

