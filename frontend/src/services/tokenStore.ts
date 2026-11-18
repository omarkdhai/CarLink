import axios from 'axios'
import type { AuthResponse, UserResponse } from '@/types'

/**
 * Auth token + session store.
 *
 * Security model (security plan S-09):
 * - The access token lives ONLY in memory — never written to storage, so an
 *   XSS can use it for minutes at most, never carry it away for days.
 * - The refresh token is never visible to JavaScript. The backend keeps it in
 *   an HttpOnly, SameSite=Strict cookie scoped to /api/v1/auth; the browser
 *   sends it to /auth/refresh and /auth/logout by itself.
 * - What we do store is harmless: a "a session probably exists" hint in
 *   localStorage (so a reload or a new tab knows to call /refresh, and an
 *   anonymous visitor makes no call at all) and the cached profile in
 *   sessionStorage (so the UI does not flash blank while /refresh runs).
 */

const SESSION_HINT_KEY = 'carlink.session'
const USER_KEY = 'carlink.user'
const LEGACY_REFRESH_KEY = 'carlink.refreshToken'

/** Anti-CSRF header the backend requires on the cookie endpoints (S-24). */
export const CSRF_HEADERS = { 'X-Requested-With': 'XMLHttpRequest' } as const

interface Subscriber {
  (user: UserResponse | null): void
}

function safe<T>(fn: () => T, fallback: T): T {
  try {
    return fn()
  } catch {
    return fallback
  }
}

// Pre-S-09 builds kept the refresh token itself in sessionStorage: wipe it.
safe(() => sessionStorage.removeItem(LEGACY_REFRESH_KEY), undefined)

let accessToken: string | null = null
let expiresAt: number | null = null // epoch ms
let sessionHint: boolean = safe(() => localStorage.getItem(SESSION_HINT_KEY) === '1', false)
let user: UserResponse | null = readStoredUser()
const listeners = new Set<Subscriber>()

function readStoredUser(): UserResponse | null {
  const raw = safe(() => sessionStorage.getItem(USER_KEY), null)
  if (!raw) return null
  try {
    return JSON.parse(raw) as UserResponse
  } catch {
    safe(() => sessionStorage.removeItem(USER_KEY), undefined)
    return null
  }
}

function persist() {
  safe(() => {
    if (sessionHint) localStorage.setItem(SESSION_HINT_KEY, '1')
    else localStorage.removeItem(SESSION_HINT_KEY)
    if (user) sessionStorage.setItem(USER_KEY, JSON.stringify(user))
    else sessionStorage.removeItem(USER_KEY)
  }, undefined)
}

/** Called on login/register/refresh. */
export function setSession(auth: AuthResponse) {
  accessToken = auth.accessToken
  expiresAt = Date.now() + auth.expiresIn * 1000
  sessionHint = true
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
  expiresAt = null
  sessionHint = false
  user = null
  persist()
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

export function getUser(): UserResponse | null {
  return user
}

/** True if a refresh cookie probably exists and could restore a session. */
export function hasSession(): boolean {
  return sessionHint
}

export function subscribe(listener: Subscriber): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function emit() {
  for (const l of listeners) l(user)
}

// Signing out in one tab signs out the others: the cookie is gone for all of
// them, so drop the in-memory token too instead of waiting for it to expire.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key === SESSION_HINT_KEY && e.newValue === null && sessionHint) {
      accessToken = null
      expiresAt = null
      sessionHint = false
      user = null
      safe(() => sessionStorage.removeItem(USER_KEY), undefined)
      emit()
    }
  })
}

// ---------------------------------------------------------------------------
// Singleton session-restore
// ---------------------------------------------------------------------------
// A single shared Promise for any in-flight /auth/refresh call, so a page
// reload only ever triggers ONE backend call from this tab.
// Refresh tokens are single-use and the cookie is shared by every tab: two
// tabs refreshing at the same instant would both present the same cookie, the
// second would look like token reuse, and the backend would end the session.
// The Web Locks API serialises the call across tabs; the tab that waits then
// sends the already-rotated cookie.
let _refreshPromise: Promise<boolean> | null = null

export function refreshSession(): Promise<boolean> {
  if (!_refreshPromise) {
    _refreshPromise = withCrossTabLock(_doRefresh).finally(() => {
      _refreshPromise = null
    })
  }
  return _refreshPromise
}

function withCrossTabLock(fn: () => Promise<boolean>): Promise<boolean> {
  const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined
  if (!locks?.request) return fn()
  return locks.request('carlink-refresh', fn) as Promise<boolean>
}

async function _doRefresh(): Promise<boolean> {
  if (!sessionHint) return false
  try {
    // Plain axios — NOT apiClient — to avoid re-entering the request
    // interceptor that called us. The cookie goes along by itself (same origin).
    const { data } = await axios.post<AuthResponse>('/api/v1/auth/refresh', null, {
      headers: CSRF_HEADERS,
    })
    setSession(data)
    return true
  } catch {
    clearSession()
    return false
  }
}
