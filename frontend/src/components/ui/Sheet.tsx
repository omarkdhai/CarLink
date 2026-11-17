import { useEffect, useRef, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { useTranslation } from 'react-i18next'
import { X } from 'lucide-react'

/**
 * Bottom sheet on phones, centred dialog on larger screens. Closes on Escape
 * or backdrop click, locks page scroll, moves focus in and restores it on close.
 */
export function Sheet({
  open,
  onClose,
  title,
  badges,
  subtitle,
  children,
  footer,
  blockEscape,
  width = 'lg',
}: {
  open: boolean
  onClose: () => void
  title: ReactNode
  badges?: ReactNode
  subtitle?: ReactNode
  children: ReactNode
  footer?: ReactNode
  /** True while a nested confirm dialog owns Escape. */
  blockEscape?: boolean
  /** Panel width on sm+ screens. */
  width?: 'md' | 'lg'
}) {
  const { t } = useTranslation()
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    const previous = document.activeElement as HTMLElement | null
    closeRef.current?.focus()
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = prevOverflow
      previous?.focus?.()
    }
  }, [open])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !blockEscape) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, blockEscape, onClose])

  if (!open) return null
  return createPortal(
    <div className="fixed inset-0 z-50 overflow-y-auto" role="dialog" aria-modal="true" aria-labelledby="sheet-title">
      <button
        className="fixed inset-0 bg-heading/40 backdrop-blur-[1px] cursor-default dialog-backdrop-enter"
        onClick={onClose}
        aria-label={t('common.close')}
        tabIndex={-1}
      />
      <div className="relative min-h-full flex items-end sm:items-center justify-center p-0 sm:p-6">
        <div className={`relative w-full ${width === 'md' ? 'sm:max-w-lg' : 'sm:max-w-2xl'} bg-surface rounded-t-2xl sm:rounded-2xl shadow-pop border border-border flex flex-col max-h-[92vh] dialog-panel-enter`}>
          <div className="sm:hidden mx-auto mt-2 h-1.5 w-10 rounded-full bg-border" aria-hidden />
          <div className="shrink-0 flex items-start justify-between gap-3 px-5 pt-4 pb-4 sm:pt-5 border-b border-border">
            <div className="min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h2 id="sheet-title" className="text-xl font-bold text-heading break-words">
                  {title}
                </h2>
                {badges}
              </div>
              {subtitle && <div className="text-xs text-muted-fg mt-1">{subtitle}</div>}
            </div>
            <button
              ref={closeRef}
              onClick={onClose}
              aria-label={t('common.close')}
              className="h-10 w-10 shrink-0 rounded-lg flex items-center justify-center text-muted-fg hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <X className="h-5 w-5" aria-hidden />
            </button>
          </div>
          <div className="flex-1 min-h-0 overflow-y-auto p-5 space-y-6">{children}</div>
          {footer && (
            <div className="shrink-0 border-t border-border p-4 sm:p-5 flex flex-wrap items-center justify-end gap-2 bg-muted/20 sm:rounded-b-2xl">
              {footer}
            </div>
          )}
        </div>
      </div>
    </div>,
    document.body,
  )
}
