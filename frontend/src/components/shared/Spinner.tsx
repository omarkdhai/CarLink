import { cn } from '@/lib/cn'

/** Pulsing-dots loader (from Uiverse.io by adamgiebl). */
export function Spinner({ className }: { className?: string }) {
  return (
    <section className={cn('dots-container', className)} role="status" aria-live="polite" aria-label="Loading">
      {[1, 2, 3, 4, 5].map((i) => (
        <div key={i} className="dot" />
      ))}
    </section>
  )
}

export default Spinner