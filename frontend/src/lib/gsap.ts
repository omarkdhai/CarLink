import { gsap } from 'gsap'
import { Flip } from 'gsap/Flip'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { useGSAP } from '@gsap/react'

/**
 * Single GSAP entry point for the app: plugins are registered once here, and
 * every component imports gsap from this module (never from 'gsap' directly)
 * so registration always happens first.
 */
gsap.registerPlugin(useGSAP, ScrollTrigger, Flip)

/** The app's signature ease, matching --ease-premium in index.css. */
gsap.defaults({ ease: 'power3.out', duration: 0.6 })

/**
 * Media queries for gsap.matchMedia(). Animations are only created under
 * MOTION; reduced-motion users get the final, static layout (matchMedia
 * reverts everything if the preference changes while the page is open).
 */
export const MQ = {
  MOTION: '(prefers-reduced-motion: no-preference)',
  DESKTOP_MOTION: '(prefers-reduced-motion: no-preference) and (min-width: 1024px)',
  FINE_POINTER_MOTION: '(prefers-reduced-motion: no-preference) and (hover: hover) and (pointer: fine)',
} as const

/** jsdom and very old browsers have no matchMedia: animate nothing there. */
export const canAnimate = () => typeof window !== 'undefined' && typeof window.matchMedia === 'function'

export { gsap, Flip, ScrollTrigger, useGSAP }
