import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/features/auth/AuthContext'
import { FullPageLoader } from '@/components/shared/FullPageLoader'

/**
 * Route guards. The frontend only controls *routing* to the UI —
 * all authorization is enforced server-side (ROLE_ADMIN / ownership checks).
 */

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isRestoring } = useAuth()
  const location = useLocation()

  if (isRestoring) return <FullPageLoader />

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />
  }
  return <>{children}</>
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAdmin, isRestoring } = useAuth()
  const location = useLocation()

  if (isRestoring) return <FullPageLoader />

  if (!isAdmin) {
    // Authenticated but not admin → show a forbidden state rather than bounce.
    return <Navigate to="/403" state={{ from: location }} replace />
  }
  return <>{children}</>
}

/** Guests only — signed-in users are redirected home. */
export function RedirectIfAuthed({ children }: { children: ReactNode }) {
  const { isAuthenticated, isRestoring } = useAuth()

  if (isRestoring) return <FullPageLoader />
  if (isAuthenticated) return <Navigate to="/dashboard" replace />
  return <>{children}</>
}