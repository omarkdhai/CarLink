import { useCallback, useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { AlertCircle, CheckCircle2 } from 'lucide-react'

type ToastMessage = { text: string; error?: boolean }

/**
 * Minimal toast for completed actions (polite live region). Sits above the
 * phone tab bar; disappears after 3.5 s. `show(text, true)` for failures.
 */
export function useToast() {
  const [message, setMessage] = useState<ToastMessage | null>(null)
  useEffect(() => {
    if (!message) return
    const id = window.setTimeout(() => setMessage(null), 3500)
    return () => window.clearTimeout(id)
  }, [message])
  const show = useCallback((text: string | null, error?: boolean) => setMessage(text ? { text, error } : null), [])
  const node = createPortal(
    <div
      className="fixed bottom-24 lg:bottom-6 inset-x-4 sm:inset-x-auto sm:end-6 z-[60] flex justify-center sm:justify-end pointer-events-none"
      role="status"
      aria-live="polite"
    >
      {message && (
        <div className="pointer-events-auto flex items-center gap-2 rounded-xl bg-heading text-white px-4 py-3 text-sm font-medium shadow-pop dialog-panel-enter max-w-md">
          {message.error ? (
            <AlertCircle className="h-4 w-4 shrink-0 text-destructive" aria-hidden />
          ) : (
            <CheckCircle2 className="h-4 w-4 shrink-0 text-success" aria-hidden />
          )}
          {message.text}
        </div>
      )}
    </div>,
    document.body,
  )
  return { show, node }
}
