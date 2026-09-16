import type { ReactNode } from 'react'

interface PageTransitionProps {
  children: ReactNode
  /** Extra classes merged onto the wrapper (e.g. `flex-1 flex flex-col` in flex-column mains). */
  className?: string
}

/**
 * Wraps page content in a CSS fade-in-up on every route change.
 * Stateless and location-agnostic — layouts add `key={location.pathname}`
 * to replay the animation on navigation (and own their scroll-to-top).
 * The animation is pure CSS (.page-enter); reduced-motion users see nothing
 * move (the global 0.01ms override in index.css).
 */
export function PageTransition({ children, className }: PageTransitionProps) {
  return <div className={className ? `page-enter ${className}` : 'page-enter'}>{children}</div>
}