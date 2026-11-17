import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

/*
 * Shared rhythm for public pages (pricing, about, contact, order, legal):
 * one content width, one gutter, one title scale — no card-in-card wrappers.
 */

/** Page body: 16/24px gutters, centred, consistent top/bottom spacing. */
export function PublicContainer({ children, className, narrow }: { children: ReactNode; className?: string; narrow?: boolean }) {
  return (
    <div className={cn('mx-auto w-full px-4 sm:px-6 pt-10 pb-16 sm:pt-14 sm:pb-20', narrow ? 'max-w-3xl' : 'max-w-6xl', className)}>
      {children}
    </div>
  )
}

/** Eyebrow + H1 + lead paragraph. */
export function PageIntro({
  eyebrow,
  title,
  lead,
  center,
  children,
}: {
  eyebrow?: string
  title: string
  lead?: string
  center?: boolean
  children?: ReactNode
}) {
  return (
    <header className={cn('mb-10 sm:mb-12', center && 'text-center')}>
      {eyebrow && (
        <p className="mb-3 text-xs font-semibold uppercase tracking-[0.12em] text-primary rtl:tracking-normal">{eyebrow}</p>
      )}
      <h1
        className={cn(
          'text-[2.25rem] font-black leading-[1.08] tracking-[-0.03em] text-heading [text-wrap:balance] sm:text-5xl',
          center && 'mx-auto max-w-[20ch]',
        )}
      >
        {title}
      </h1>
      {lead && (
        <p className={cn('mt-4 max-w-[60ch] text-base leading-relaxed text-muted-fg sm:text-lg', center && 'mx-auto')}>{lead}</p>
      )}
      {children}
    </header>
  )
}
