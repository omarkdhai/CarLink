import type { ReactNode } from 'react'
import { CheckCircle2, AlertCircle, Info, AlertTriangle } from 'lucide-react'
import { clsx } from '@/lib/cn'

type Tone = 'success' | 'error' | 'info' | 'warning'

const config: Record<Tone, { icon: typeof Info; classes: string; iconClasses: string }> = {
  success: { icon: CheckCircle2, classes: 'bg-success-soft border-success/30', iconClasses: 'text-success' },
  error: { icon: AlertCircle, classes: 'bg-destructive-soft border-destructive/30', iconClasses: 'text-destructive' },
  info: { icon: Info, classes: 'bg-primary-soft border-primary/30', iconClasses: 'text-primary' },
  warning: { icon: AlertTriangle, classes: 'bg-warning-soft border-warning/30', iconClasses: 'text-warning' },
}

export interface AlertProps {
  tone?: Tone
  title?: string
  children: ReactNode
  className?: string
}

export function Alert({ tone = 'info', title, children, className }: AlertProps) {
  const { icon: Icon, classes, iconClasses } = config[tone]
  return (
    <div className={clsx('flex gap-3 rounded-lg border px-4 py-3', classes, className)} role={tone === 'error' ? 'alert' : 'status'}>
      <Icon className={clsx('h-5 w-5 shrink-0 mt-0.5', iconClasses)} aria-hidden />
      <div className="text-sm">
        {title && <p className="font-semibold text-heading">{title}</p>}
        <div className="text-muted-fg">{children}</div>
      </div>
    </div>
  )
}