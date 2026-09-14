import type { InputHTMLAttributes } from 'react'
import { forwardRef } from 'react'
import { clsx } from '@/lib/cn'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  error?: boolean
}

const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { error, className, ...props },
  ref,
) {
  return (
    <input
      ref={ref}
      aria-invalid={error || undefined}
      className={clsx(
        'w-full h-11 px-3.5 rounded-lg bg-surface border text-foreground placeholder:text-muted-fg/60',
        'transition-colors focus-visible:outline-none',
        error
          ? 'border-destructive focus-visible:border-destructive'
          : 'border-input-border hover:border-primary/50 focus-visible:border-primary',
        className,
      )}
      {...props}
    />
  )
})
Input.displayName = 'Input'

export { Input }