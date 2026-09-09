import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { forwardRef } from 'react'
import { Loader2 } from 'lucide-react'
import { clsx } from '@/lib/cn'

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger' | 'whatsapp' | 'sms'
export type ButtonSize = 'sm' | 'md' | 'lg' | 'icon'

const baseClasses =
  'inline-flex items-center justify-center gap-2 font-semibold rounded-lg ' +
  'touch-target transition-colors duration-150 focus-visible:outline-none ' +
  'disabled:opacity-50 disabled:cursor-not-allowed active:scale-[0.98] whitespace-nowrap'

const variantClasses: Record<ButtonVariant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover active:bg-primary-active',
  secondary: 'bg-primary-soft text-primary hover:bg-primary-muted',
  outline: 'bg-surface border border-border text-foreground hover:bg-muted',
  ghost: 'bg-transparent text-muted-fg hover:bg-muted hover:text-foreground',
  danger: 'bg-destructive text-white hover:bg-destructive-hover',
  whatsapp: 'bg-whatsapp text-white hover:bg-whatsapp-hover',
  sms: 'bg-sms text-white hover:bg-primary-hover',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'h-9 px-3 text-sm',
  md: 'h-11 px-4 text-sm',
  lg: 'h-12 px-6 text-base',
  icon: 'h-11 w-11',
}

/** Classes for button-like links/anchors (no <button> element). */
export function buttonClasses(opts: { variant?: ButtonVariant; size?: ButtonSize; fullWidth?: boolean } = {}) {
  return clsx(
    baseClasses,
    variantClasses[opts.variant ?? 'primary'],
    sizeClasses[opts.size ?? 'md'],
    opts.fullWidth && 'w-full',
  )
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
  fullWidth?: boolean
  leftIcon?: ReactNode
  rightIcon?: ReactNode
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, fullWidth, leftIcon, rightIcon, className, children, disabled, ...props },
  ref,
) {
  return (
    <button
      ref={ref}
      className={buttonClasses({ variant, size, fullWidth }) + (className ? ` ${className}` : '')}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : leftIcon}
      {children}
      {!loading && rightIcon}
    </button>
  )
})
Button.displayName = 'Button'

export { Button }