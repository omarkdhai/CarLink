import type { ApiError } from '@/types'

/** Error surfaced to the rest of the app after the HTTP layer normalizes it. */
export class ApiRequestError extends Error {
  status: number
  code: string
  fieldErrors: ApiError['fieldErrors']

  constructor(status: number, code: string, message: string, fieldErrors?: ApiError['fieldErrors']) {
    super(message)
    this.name = 'ApiRequestError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

/** Best-effort translation of an HTTP failure + status into the ApiRequestError shape. */
export function toApiError(error: unknown): ApiRequestError {
  if (error instanceof ApiRequestError) return error

  const anyErr = error as { response?: { data?: Partial<ApiError>; status?: number }; message?: string }

  if (anyErr?.response) {
    const data = anyErr.response.data ?? {}
    // Network-level failure without a parsed body still carries a status.
    const status = data.status ?? anyErr.response.status ?? 0
    let message: string
    if (data.code === 'VALIDATION_ERROR' && data.fieldErrors?.length) {
      message = data.fieldErrors.map((f) => f.message).join('; ')
    } else {
      message = data.message ?? `HTTP ${status}`
    }
    return new ApiRequestError(status, data.code ?? 'ERROR', message, data.fieldErrors)
  }

  // No response reached: DNS fail, backend down, CORS, timeout.
  return new ApiRequestError(0, 'NETWORK_ERROR', anyErr?.message ?? 'Network error')
}

/**
 * Localized i18n keys for the stable error codes returned by the backend.
 * These messages explain the real problem in plain language, so users never
 * see raw HTTP status codes like "404" or "503".
 */
const CODE_I18N_KEYS: Record<string, string> = {
  NETWORK_ERROR: 'errors.network',
  VALIDATION_ERROR: 'errors.validation',
  INVALID_CREDENTIALS: 'errors.invalidCredentials',
  UNAUTHORIZED: 'errors.unauthorized',
  FORBIDDEN: 'errors.forbidden',
  NOT_FOUND: 'errors.notFound',
  CONFLICT: 'errors.conflict',
  BAD_REQUEST: 'errors.badRequest',
  MALFORMED_REQUEST: 'errors.malformedRequest',
  METHOD_NOT_ALLOWED: 'errors.methodNotAllowed',
  RATE_LIMIT_EXCEEDED: 'errors.tooManyRequests',
  TOO_MANY_LOGIN_ATTEMPTS: 'errors.tooManyAttempts',
  INTERNAL_ERROR: 'errors.server',
  EMAIL_ALREADY_REGISTERED: 'errors.emailAlreadyRegistered',
  INVALID_STICKER_CODE: 'errors.invalidStickerCode',
  STICKER_ALREADY_BOUND: 'errors.stickerAlreadyBound',
  INVALID_RESET_LINK: 'errors.invalidResetLink',
  PASSWORD_TOO_SHORT: 'auth.passwordTooShort',
  PASSWORD_TOO_LONG: 'errors.passwordTooLong',
  PASSWORD_TOO_COMMON: 'errors.passwordTooCommon',
  PASSWORD_BREACHED: 'errors.passwordBreached',
  INVALID_VERIFICATION_LINK: 'errors.invalidVerificationLink',
}

/** Localized i18n key for a backend error code, when one is known. */
export function errorI18nKeyForCode(code: string | undefined): string | null {
  if (!code) return null
  return CODE_I18N_KEYS[code] ?? null
}

/** Localized i18n key for an HTTP status code (fallback when no code matches). */
export function errorI18nKey(status: number): string {
  switch (status) {
    case 0:
      return 'errors.network'
    case 400:
      return 'errors.badRequest'
    case 401:
      return 'errors.unauthorized'
    case 403:
      return 'errors.forbidden'
    case 404:
      return 'errors.notFound'
    case 405:
      return 'errors.methodNotAllowed'
    case 409:
      return 'errors.conflict'
    case 422:
      return 'errors.validation'
    case 429:
      return 'errors.tooManyRequests'
    case 500:
    case 502:
    case 503:
    case 504:
      return 'errors.server'
    default:
      return 'errors.generic'
  }
}
