import apiClient from '@/services/apiClient'
import type {
  ContactSubmitRequest,
  ContactSubmitResponse,
  MessageResponse,
  QrPublicView,
  ReportSubmitRequest,
} from '@/types'

const BASE = '/public/qr'

/**
 * The raw QR token travels in this header, never in an API path: paths are
 * written to proxy/access logs (security plan S-08).
 */
export const TOKEN_HEADER = 'X-CarLink-Token'
const withToken = (token: string) => ({ headers: { [TOKEN_HEADER]: token } })

/**
 * Anonymous public endpoints behind a scanned QR. No auth token is attached:
 * apiClient simply omits the Authorization header when there's no session.
 */
export const publicQrApi = {
  /** Safe vehicle summary + advertised channels (never a phone/plate). */
  view(token: string): Promise<QrPublicView> {
    return apiClient.get<QrPublicView>(BASE, withToken(token)).then((r) => r.data)
  },

  /** Submit a visitor contact request to the vehicle owner. */
  submit(token: string, body: ContactSubmitRequest, captcha: Record<string, string> = {}): Promise<ContactSubmitResponse> {
    return apiClient
      .post<ContactSubmitResponse>(`${BASE}/contact`, body, { headers: { ...withToken(token).headers, ...captcha } })
      .then((r) => r.data)
  },

  /** File an anonymous SPAM/ABUSE/OTHER report against a conversation. */
  report(body: ReportSubmitRequest, captcha: Record<string, string> = {}): Promise<MessageResponse> {
    return apiClient.post<MessageResponse>(`${BASE}/report`, body, { headers: captcha }).then((r) => r.data)
  },
}

export const publicQrQueryKeys = {
  view: (token: string) => ['public', 'qr', 'view', token] as const,
}
