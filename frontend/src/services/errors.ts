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
      message = data.fieldErrors.map((f) => f.message).join(' · ')
    } else {
      message = data.message ?? `HTTP ${status}`
    }
    return new ApiRequestError(status, data.code ?? 'ERROR', message, data.fieldErrors)
  }

  // No response reached: DNS fail, backend down, CORS, timeout.
  return new ApiRequestError(0, 'NETWORK_ERROR', anyErr?.message ?? 'Network error')
}

/** Machine-readable internationalization key for a status code. */
export function errorI18nKey(status: number): string {
  switch (status) {
    case 401:
      return 'errors.unauthorized'
    case 403:
      return 'errors.forbidden'
    case 404:
      return 'errors.notFound'
    case 429:
      return 'errors.tooManyRequests'
    case 0:
      return 'errors.network'
    default:
      return 'errors.generic'
  }
}