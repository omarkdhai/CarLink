import apiClient from '@/services/apiClient'
import type { ActivateStickerRequest, StickerView } from '@/types'

const BASE = '/stickers'

export const stickerApi = {
  /** Activate (claim) a virgin or deactivated sticker by its raw token. */
  activate(token: string, data: ActivateStickerRequest): Promise<{ message: string }> {
    return apiClient.post<{ message: string }>(`${BASE}/${token}/activate`, data).then((r) => r.data)
  },

  /** Deactivate (release) a BOUND sticker so it can be re-claimed. */
  deactivate(id: string): Promise<void> {
    return apiClient.post(`${BASE}/${id}/deactivate`).then(() => undefined)
  },

  /** List all stickers owned by the current user. */
  listMine(): Promise<StickerView[]> {
    return apiClient.get<StickerView[]>('/me/stickers').then((r) => r.data)
  },
}

export const stickerQueryKeys = {
  mine: ['stickers', 'mine'] as const,
}
