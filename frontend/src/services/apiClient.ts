import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from '@/types'
import {
  getAccessToken,
  getRefreshToken,
  setSession,
  clearSession,
  isAccessTokenExpired,
} from '@/services/tokenStore'
import { errorI18nKey, toApiError } from '@/services/errors'
import i18n from '@/lib/i18n'

/**
 * Central HTTP client for the CarLink API.
 * - Attaches the Bearer access token to every request.
 * - On a 401, attempts ONE background refresh (serialized so concurrent
 *   failures enqueue behind a single /refresh call), then retries the request.
 * - If refresh fails, clears the session and lets the caller decide (redirects
 *   handled in the auth layer / route guards).
 */
const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

let refreshPromise: Promise<boolean> | null = null

async function performRefresh(): Promise<boolean> {
  const token = getRefreshToken()
  if (!token) return false
  try {
    const { data } = await axios.post<AuthResponse>('/api/v1/auth/refresh', {
      refreshToken: token,
    })
    setSession(data)
    return true
  } catch {
    clearSession()
    return false
  }
}

/** Serializes concurrent refresh attempts. */
function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  // Ensure a valid token before sending authenticated requests.
  if (getAccessToken() && isAccessTokenExpired() && getRefreshToken()) {
    const ok = await refreshAccessToken()
    if (!ok) return config // will fail 401 at server
  }
  const token = getAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined
    const status = error.response?.status

    const isAuthEndpoint = original?.url?.includes('/auth/')
    const wasRetried = original?._retried

    // Only attempt a single refresh-on-401 for authenticated non-auth requests.
    if (status === 401 && original && !isAuthEndpoint && !wasRetried && getRefreshToken()) {
      original._retried = true
      const ok = await refreshAccessToken()
      if (ok) {
        original.headers.Authorization = `Bearer ${getAccessToken()}`
        return apiClient(original)
      }
    }

    return Promise.reject(error)
  },
)

export interface HttpService {
  /** Localized, human-friendly message for an error. */
  humanizeError(error: unknown): string
}

export const http: HttpService = {
  humanizeError(error: unknown) {
    const normalized = toApiError(error)
    if (normalized.code === 'NETWORK_ERROR' || normalized.status === 0) {
      return i18n.t(errorI18nKey(normalized.status))
    }
    return normalized.message
  },
}

export default apiClient