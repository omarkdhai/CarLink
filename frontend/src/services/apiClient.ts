import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import {
  getAccessToken,
  getRefreshToken,
  isAccessTokenExpired,
  refreshSession,
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
 *
 * The refresh singleton lives in tokenStore so AuthContext.restore() and this
 * interceptor share the same Promise — a single-use refresh token can only be
 * consumed once; two concurrent callers must not each fire their own call.
 */
const apiClient = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

apiClient.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  // Proactively obtain a fresh access token when:
  //   a) the token is null  — happens after every page reload since access
  //      tokens live only in memory and are wiped on reload, OR
  //   b) the token exists but is about to expire.
  // refreshSession() is a singleton — if AuthContext.restore() already started
  // a refresh, we wait on the same Promise instead of firing a second one.
  if ((!getAccessToken() || isAccessTokenExpired()) && getRefreshToken()) {
    const ok = await refreshSession()
    if (!ok) return config // refresh failed — request will 401 at the server
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
      const ok = await refreshSession()
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