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
 * Anonymous public endpoints behind a scanned QR. No auth token is attached:
 * apiClient simply omits the Authorization header when there's no session.
 */
export const publicQrApi = {
  /** Safe vehicle summary + advertised channels (never a phone/plate). */
  view(token: string): Promise<QrPublicView> {
    return apiClient.get<QrPublicView>(`${BASE}/${token}`).then((r) => r.data)
  },

  /** Submit a visitor contact request to the vehicle owner. */
  submit(token: string, body: ContactSubmitRequest): Promise<ContactSubmitResponse> {
    return apiClient.post<ContactSubmitResponse>(`${BASE}/${token}/contact`, body).then((r) => r.data)
  },

  /** File an anonymous SPAM/ABUSE/OTHER report against a conversation. */
  report(token: string, body: ReportSubmitRequest): Promise<MessageResponse> {
    return apiClient.post<MessageResponse>(`${BASE}/${token}/report`, body).then((r) => r.data)
  },
}

export const publicQrQueryKeys = {
  view: (token: string) => ['public', 'qr', 'view', token] as const,
}
