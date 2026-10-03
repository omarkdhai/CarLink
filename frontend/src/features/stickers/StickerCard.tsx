import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { CarFront, Calendar, Power, ScanLine } from 'lucide-react'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Alert } from '@/components/ui/Alert'
import { useCarColor } from '@/hooks/useCarColor'
import { stickerApi, stickerQueryKeys } from '@/services/stickerApi'
import { http } from '@/services/apiClient'
import { formatDateTime } from '@/lib/format'
import type { StickerStatus, StickerView } from '@/types'

const STATUS_TONE: Record<StickerStatus, 'success' | 'warning' | 'neutral'> = {
  BOUND: 'success',
  UNBOUND: 'warning',
  DEACTIVATED: 'neutral',
}

/**
 * One physical sticker owned by the current user. BOUND stickers show their
 * linked car and the owner-only deactivate control (releases the sticker so the
 * next owner can claim it after selling the car).
 */
export function StickerCard({ sticker }: { sticker: StickerView }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [confirm, setConfirm] = useState(false)
  const [toast, setToast] = useState<string | null>(null)

  const color = useCarColor(sticker.vehicle?.color ?? null)

  const deactivate = useMutation({
    mutationFn: () => stickerApi.deactivate(sticker.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: stickerQueryKeys.mine })
      setConfirm(false)
      setToast(t('stickers.deactivateSuccess'))
    },
    onError: (err) => {
      setConfirm(false)
      setToast(http.humanizeError(err))
    },
  })

  return (
    <div className="bg-surface border border-border rounded-xl p-5 flex flex-col">
      <div className="flex items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <div
            className="h-11 w-11 shrink-0 rounded-lg flex items-center justify-center"
            style={
              sticker.vehicle
                ? { backgroundColor: color.soft, color: color.main }
                : undefined
            }
          >
            {sticker.vehicle ? (
              <CarFront className="h-5 w-5" aria-hidden />
            ) : (
              <ScanLine className="h-5 w-5 text-muted-fg" aria-hidden />
            )}
          </div>
          <div className="min-w-0">
            <Badge tone={STATUS_TONE[sticker.status]}>{t(`stickers.status.${sticker.status}`)}</Badge>
          </div>
        </div>
        {sticker.orderReference && (
          <span className="text-xs text-muted-fg">
            {t('stickers.orderRef')} <span className="font-mono">{sticker.orderReference}</span>
          </span>
        )}
      </div>

      {toast && (
        <div className="mt-3">
          <Alert tone={toast === t('stickers.deactivateSuccess') ? 'success' : 'error'}>{toast}</Alert>
        </div>
      )}

      <div className="mt-4 flex-1">
        {sticker.vehicle ? (
          <>
            <p className="font-bold text-heading truncate">
              {sticker.vehicle.nickname ?? t('stickers.unnamed')}
            </p>
            <p className="text-sm text-muted-fg mt-0.5 truncate">
              {[sticker.vehicle.brand, sticker.vehicle.model, sticker.vehicle.color]
                .filter(Boolean)
                .join(' · ')}
            </p>
            <p className="text-xs text-muted-fg mt-1 font-medium">{sticker.vehicle.licensePlate}</p>
          </>
        ) : (
          <p className="text-sm text-muted-fg">{t('stickers.notLinked')}</p>
        )}
      </div>

      <div className="mt-4 pt-4 border-t border-border flex items-center justify-between gap-2 flex-wrap">
        {sticker.status === 'BOUND' && sticker.boundAt ? (
          <span className="inline-flex items-center gap-1.5 text-xs text-muted-fg">
            <Calendar className="h-3.5 w-3.5" aria-hidden />
            {t('stickers.boundAt')}: {formatDateTime(sticker.boundAt)}
          </span>
        ) : sticker.status === 'DEACTIVATED' && sticker.deactivatedAt ? (
          <span className="inline-flex items-center gap-1.5 text-xs text-muted-fg">
            <Calendar className="h-3.5 w-3.5" aria-hidden />
            {t('stickers.deactivatedAt')}: {formatDateTime(sticker.deactivatedAt)}
          </span>
        ) : (
          <span />
        )}

        {sticker.status === 'BOUND' && (
          <Button variant="outline" size="sm" onClick={() => setConfirm(true)}>
            <Power className="h-4 w-4" aria-hidden />
            {t('stickers.deactivate')}
          </Button>
        )}
      </div>

      <ConfirmDialog
        open={confirm}
        title={t('stickers.deactivate')}
        description={t('stickers.deactivateConfirm')}
        confirmLabel={t('stickers.deactivate')}
        tone="danger"
        loading={deactivate.isPending}
        onConfirm={() => deactivate.mutate()}
        onCancel={() => setConfirm(false)}
      />
    </div>
  )
}