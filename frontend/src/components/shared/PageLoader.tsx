import type { ReactNode } from 'react'
import { Spinner } from '@/components/shared/Spinner'

/** Minimal loader for lazy route chunks. */
export function PageLoader() {
  return (
    <div className="min-h-[40vh] flex items-center justify-center" role="status" aria-live="polite">
      <div className="flex flex-col items-center gap-3 text-muted-fg">
        <Spinner />
        <span className="text-sm font-semibold">CarLink</span>
      </div>
    </div>
  )
}

export type { ReactNode }