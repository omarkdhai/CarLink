import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CarFront, Pencil, QrCode, Archive, Trash2, MessageSquare } from 'lucide-react'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import { conversationApi, conversationQueryKeys } from '@/services/conversationApi'
import { http } from '@/services/apiClient'
import { useCarColor } from '@/hooks/useCarColor'
import { VehicleStatusBadge } from '@/features/vehicles/VehicleStatusBadge'
import { ConversationStatusBadge } from '@/features/conversations/ConversationBadges'
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

  const color = useCarColor(vehicle.data?.color ?? null)

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
      <div className="bg-surface border border-border rounded-xl shadow-sm p-4 mb-4">
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-3">
            <div
              className="h-12 w-12 shrink-0 rounded-lg flex items-center justify-center"
              style={{ backgroundColor: color.soft, color: color.main }}
            >
              <CarFront className="h-6 w-6" aria-hidden />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap mb-1">
                <h1 className="text-lg font-semibold text-heading truncate">{v.nickname ?? t('vehicle.details')}</h1>
                <VehicleStatusBadge status={v.status} />
              </div>
              <p className="text-sm text-muted-fg truncate">
                {[v.brand, v.model, v.licensePlate].filter(Boolean).join(' · ')}
              </p>
            </div>
            <Link to={`/vehicles/${v.id}/edit`} className={buttonClasses({ variant: 'ghost', size: 'sm' })}>
              <Pencil className="h-4 w-4" aria-hidden />
            </Link>
          </div>

          {/* Details */}
          <div className="grid grid-cols-2 gap-2 pt-3 border-t border-border">
            <div className="p-2 rounded-lg bg-muted/30">
              <p className="text-xs text-muted-fg">{t('vehicle.brand')}</p>
              <p className="text-sm text-foreground mt-0.5 truncate">{v.brand ?? '—'}</p>
            </div>
            <div className="p-2 rounded-lg bg-muted/30">
              <p className="text-xs text-muted-fg">{t('vehicle.model')}</p>
              <p className="text-sm text-foreground mt-0.5 truncate">{v.model ?? '—'}</p>
            </div>
            <div className="p-2 rounded-lg bg-muted/30">
              <p className="text-xs text-muted-fg">{t('vehicle.color')}</p>
              <p className="text-sm text-foreground mt-0.5 truncate">{v.color ?? '—'}</p>
            </div>
            <div className="p-2 rounded-lg bg-muted/30">
              <p className="text-xs text-muted-fg">{t('admin.memberSince')}</p>
              <p className="text-sm text-foreground mt-0.5">{formatDateTime(v.createdAt).split(',')[0]}</p>
            </div>
          </div>

          {/* Actions */}
          <div className="flex items-center gap-2 pt-3 border-t border-border">
            <Link to="/stickers" className={buttonClasses({ size: 'sm' }) + ' flex-1 justify-center'}>
              <QrCode className="h-4 w-4" aria-hidden />
              {t('stickers.title')}
            </Link>
            {v.status === 'ACTIVE' ? (
              <Button variant="outline" size="sm" className="flex-1 justify-center" onClick={() => setConfirm('archive')}>
                <Archive className="h-4 w-4" aria-hidden />
                {t('vehicle.archive')}
              </Button>
            ) : null}
            <Button variant="ghost" size="sm" className="text-destructive hover:text-destructive hover:bg-destructive-soft px-2" onClick={() => setConfirm('delete')}>
              <Trash2 className="h-4 w-4" aria-hidden />
            </Button>
          </div>
        </div>
      </div>

      {/* Conversations for this vehicle */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-base font-semibold text-heading flex items-center gap-2">
            <MessageSquare className="h-4 w-4" aria-hidden />
            {t('message.title')}
          </h2>
          {conversations.data && conversations.data.length > 0 && (
            <Link to="/messages" className="text-sm text-primary hover:text-primary-hover">
              {t('dashboard.viewAll')}
            </Link>
          )}
        </div>

        {conversations.isPending ? (
          <div className="h-20 rounded-lg bg-muted animate-pulse" />
        ) : !conversations.data || conversations.data.length === 0 ? (
          <div className="bg-surface border border-border rounded-xl p-6 text-center">
            <p className="text-sm text-muted-fg">{t('message.empty')}</p>
          </div>
        ) : (
          <ul className="bg-surface border border-border rounded-xl divide-y divide-border overflow-hidden">
            {conversations.data.map((c) => (
              <li key={c.id}>
                <Link to={`/messages/${c.id}`} className="flex items-center gap-3 p-3 hover:bg-muted/50 transition-colors">
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center gap-2">
                      <p className={cn('truncate text-sm', c.unread ? 'font-semibold text-heading' : 'text-foreground')}>
                        {c.lastMessagePreview ?? t('message.pending')}
                      </p>
                      {c.unread && <span className="h-1.5 w-1.5 rounded-full bg-primary shrink-0" aria-hidden />}
                    </div>
                    <div className="flex items-center gap-2 mt-1">
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


