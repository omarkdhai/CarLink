import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import type { AuthResponse, UserResponse } from '@/types'
import * as tokenStore from '@/services/tokenStore'
import { useMutation } from '@tanstack/react-query'
import { login, register, logout as logoutApi } from '@/services/authApi'

export type AuthFlow = 'login' | 'register'

interface AuthContextValue {
  /** Current authenticated user, or null when signed out. */
  user: UserResponse | null
  /** true while a stored session is being restored via /refresh on boot. */
  isRestoring: boolean
  isAuthenticated: boolean
  signIn: (email: string, password: string) => Promise<void>
  signUp: (data: { email: string; password: string; firstName: string; lastName: string; phone?: string }) => Promise<void>
  signOut: () => Promise<void>
  /** Audit-friendly role check — never trusted for authorization. */
  isAdmin: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<UserResponse | null>(() => tokenStore.getUser())
  const [isRestoring, setIsRestoring] = useState<boolean>(() => tokenStore.hasSession() && !tokenStore.getUser())

  // Restore session silently from the persisted refresh token.
  useEffect(() => {
    let cancelled = false
    async function restore() {
      if (!tokenStore.hasSession() || tokenStore.getUser()) {
        setIsRestoring(false)
        return
      }
      setIsRestoring(true)
      try {
        const { authApi } = await import('@/services/authApi')
        const refreshed = await authApi.refresh(tokenStore.getRefreshToken()!)
        if (!cancelled) {
          tokenStore.setSession(refreshed)
          setUserState(refreshed.user)
        }
      } catch {
        if (!cancelled) tokenStore.clearSession()
      } finally {
        if (!cancelled) setIsRestoring(false)
      }
    }
    void restore()
    return () => {
      cancelled = true
    }
  }, [])

  // External subscriptions (e.g. interceptor clearing session on failed refresh).
  useEffect(() => {
    const unsub = tokenStore.subscribe((next) => {
      setUserState(next)
    })
    return unsub
  }, [])

  const loginMutation = useMutation({
    mutationFn: ({ email, password }: { email: string; password: string }) => login(email, password),
    onSuccess: (data) => {
      tokenStore.setSession(data)
      setUserState(data.user)
    },
  })

  const registerMutation = useMutation({
    mutationFn: (data: { email: string; password: string; firstName: string; lastName: string; phone?: string }) =>
      register(data),
    onSuccess: (data) => {
      tokenStore.setSession(data)
      setUserState(data.user)
    },
  })

  const logoutMutation = useMutation({
    mutationFn: logoutApi,
  })

  const signIn = useCallback(
    async (email: string, password: string) => {
      await loginMutation.mutateAsync({ email, password })
    },
    [loginMutation],
  )

  const signUp = useCallback(
    async (data: { email: string; password: string; firstName: string; lastName: string; phone?: string }) => {
      await registerMutation.mutateAsync(data)
    },
    [registerMutation],
  )

  const signOut = useCallback(async () => {
    try {
      await logoutMutation.mutateAsync()
    } finally {
      tokenStore.clearSession()
      setUserState(null)
    }
  }, [logoutMutation])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isRestoring,
      isAuthenticated: user !== null,
      signIn,
      signUp,
      signOut,
      isAdmin: user?.role === 'ADMIN',
    }),
    [user, isRestoring, signIn, signUp, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}

export type { AuthResponse }