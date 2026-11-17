import { cn } from '@/lib/cn'

/** Licence plate styled like a physical plate for fast scanning. */
export function PlateChip({ plate, className }: { plate: string; className?: string }) {
  return (
    <span
      dir="ltr"
      className={cn(
        'inline-flex items-center rounded-md border-2 border-heading/80 bg-surface px-2 py-0.5 font-mono text-xs font-bold tracking-wider text-heading whitespace-nowrap',
        className,
      )}
    >
      {plate}
    </span>
  )
}
