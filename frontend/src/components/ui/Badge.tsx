import type { HTMLAttributes, ReactNode } from 'react'
import { clsx } from '@/lib/cn'

export type BadgeTone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger' | 'whatsapp' | 'sms'

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone
  children: ReactNode
}

const tones: Record<BadgeTone, string> = {
  neutral: 'bg-muted text-muted-fg',
  primary: 'bg-primary-soft text-primary',
  success: 'bg-success-soft text-success',
  warning: 'bg-warning-soft text-warning',
  danger: 'bg-destructive-soft text-destructive',
  whatsapp: 'bg-success-soft text-whatsapp',
  sms: 'bg-primary-soft text-sms',
}

export function Badge({ tone = 'neutral', className, children, ...props }: BadgeProps) {
  return (
    <span
      className={clsx(
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold leading-5',
        tones[tone],
        className,
      )}
      {...props}
    >
      {children}
    </span>
  )
}