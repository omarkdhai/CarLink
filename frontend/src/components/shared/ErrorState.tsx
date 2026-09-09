import { AlertTriangle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/Button'

interface ErrorStateProps {
  /** Pre-formatted human message to display. */
  message?: string
  /** Async retry callback. */
  onRetry?: () => void
  className?: string
}

export function ErrorState({ message, onRetry, className }: ErrorStateProps) {
  const { t } = useTranslation()

  return (
    <div
      className={`flex flex-col items-center justify-center gap-3 py-12 text-center px-6 ${className ?? ''}`}
      role="alert"
    >
      <div className="h-12 w-12 rounded-full bg-destructive-soft flex items-center justify-center">
        <AlertTriangle className="h-6 w-6 text-destructive" aria-hidden />
      </div>
      <p className="text-foreground font-medium max-w-sm">{message ?? t('errors.generic')}</p>
      {onRetry && (
        <Button variant="outline" onClick={onRetry} className="mt-1">
          {t('common.retry')}
        </Button>
      )}
    </div>
  )
}