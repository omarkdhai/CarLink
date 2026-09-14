import type { HTMLAttributes, ReactNode } from 'react'
import { clsx } from '@/lib/cn'

type CardVariant = 'default' | 'flat' | 'interactive'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  variant?: CardVariant
  children: ReactNode
}

/**
 * Base surface. `interactive` adds a hover lift — used for vehicle cards and
 * clickable rows. Kept subtle per the Swiss/Flat direction.
 */
const Card = function Card({ variant = 'default', className, children, ...props }: CardProps) {
  return (
    <div
      className={clsx(
        'bg-surface rounded-lg',
        variant === 'default' && 'border border-border shadow-card',
        variant === 'flat' && 'border border-border',
        variant === 'interactive' &&
          'border border-border shadow-card hover:shadow-pop hover:border-primary/30 transition-shadow cursor-pointer',
        className,
      )}
      {...props}
    >
      {children}
    </div>
  )
}

export function CardHeader({ className, children, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={clsx('px-5 pt-5 pb-3 flex items-start justify-between gap-3', className)} {...props}>
      {children}
    </div>
  )
}

export function CardTitle({ className, children, ...props }: HTMLAttributes<HTMLHeadingElement>) {
  return (
    <h3 className={clsx('text-base font-bold text-heading leading-snug', className)} {...props}>
      {children}
    </h3>
  )
}

export function CardDescription({ className, children, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return <p className={clsx('text-sm text-muted-fg mt-0.5', className)} {...props} />
}

export function CardContent({ className, children, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={clsx('px-5 py-3', className)} {...props}>
      {children}
    </div>
  )
}

export function CardFooter({ className, children, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={clsx('px-5 py-4 border-t border-border flex items-center gap-2', className)} {...props}>
      {children}
    </div>
  )
}

export { Card }