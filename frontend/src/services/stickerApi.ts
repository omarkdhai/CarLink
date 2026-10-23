import apiClient from '@/services/apiClient'
import type {
  ActivateStickerRequest,
  StickerResolveResponse,
  StickerView,
} from '@/types'

const BASE = '/stickers'

export const stickerApi = {
  /** Activate (claim) a virgin or deactivated sticker by its raw token. */
  activate(token: string, data: ActivateStickerRequest): Promise<{ message: string }> {
    return apiClient.post<{ message: string }>(`${BASE}/${token}/activate`, data).then((r) => r.data)
  },

  /** Claim a sticker by its short printed activation code (authenticated). */
  activateByCode(code: string, details: ActivateStickerRequest): Promise<{ message: string }> {
    return apiClient
      .post<{ message: string }>(`${BASE}/activate-by-code`, { code, details })
      .then((r) => r.data)
  },

  /** Public lookup: is this scanned/typed sticker code still claimable? */
  resolve(code: string): Promise<StickerResolveResponse> {
    return apiClient
      .post<StickerResolveResponse>('/public/stickers/resolve', { code })
      .then((r) => r.data)
  },

  /** Deactivate (release) a BOUND sticker so it can be re-claimed. */
  deactivate(id: string): Promise<void> {
    return apiClient.post(`${BASE}/${id}/deactivate`).then(() => undefined)
  },

  /** List all stickers owned by the current user. */
  listMine(): Promise<StickerView[]> {
    return apiClient.get<StickerView[]>('/stickers/me').then((r) => r.data)
  },
}

export const stickerQueryKeys = {
  mine: ['stickers', 'mine'] as const,
}
