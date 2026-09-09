import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

interface EmptyStateProps {
  icon?: ReactNode
  title: string
  hint?: string
  action?: ReactNode
  compact?: boolean
}

export function EmptyState({ icon, title, hint, action, compact }: EmptyStateProps) {
  const { t } = useTranslation()
  return (
    <div
      className={`flex flex-col items-center justify-center text-center ${compact ? 'py-8 px-4' : 'py-14 px-6'}`}
    >
      {icon && (
        <div className="h-14 w-14 rounded-full bg-muted flex items-center justify-center text-muted-fg mb-4">
          {icon}
        </div>
      )}
      <h3 className="text-base font-bold text-heading">{title}</h3>
      {hint && <p className="text-sm text-muted-fg mt-1.5 max-w-sm">{hint}</p>}
      {action && <div className="mt-5">{action}</div>}
      <span className="sr-only">{t('common.empty')}</span>
    </div>
  )
}