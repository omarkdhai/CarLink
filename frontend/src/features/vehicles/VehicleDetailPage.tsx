import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CarFront, Pencil, QrCode, Archive, Trash2, MessageSquare, Calendar, RefreshCw } from 'lucide-react'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import { http } from '@/services/apiClient'
import { useCarColor } from '@/hooks/useCarColor'
import { VehicleStatusBadge } from '@/features/vehicles/VehicleStatusBadge'
import { ChannelBadge, ConversationStatusBadge } from '@/features/conversations/ConversationBadges'
import { Button, buttonClasses } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { ErrorState } from '@/components/shared/ErrorState'
import { formatDateTime, formatRelative } from '@/lib/format'
import { cn } from '@/lib/cn'

export default function VehicleDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { id = '' } = useParams<{ id: string }>()
  const [confirm, setConfirm] = useState<'archive' | 'delete' | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const vehicle = useQuery({
    queryKey: vehicleQueryKeys.detail(id),
    queryFn: () => vehicleApi.get(id),
    enabled: Boolean(id),
    retry: false,
  })

  const conversations = useQuery({
    queryKey: conversationQueryKeys.list(id),
    queryFn: () => conversationApi.list(id),
    enabled: Boolean(id),
  })

  const mutation = useMutation({
    mutationFn: (type: 'archive' | 'delete') =>
      type === 'archive' ? vehicleApi.archive(id) : vehicleApi.remove(id),
    onSuccess: (_, type) => {
      queryClient.invalidateQueries({ queryKey: vehicleQueryKeys.all })
      navigate(type === 'delete' ? '/vehicles' : `/vehicles/${id}`, { replace: true })
      setConfirm(null)
      if (type === 'archive') setToast(t('vehicle.archiveSuccess'))
    },
    onError: (err) => {
      setConfirm(null)
      setToast(http.humanizeError(err))
    },
  })

  if (vehicle.isPending) {
    return (
      <div className="max-w-3xl mx-auto">
        <div className="bg-surface border border-border rounded-lg p-8 space-y-3">
          <div className="h-5 w-1/3 rounded bg-muted animate-pulse" />
          <div className="h-4 w-2/3 rounded bg-muted animate-pulse" />
          <div className="h-24 rounded-lg bg-muted animate-pulse" />
        </div>
      </div>
    )
  }

  if (vehicle.isError) {
    return <ErrorState message={t('errors.notFound')} />
  }

  const v = vehicle.data!
  const color = useCarColor(v.color)

  return (
    <div className="max-w-3xl mx-auto">
      <Link
        to="/vehicles"
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-fg hover:text-primary mb-4 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        {t('vehicle.title')}
      </Link>

      {toast && (
        <div className="mb-4">
          <Alert tone="success">{toast}</Alert>
        </div>
      )}

      {/* Hero */}
      <div className="bg-surface border border-border rounded-2xl p-6 mb-4">
        <div className="flex flex-col sm:flex-row sm:items-center gap-4">
          <div
            className="h-16 w-16 shrink-0 rounded-2xl flex items-center justify-center"
            style={{ backgroundColor: color.soft, color: color.main }}
          >
            <CarFront className="h-8 w-8" aria-hidden />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <h1 className="text-2xl font-bold text-heading tracking-tight">{v.nickname ?? t('vehicle.details')}</h1>
              <VehicleStatusBadge status={v.status} />
            </div>
            <p className="text-sm text-muted-fg mt-1">
              {[v.brand, v.model].filter(Boolean).join(' ')}
              {v.color ? ` · ${v.color}` : ''}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link to={`/vehicles/${v.id}/edit`} className={buttonClasses({ variant: 'secondary', size: 'sm' })}>
              <Pencil className="h-4 w-4" aria-hidden />
              {t('vehicle.edit')}
            </Link>
            <Link to={`/vehicles/${v.id}/qr`} className={buttonClasses({ size: 'sm' })}>
              <QrCode className="h-4 w-4" aria-hidden />
              {t('vehicle.qr')}
            </Link>
          </div>
        </div>

        {/* Details grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mt-6 pt-5 border-t border-border">
          <Detail label={t('vehicle.licensePlate')} value={v.licensePlate} />
          <Detail label={t('vehicle.brand')} value={v.brand ?? '—'} />
          <Detail label={t('vehicle.model')} value={v.model ?? '—'} />
          <Detail label={t('vehicle.color')} value={v.color ?? '—'} />
        </div>

        <div className="flex items-center gap-4 mt-5 text-xs text-muted-fg flex-wrap">
          <span className="inline-flex items-center gap-1.5">
            <Calendar className="h-3.5 w-3.5" aria-hidden />
            {t('admin.memberSince')} {formatDateTime(v.createdAt)}
          </span>
          <span className="inline-flex items-center gap-1.5">
            <RefreshCw className="h-3.5 w-3.5" aria-hidden />
            {formatRelative(v.updatedAt)}
          </span>
        </div>

        {/* Danger actions */}
        <div className="flex gap-2 mt-6 pt-5 border-t border-border">
          {v.status === 'ACTIVE' ? (
            <Button variant="outline" size="sm" onClick={() => setConfirm('archive')}>
              <Archive className="h-4 w-4" aria-hidden />
              {t('vehicle.archive')}
            </Button>
          ) : null}
          <Button variant="danger" size="sm" onClick={() => setConfirm('delete')}>
            <Trash2 className="h-4 w-4" aria-hidden />
            {t('vehicle.delete')}
          </Button>
        </div>
      </div>

      {/* Conversations for this vehicle */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-lg font-bold text-heading flex items-center gap-2">
            <MessageSquare className="h-5 w-5" aria-hidden />
            {t('message.title')}
          </h2>
          <Link to="/messages" className="text-sm font-semibold text-primary hover:text-primary-hover">
            {t('dashboard.viewAll')}
          </Link>
        </div>

        {conversations.isPending ? (
          <div className="h-28 rounded-lg bg-muted animate-pulse" />
        ) : !conversations.data || conversations.data.length === 0 ? (
          <div className="bg-surface border border-border rounded-lg p-8 text-center">
            <p className="text-sm text-muted-fg">{t('message.empty')}</p>
            <p className="text-xs text-muted-fg mt-1">{t('message.emptyHint')}</p>
          </div>
        ) : (
          <ul className="bg-surface border border-border rounded-lg divide-y divide-border">
            {conversations.data.map((c) => (
              <li key={c.id}>
                <Link to={`/messages/${c.id}`} className="flex items-center gap-3 p-4 hover:bg-muted transition-colors">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className={cn('truncate', c.unread ? 'font-bold text-heading' : 'font-semibold text-foreground')}>
                        {c.lastMessagePreview ?? t('message.pending')}
                      </p>
                      {c.unread && <span className="h-2 w-2 rounded-full bg-primary shrink-0" aria-hidden />}
                    </div>
                    <div className="flex items-center gap-2 mt-1">
                      <ChannelBadge channel={c.channel} />
                      <ConversationStatusBadge status={c.status} />
                    </div>
                  </div>
                  <span className="text-xs text-muted-fg shrink-0">{formatRelative(c.createdAt)}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      <ConfirmDialog
        open={confirm === 'archive'}
        title={t('vehicle.archive')}
        description={t('vehicle.archiveConfirm')}
        confirmLabel={t('vehicle.archive')}
        tone="primary"
        loading={mutation.isPending}
        onConfirm={() => mutation.mutate('archive')}
        onCancel={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirm === 'delete'}
        title={t('vehicle.delete')}
        description={t('vehicle.deleteConfirm')}
        confirmLabel={t('vehicle.delete')}
        tone="danger"
        loading={mutation.isPending}
        onConfirm={() => mutation.mutate('delete')}
        onCancel={() => setConfirm(null)}
      />
    </div>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs text-muted-fg">{label}</p>
      <p className="text-sm font-semibold text-foreground mt-0.5 truncate">{value}</p>
    </div>
  )
}
