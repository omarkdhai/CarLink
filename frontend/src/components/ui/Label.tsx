import type { LabelHTMLAttributes } from 'react'
import { clsx } from '@/lib/cn'

export function Label({ className, ...props }: LabelHTMLAttributes<HTMLLabelElement>) {
  return (
    <label
      className={clsx('block text-sm font-semibold text-heading mb-1.5', className)}
      {...props}
    />
  )
}