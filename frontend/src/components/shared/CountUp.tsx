import { useRef, useState } from 'react'
import { canAnimate, gsap, MQ, ScrollTrigger, useGSAP } from '@/lib/gsap'

/**
 * Counts from 0 to `value` once the number scrolls into view (GSAP tween,
 * power3.out, ~0.9s). Reduced-motion users and environments without
 * matchMedia (tests, ancient browsers) see the final value immediately.
 * When `value` changes later (e.g. a live refetch) it tweens from the number
 * currently shown instead of restarting at 0. `format` styles the number.
 */
export function CountUp({
  value,
  duration = 900,
  format = (n: number) => String(n),
}: {
  value: number
  duration?: number
  format?: (n: number) => string
}) {
  const ref = useRef<HTMLSpanElement>(null)
  const current = useRef({ n: 0 })
  const started = useRef(false)
  const [shown, setShown] = useState(value)

  useGSAP(
    () => {
      const el = ref.current
      if (!el || !canAnimate()) {
        setShown(value)
        return
      }
      const mm = gsap.matchMedia()
      mm.add(MQ.MOTION, () => {
        const tweenTo = () =>
          gsap.to(current.current, {
            n: value,
            duration: duration / 1000,
            ease: 'power3.out',
            overwrite: true,
            onUpdate: () => setShown(Math.round(current.current.n)),
          })
        if (started.current) {
          tweenTo()
          return
        }
        setShown(0)
        current.current.n = 0
        ScrollTrigger.create({
          trigger: el,
          start: 'top 90%',
          once: true,
          onEnter: () => {
            started.current = true
            tweenTo()
          },
        })
      })
      mm.add('(prefers-reduced-motion: reduce)', () => {
        current.current.n = value
        setShown(value)
      })
    },
    { dependencies: [value, duration] },
  )

  return (
    <span ref={ref} className="tabular-nums">
      {format(shown)}
    </span>
  )
}

/**
 * Animates the leading integer of a short stat label ("100%", "30 s", "3+").
 * Anything that is not "<digits><suffix>" (e.g. "24/7") is shown as is.
 */
export function CountUpText({ text }: { text: string }) {
  const m = /^(\d+)([^\d/]*)$/.exec(text.trim())
  if (!m) return <span>{text}</span>
  const suffix = m[2]
  return <CountUp value={Number(m[1])} format={(n) => `${n}${suffix}`} />
}
