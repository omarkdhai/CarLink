import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Car, QrCode, MessageSquare, MoreHorizontal, Archive, Trash2 } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Card, CardContent } from '@/components/ui/Card'
import { Badge } from '@/components/ui/Badge'
import { VehicleStatusBadge } from '@/features/vehicles/VehicleStatusBadge'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import { http } from '@/services/apiClient'
import { type RgbColor, useCarColor } from '@/hooks/useCarColor'
import type { VehicleResponse } from '@/types'

interface VehicleCardProps {
  vehicle: VehicleResponse
  /** Conversation stats per vehicle (from the conversations list). */
  conversationCount?: number
  unreadCount?: number
  onError: (message: string) => void
}

export function VehicleCard({ vehicle, conversationCount, unreadCount, onError }: VehicleCardProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirm, setConfirm] = useState<'archive' | 'delete' | null>(null)

  const color = useCarColor(vehicle.color)

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: vehicleQueryKeys.all })

  const archiveMutation = useMutation({
    mutationFn: () => vehicleApi.archive(vehicle.id),
    onSuccess: () => {
      invalidate()
      setConfirm(null)
    },
    onError: (err) => {
      onError(http.humanizeError(err))
      setConfirm(null)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => vehicleApi.remove(vehicle.id),
    onSuccess: () => {
      invalidate()
      setConfirm(null)
    },
    onError: (err) => {
      onError(http.humanizeError(err))
      setConfirm(null)
    },
  })

  const displayName =
    vehicle.nickname ?? [vehicle.brand, vehicle.model].filter(Boolean).join(' ') ?? vehicle.licensePlate

  return (
    <>
      <Card variant="interactive" className="relative flex flex-col">
        <Link to={`/vehicles/${vehicle.id}`} className="absolute inset-0 z-10" aria-label={displayName} />
        <CardContent className="flex-1 pt-5">
          <div className="flex items-start justify-between gap-2">
            <div className="flex items-center gap-3 min-w-0">
              <span className="h-11 w-11 shrink-0 rounded-lg flex items-center justify-center" style={{ backgroundColor: color?.soft ?? '#E9EFF8' }}>
                <Car className="h-6 w-6 shrink-0" style={{ color: color?.main ?? '#475569' }} aria-hidden />
              </span>
              <div className="min-w-0">
                <h3 className="font-bold text-heading truncate">{displayName}</h3>
                <p className="text-sm text-muted-fg truncate">{vehicle.licensePlate}</p>
              </div>
            </div>
            <span
              className="relative z-20"
              onClick={(e) => {
                e.preventDefault()
                e.stopPropagation()
              }}
            >
              <button
                className="h-9 w-9 rounded-lg text-muted-fg hover:bg-muted flex items-center justify-center"
                onClick={() => setMenuOpen((o) => !o)}
                aria-haspopup="menu"
                aria-expanded={menuOpen}
                aria-label={t('common.actions')}
              >
                <MoreHorizontal className="h-5 w-5" />
              </button>
              {menuOpen && (
                <div
                  role="menu"
                  className="absolute end-0 mt-1 w-44 bg-surface border border-border rounded-lg shadow-pop py-1 z-30"
                >
                  <button
                    role="menuitem"
                    className="w-full text-start px-3 py-2 text-sm hover:bg-muted flex items-center gap-2"
                    onClick={() => {
                      setMenuOpen(false)
                      setConfirm('archive')
                    }}
                  >
                    <Archive className="h-4 w-4" aria-hidden />
                    {t('vehicle.archive')}
                  </button>
                  <button
                    role="menuitem"
                    className="w-full text-start px-3 py-2 text-sm text-destructive hover:bg-destructive-soft flex items-center gap-2"
                    onClick={() => {
                      setMenuOpen(false)
                      setConfirm('delete')
                    }}
                  >
                    <Trash2 className="h-4 w-4" aria-hidden />
                    {t('vehicle.delete')}
                  </button>
                </div>
              )}
            </span>
          </div>

          <div className="flex flex-wrap items-center gap-1.5 mt-3">
            <VehicleStatusBadge status={vehicle.status} />
            <Badge tone="primary">
              <QrCode className="h-3.5 w-3.5" aria-hidden />
              {t('vehicle.qr')}
            </Badge>
          </div>

          <div className="flex items-center gap-4 mt-4 text-sm text-muted-fg">
            <span className="inline-flex items-center gap-1.5">
              <MessageSquare className="h-4 w-4" aria-hidden />
              {conversationCount ?? 0} {t('vehicle.messages')}
            </span>
            {unreadCount ? (
              <span className="inline-flex items-center gap-1.5 text-primary font-semibold">
                <span className="h-2 w-2 rounded-full bg-primary" aria-hidden />
                {unreadCount} {t('common.unread')}
              </span>
            ) : null}
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={confirm === 'archive'}
        title={t('vehicle.archiveConfirm')}
        confirmLabel={t('vehicle.archive')}
        loading={archiveMutation.isPending}
        onConfirm={() => archiveMutation.mutate()}
        onCancel={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirm === 'delete'}
        title={t('vehicle.deleteConfirm')}
        confirmLabel={t('vehicle.delete')}
        tone="danger"
        loading={deleteMutation.isPending}
        onConfirm={() => deleteMutation.mutate()}
        onCancel={() => setConfirm(null)}
      />
    </>
  )
}

export type { RgbColor }