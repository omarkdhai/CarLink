import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Calendar, Hash, Power, ScanLine } from 'lucide-react'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { PlateChip } from '@/components/shared/PlateChip'
import { VehicleAvatar } from '@/features/account/OwnerUI'
import { stickerApi, stickerQueryKeys } from '@/services/stickerApi'
import { http } from '@/services/apiClient'
import { formatDate } from '@/lib/format'
import type { StickerStatus, StickerView } from '@/types'

const STATUS_TONE: Record<StickerStatus, 'success' | 'warning' | 'neutral'> = {
  BOUND: 'success',
  UNBOUND: 'warning',
  DEACTIVATED: 'neutral',
}

/**
 * One physical sticker owned by the current user. BOUND stickers show their
 * linked car and the owner-only release control (frees the sticker so the
 * next owner can claim it after the car is sold).
 */
export function StickerCard({ sticker, onMessage }: { sticker: StickerView; onMessage: (text: string, error?: boolean) => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [confirm, setConfirm] = useState(false)
  const v = sticker.vehicle

  const deactivate = useMutation({
    mutationFn: () => stickerApi.deactivate(sticker.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: stickerQueryKeys.mine })
      setConfirm(false)
      onMessage(t('stickers.deactivateSuccess'))
    },
    onError: (err) => {
      setConfirm(false)
      onMessage(http.humanizeError(err), true)
    },
  })

  const dated =
    sticker.status === 'BOUND' && sticker.boundAt
      ? { label: t('stickers.boundAt'), at: sticker.boundAt }
      : sticker.status === 'DEACTIVATED' && sticker.deactivatedAt
        ? { label: t('stickers.deactivatedAt'), at: sticker.deactivatedAt }
        : null

  return (
    <article className="h-full bg-surface border border-border rounded-2xl shadow-card flex flex-col">
      <div className="p-5 flex-1">
        <div className="flex items-start gap-3">
          {v ? (
            <VehicleAvatar color={v.color} size="lg" />
          ) : (
            <span className="h-12 w-12 shrink-0 rounded-xl bg-muted text-muted-fg flex items-center justify-center" aria-hidden>
              <ScanLine className="h-6 w-6" />
            </span>
          )}
          <div className="min-w-0 flex-1">
            <div className="flex items-start justify-between gap-2">
              <h3 className="font-bold text-heading truncate leading-snug">
                {v ? v.nickname ?? t('stickers.unnamed') : t('stickers.notLinked')}
              </h3>
              <Badge tone={STATUS_TONE[sticker.status]} className="shrink-0">
                {t(`stickers.status.${sticker.status}`)}
              </Badge>
            </div>
            {v && (
              <p className="text-sm text-muted-fg mt-1 truncate">
                {[v.brand, v.model, v.color].filter(Boolean).join(' · ') || '—'}
              </p>
            )}
          </div>
        </div>

        <dl className="mt-5 space-y-2.5 text-sm">
          {v && (
            <div className="flex items-center justify-between gap-3">
              <dt className="text-muted-fg">{t('stickers.licensePlate')}</dt>
              <dd>
                <PlateChip plate={v.licensePlate} />
              </dd>
            </div>
          )}
          {dated && (
            <div className="flex items-center justify-between gap-3">
              <dt className="text-muted-fg inline-flex items-center gap-1.5">
                <Calendar className="h-4 w-4" aria-hidden />
                {dated.label}
              </dt>
              <dd className="font-medium text-foreground">{formatDate(dated.at)}</dd>
            </div>
          )}
          {sticker.orderReference && (
            <div className="flex items-center justify-between gap-3">
              <dt className="text-muted-fg inline-flex items-center gap-1.5">
                <Hash className="h-4 w-4" aria-hidden />
                {t('stickers.orderRef')}
              </dt>
              <dd className="font-mono text-xs font-semibold text-foreground">{sticker.orderReference}</dd>
            </div>
          )}
        </dl>
      </div>

      {(sticker.status === 'BOUND' || v) && (
        <div className="px-5 py-3 border-t border-border flex items-center justify-between gap-2">
          {v ? (
            <Link to={`/vehicles/${v.id}`} className="text-sm font-semibold text-primary hover:underline">
              {t('stickers.viewVehicle')}
            </Link>
          ) : (
            <span />
          )}
          {sticker.status === 'BOUND' && (
            <Button variant="ghost" size="sm" className="hover:text-destructive! hover:bg-destructive-soft" onClick={() => setConfirm(true)}>
              <Power className="h-4 w-4" aria-hidden />
              {t('stickers.deactivate')}
            </Button>
          )}
        </div>
      )}

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
    </article>
  )
}
