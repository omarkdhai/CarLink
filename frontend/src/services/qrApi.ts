import apiClient from '@/services/apiClient'
import type { MessageResponse, QrIssuedResponse, QrStatusResponse } from '@/types'

const BASE = '/vehicles'

export const qrApi = {
  /** Generate (or regenerate) the vehicle's QR. The raw token is returned once. */
  issue(vehicleId: string): Promise<QrIssuedResponse> {
    return apiClient.post<QrIssuedResponse>(`${BASE}/${vehicleId}/qr`).then((r) => r.data)
  },

  /** Status of the currently active QR (token/image never returned). */
  current(vehicleId: string): Promise<QrStatusResponse> {
    return apiClient.get<QrStatusResponse>(`${BASE}/${vehicleId}/qr`).then((r) => r.data)
  },

  /** Full QR issuance history (newest first). */
  history(vehicleId: string): Promise<QrStatusResponse[]> {
    return apiClient.get<QrStatusResponse[]>(`${BASE}/${vehicleId}/qr/history`).then((r) => r.data)
  },

  /** Turn off the currently active QR (the contact channel is cut). */
  deactivate(vehicleId: string): Promise<MessageResponse> {
    return apiClient.post<MessageResponse>(`${BASE}/${vehicleId}/qr/deactivate`).then((r) => r.data)
  },
}

export const qrQueryKeys = {
  current: (vehicleId: string) => ['qr', 'current', vehicleId] as const,
  history: (vehicleId: string) => ['qr', 'history', vehicleId] as const,
}
