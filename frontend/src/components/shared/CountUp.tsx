import { useEffect, useRef, useState } from 'react'

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches

/**
 * Counts from 0 to `value` once the number scrolls into view (ease-out, ~0.9s).
 * Renders the final value immediately for reduced-motion users and in
 * environments without IntersectionObserver. `format` styles the number.
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
  const [shown, setShown] = useState(() => (prefersReducedMotion() ? value : 0))
  const started = useRef(false)

  useEffect(() => {
    if (prefersReducedMotion() || typeof IntersectionObserver === 'undefined') {
      setShown(value)
      return
    }
    const el = ref.current
    if (!el) return
    let frame = 0
    const run = () => {
      started.current = true
      const start = performance.now()
      const tick = (now: number) => {
        const t = Math.min(1, (now - start) / duration)
        const eased = 1 - Math.pow(1 - t, 3)
        setShown(Math.round(value * eased))
        if (t < 1) frame = requestAnimationFrame(tick)
      }
      frame = requestAnimationFrame(tick)
    }
    // Value changed after the first run (e.g. live refetch): jump straight to it.
    if (started.current) {
      setShown(value)
      return
    }
    const io = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          io.disconnect()
          run()
        }
      },
      { threshold: 0.4 },
    )
    io.observe(el)
    return () => {
      io.disconnect()
      cancelAnimationFrame(frame)
    }
  }, [value, duration])

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
