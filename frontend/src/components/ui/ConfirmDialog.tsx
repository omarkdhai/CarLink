import type { ReactNode } from 'react'
import { useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { AlertTriangle } from 'lucide-react'
import { Button } from '@/components/ui/Button'

export interface ConfirmDialogProps {
  open: boolean
  title: string
  description?: ReactNode
  confirmLabel?: string
  tone?: 'primary' | 'danger'
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * Lightweight, accessible confirmation dialog (no portal dependency).
 * Focuses the cancel button on open, traps Escape → cancel.
 */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  tone = 'danger',
  loading,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const { t } = useTranslation()

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onCancel])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-6"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-title"
    >
      <button
        className="absolute inset-0 bg-heading/40 backdrop-blur-[1px] cursor-default"
        onClick={onCancel}
        aria-label="Close"
        tabIndex={-1}
      />
      <div className="relative w-full sm:max-w-md bg-surface rounded-t-xl sm:rounded-xl shadow-pop border border-border p-6">
        <div className="flex gap-4">
          <div className="h-11 w-11 shrink-0 rounded-full bg-destructive-soft flex items-center justify-center">
            <AlertTriangle className="h-5 w-5 text-destructive" aria-hidden />
          </div>
          <div className="min-w-0">
            <h2 id="confirm-dialog-title" className="text-base font-bold text-heading">
              {title}
            </h2>
            {description && <p className="text-sm text-muted-fg mt-1">{description}</p>}
          </div>
        </div>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="outline" onClick={onCancel} disabled={loading} autoFocus>
            {t('common.cancel')}
          </Button>
          <Button variant={tone} onClick={onConfirm} loading={loading}>
            {confirmLabel ?? t('common.confirm')}
          </Button>
        </div>
      </div>
    </div>
  )
}