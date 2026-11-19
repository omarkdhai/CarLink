import { useRef } from 'react'
import { Check } from 'lucide-react'
import { cn } from '@/lib/cn'
import { canAnimate, gsap, MQ, useGSAP } from '@/lib/gsap'

/**
 * Round success badge used on every "it worked" screen (message sent, order
 * placed, sticker activated, contact form sent).
 * Motion (GSAP timeline): the disc pops in, the check mark draws itself, and
 * two rings ripple outwards once. Reduced-motion users see the static badge.
 */
export function SuccessMark({
  className,
  discClassName,
  onSurface = false,
}: {
  /** Size/spacing of the whole mark (default h-16 w-16). */
  className?: string
  /** Extra classes for the disc itself (e.g. a ring). */
  discClassName?: string
  /** White disc with a card shadow, for use on tinted backgrounds. */
  onSurface?: boolean
}) {
  const ref = useRef<HTMLDivElement>(null)

  useGSAP(
    () => {
      if (!canAnimate()) return
      gsap.matchMedia().add(MQ.MOTION, () => {
        const tl = gsap.timeline({ delay: 0.1 })
        tl.from('[data-mark="disc"]', { scale: 0.4, opacity: 0, duration: 0.55, ease: 'back.out(2.2)' })
        const path = ref.current?.querySelector<SVGPathElement>('svg path')
        if (path && typeof path.getTotalLength === 'function') {
          const len = path.getTotalLength()
          tl.fromTo(
            path,
            { strokeDasharray: len, strokeDashoffset: len },
            { strokeDashoffset: 0, duration: 0.45, ease: 'power2.out', clearProps: 'strokeDasharray,strokeDashoffset' },
            '-=0.2',
          )
        }
        tl.fromTo(
          '[data-mark="ring"]',
          { scale: 0.85, opacity: 0.6 },
          { scale: 1.9, opacity: 0, duration: 1, ease: 'power2.out', stagger: 0.2 },
          0.25,
        )
      })
    },
    { scope: ref },
  )

  return (
    <div ref={ref} className={cn('relative mx-auto grid h-16 w-16 place-items-center', className)}>
      <span data-mark="ring" className="absolute inset-0 rounded-full border-2 border-success/40 opacity-0" aria-hidden />
      <span data-mark="ring" className="absolute inset-0 rounded-full border-2 border-success/25 opacity-0" aria-hidden />
      <span
        data-mark="disc"
        className={cn(
          'relative grid h-full w-full place-items-center rounded-full text-success',
          onSurface ? 'bg-surface shadow-card' : 'bg-success-soft',
          discClassName,
        )}
      >
        <Check className="h-1/2 w-1/2" aria-hidden />
      </span>
    </div>
  )
}
