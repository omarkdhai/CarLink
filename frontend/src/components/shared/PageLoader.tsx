import type { ReactNode } from 'react'
import { Spinner } from '@/components/shared/Spinner'

/** Minimal loader for lazy route chunks. */
export function PageLoader() {
  return (
    <div className="min-h-screen flex items-center justify-center loader-enter">
      <Spinner />
    </div>
  )
}

export type { ReactNode }