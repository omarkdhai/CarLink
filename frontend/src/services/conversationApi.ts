import apiClient from '@/services/apiClient'
import type { ConversationDetailResponse, ConversationSummaryResponse, MessageResponse } from '@/types'

const BASE = '/conversations'

export const conversationApi = {
  list(vehicleId?: string): Promise<ConversationSummaryResponse[]> {
    const params = vehicleId ? { vehicleId } : undefined
    return apiClient.get<ConversationSummaryResponse[]>(BASE, { params }).then((r) => r.data)
  },

  get(id: string): Promise<ConversationDetailResponse> {
    return apiClient.get<ConversationDetailResponse>(`${BASE}/${id}`).then((r) => r.data)
  },

  markRead(id: string): Promise<MessageResponse> {
    return apiClient.post<MessageResponse>(`${BASE}/${id}/read`).then((r) => r.data)
  },
}

export const conversationQueryKeys = {
  all: ['conversations'] as const,
  list: (vehicleId?: string) => ['conversations', 'list', vehicleId ?? 'all'] as const,
  detail: (id: string) => ['conversations', 'detail', id] as const,
}