import apiClient from '@/services/apiClient'
import type {
  CreateVehicleRequest,
  MessageResponse,
  UpdateVehicleRequest,
  VehicleResponse,
  VehicleStatus,
} from '@/types'

const BASE = '/vehicles'

export const vehicleApi = {
  list(status?: VehicleStatus | 'ALL'): Promise<VehicleResponse[]> {
    const params = status && status !== 'ALL' ? { status } : undefined
    return apiClient.get<VehicleResponse[]>(BASE, { params }).then((r) => r.data)
  },

  get(id: string): Promise<VehicleResponse> {
    return apiClient.get<VehicleResponse>(`${BASE}/${id}`).then((r) => r.data)
  },

  create(data: CreateVehicleRequest): Promise<VehicleResponse> {
    return apiClient.post<VehicleResponse>(BASE, data).then((r) => r.data)
  },

  update(id: string, data: UpdateVehicleRequest): Promise<VehicleResponse> {
    return apiClient.patch<VehicleResponse>(`${BASE}/${id}`, data).then((r) => r.data)
  },

  archive(id: string): Promise<MessageResponse> {
    return apiClient.post<MessageResponse>(`${BASE}/${id}/archive`).then((r) => r.data)
  },

  remove(id: string): Promise<MessageResponse> {
    return apiClient.delete<MessageResponse>(`${BASE}/${id}`).then((r) => r.data)
  },
}

export const vehicleQueryKeys = {
  all: ['vehicles'] as const,
  list: (status?: VehicleStatus | 'ALL') => ['vehicles', 'list', status ?? 'ALL'] as const,
  detail: (id: string) => ['vehicles', 'detail', id] as const,
}