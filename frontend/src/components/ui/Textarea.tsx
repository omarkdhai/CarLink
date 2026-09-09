import type { TextareaHTMLAttributes } from 'react'
import { forwardRef } from 'react'
import { clsx } from '@/lib/cn'

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  error?: boolean
}

const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { error, className, ...props },
  ref,
) {
  return (
    <textarea
      ref={ref}
      aria-invalid={error || undefined}
      className={clsx(
        'w-full min-h-[96px] px-3.5 py-2.5 rounded-lg bg-surface border text-foreground placeholder:text-muted-fg/60',
        'transition-colors focus-visible:outline-none resize-y',
        error
          ? 'border-destructive focus-visible:border-destructive'
          : 'border-input-border hover:border-primary/50 focus-visible:border-primary',
        className,
      )}
      {...props}
    />
  )
})
Textarea.displayName = 'Textarea'

export { Textarea }