import type { AuthResponse, UserResponse } from '@/types'

/**
 * Auth token + session store.
 *
 * Security model:
 * - Access token lives ONLY in memory — never written to storage. This bounds
 *   XSS exposure: a malicious script can use the token for seconds, not days.
 * - The refresh token (7-day, single-use, server-revocable) is persisted to
 *   sessionStorage so a page reload can restore the session without a re-login.
 *   sessionStorage is cleared on tab close and is not shared across tabs.
 *   This is a documented tradeoff: the backend issues the refresh token in the
 *   response body (no httpOnly cookie flow), so true cookie-bound storage is
 *   only possible if the backend adds it. tokenStorage isolates the mechanics
 *   so that can be swapped later without touching the rest of the app.
 */

const REFRESH_KEY = 'carlink.refreshToken'
const USER_KEY = 'carlink.user'

interface Subscriber {
  (user: UserResponse | null): void
}

let accessToken: string | null = null
let expiresAt: number | null = null // epoch ms
let refreshToken: string | null = sessionStorage.getItem(REFRESH_KEY)
let user: UserResponse | null = readStoredUser()
const listeners = new Set<Subscriber>()

function readStoredUser(): UserResponse | null {
  const raw = sessionStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserResponse
  } catch {
    sessionStorage.removeItem(USER_KEY)
    return null
  }
}

function persist() {
  if (refreshToken) sessionStorage.setItem(REFRESH_KEY, refreshToken)
  else sessionStorage.removeItem(REFRESH_KEY)
  if (user) sessionStorage.setItem(USER_KEY, JSON.stringify(user))
  else sessionStorage.removeItem(USER_KEY)
}

/** Called on login/register/refresh. */
export function setSession(auth: AuthResponse) {
  accessToken = auth.accessToken
  refreshToken = auth.refreshToken
  expiresAt = Date.now() + auth.expiresIn * 1000
  user = auth.user
  persist()
  emit()
}

export function setUser(next: UserResponse) {
  user = next
  persist()
  emit()
}

export function clearSession() {
  accessToken = null
  refreshToken = null
  expiresAt = null
  user = null
  sessionStorage.removeItem(REFRESH_KEY)
  sessionStorage.removeItem(USER_KEY)
  emit()
}

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string, expiresInSeconds: number) {
  accessToken = token
  expiresAt = Date.now() + expiresInSeconds * 1000
}

export function isAccessTokenExpired(): boolean {
  if (!accessToken || !expiresAt) return true
  // Refresh a little early to avoid racing the expiry.
  return Date.now() > expiresAt - 15_000
}

export function getRefreshToken(): string | null {
  return refreshToken
}

export function getUser(): UserResponse | null {
  return user
}

/** True if a refresh token exists that could restore a session. */
export function hasSession(): boolean {
  return refreshToken !== null
}

export function subscribe(listener: Subscriber): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function emit() {
  for (const l of listeners) l(user)
}