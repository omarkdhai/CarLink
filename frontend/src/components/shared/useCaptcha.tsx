import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import apiClient from '@/services/apiClient'

/**
 * Bot protection for public forms (security plan S-11), Cloudflare Turnstile.
 *
 * The site key comes from GET /api/v1/public/config at runtime, so one build
 * works in every environment. When the backend has captcha off (the default),
 * `required` is false, no script is loaded and nothing is rendered.
 *
 * Usage: render `widget` above the submit button, disable submit while
 * `required && !token`, send `headers()` with the request, and call `reset()`
 * once the request settles (tokens are single-use).
 */

export const CAPTCHA_HEADER = 'X-Captcha-Token'
const SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

interface TurnstileApi {
  render(el: HTMLElement, options: Record<string, unknown>): string
  reset(id: string): void
  remove(id: string): void
}
declare global {
  interface Window {
    turnstile?: TurnstileApi
  }
}

let scriptPromise: Promise<TurnstileApi> | null = null
function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) return Promise.resolve(window.turnstile)
  scriptPromise ??= new Promise((resolve, reject) => {
    const s = document.createElement('script')
    s.src = SCRIPT_SRC
    s.async = true
    s.onload = () => (window.turnstile ? resolve(window.turnstile) : reject(new Error('turnstile missing')))
    s.onerror = () => {
      scriptPromise = null
      reject(new Error('turnstile failed to load'))
    }
    document.head.appendChild(s)
  })
  return scriptPromise
}

export function usePublicConfig() {
  return useQuery({
    queryKey: ['public', 'config'],
    queryFn: () => apiClient.get<{ captchaSiteKey: string | null }>('/public/config').then((r) => r.data),
    staleTime: Infinity,
    retry: 1,
  })
}

export function useCaptcha(): {
  required: boolean
  token: string | null
  widget: ReactNode
  headers: () => Record<string, string>
  reset: () => void
} {
  const { i18n } = useTranslation()
  const siteKey = usePublicConfig().data?.captchaSiteKey ?? null
  // Callback ref in state: the host may mount after the hook (e.g. a panel
  // that opens later), and that must (re)render the widget.
  const [host, setHost] = useState<HTMLDivElement | null>(null)
  const widgetId = useRef<string | null>(null)
  const [token, setToken] = useState<string | null>(null)

  useEffect(() => {
    if (!siteKey || !host) return
    let cancelled = false
    loadTurnstile()
      .then((api) => {
        if (cancelled) return
        widgetId.current = api.render(host, {
          sitekey: siteKey,
          language: i18n.language,
          theme: 'light',
          size: 'flexible',
          callback: (t: string) => setToken(t),
          'expired-callback': () => setToken(null),
          'error-callback': () => setToken(null),
        })
      })
      .catch(() => setToken(null))
    return () => {
      cancelled = true
      if (widgetId.current && window.turnstile) window.turnstile.remove(widgetId.current)
      widgetId.current = null
      setToken(null)
    }
  }, [siteKey, i18n.language, host])

  const reset = useCallback(() => {
    setToken(null)
    if (widgetId.current && window.turnstile) window.turnstile.reset(widgetId.current)
  }, [])

  const headers = useCallback((): Record<string, string> => (token ? { [CAPTCHA_HEADER]: token } : {}), [token])

  const widget = siteKey ? <div ref={setHost} className="mt-4 min-h-[65px]" /> : null

  return { required: Boolean(siteKey), token, widget, headers, reset }
}
