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
  signUp: (data: { email: string; password: string; firstName: string; lastName: string; phone?: string; stickerCode: string }) => Promise<void>
  signOut: () => Promise<void>
  /** Replace the in-memory + stored user (e.g. after a profile update). */
  setUser: (next: UserResponse) => void
  /** Audit-friendly role check — never trusted for authorization. */
  isAdmin: boolean
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<UserResponse | null>(() => tokenStore.getUser())
  // isRestoring is true whenever we have a refresh token but the in-memory
  // access token is gone (page reload wipes it).  We must wait for /refresh
  // to complete before letting any authenticated query fire.
  const [isRestoring, setIsRestoring] = useState<boolean>(
    () => tokenStore.hasSession() && !tokenStore.getAccessToken(),
  )

  // Restore the in-memory access token from the persisted refresh token.
  // Runs whenever the access token is absent but a refresh token exists
  // (primarily: every page reload, since the access token lives only in memory).
  useEffect(() => {
    let cancelled = false
    async function restore() {
      // Nothing to restore if there is no session at all.
      if (!tokenStore.hasSession()) {
        setIsRestoring(false)
        return
      }
      // Access token already in memory — no round-trip needed.
      if (tokenStore.getAccessToken()) {
        setIsRestoring(false)
        return
      }
      // Refresh token present but access token missing (page reload): silently
      // obtain a fresh access token.  Keep the cached user visible so the UI
      // does not flash blank while we wait.
      // refreshSession() is a shared singleton — if the apiClient interceptor
      // already started a /refresh call, we wait on the same Promise rather
      // than issuing a second one (which would fail on a single-use token).
      setIsRestoring(true)
      try {
        const ok = await tokenStore.refreshSession()
        if (!cancelled) {
          if (ok) {
            setUserState(tokenStore.getUser())
          } else {
            tokenStore.clearSession()
          }
        }
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
    mutationFn: (data: { email: string; password: string; firstName: string; lastName: string; phone?: string; stickerCode: string }) =>
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
    async (data: { email: string; password: string; firstName: string; lastName: string; phone?: string; stickerCode: string }) => {
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

  const setUser = useCallback((next: UserResponse) => {
    tokenStore.setUser(next)
    setUserState(next)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isRestoring,
      isAuthenticated: user !== null,
      signIn,
      signUp,
      signOut,
      setUser,
      isAdmin: user?.role === 'ADMIN',
    }),
    [user, isRestoring, signIn, signUp, signOut, setUser],
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