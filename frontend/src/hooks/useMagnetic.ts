import type { RefObject } from 'react'
import { canAnimate, gsap, MQ, useGSAP } from '@/lib/gsap'

/**
 * Pulls an element a few pixels towards the pointer while hovered, then
 * springs back on leave. Mouse/trackpad + motion-OK users only; touch and
 * reduced-motion users get the element untouched.
 */
export function useMagnetic<T extends HTMLElement>(ref: RefObject<T | null>, strength = 0.25) {
  useGSAP(
    () => {
      const el = ref.current
      if (!el || !canAnimate()) return
      const mm = gsap.matchMedia()
      mm.add(MQ.FINE_POINTER_MOTION, () => {
        const xTo = gsap.quickTo(el, 'x', { duration: 0.5, ease: 'power3.out' })
        const yTo = gsap.quickTo(el, 'y', { duration: 0.5, ease: 'power3.out' })
        const onMove = (e: PointerEvent) => {
          const r = el.getBoundingClientRect()
          xTo((e.clientX - (r.left + r.width / 2)) * strength)
          yTo((e.clientY - (r.top + r.height / 2)) * strength)
        }
        const onLeave = () => {
          gsap.to(el, { x: 0, y: 0, duration: 0.7, ease: 'elastic.out(1, 0.45)', overwrite: 'auto' })
        }
        el.addEventListener('pointermove', onMove)
        el.addEventListener('pointerleave', onLeave)
        return () => {
          el.removeEventListener('pointermove', onMove)
          el.removeEventListener('pointerleave', onLeave)
        }
      })
      return () => mm.revert()
    },
    { dependencies: [strength], scope: ref },
  )
}
