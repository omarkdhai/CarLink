import apiClient from '@/services/apiClient'
import type { UpdateProfileRequest, UserResponse } from '@/types'

const BASE = '/users'

export const userApi = {
  /** Current user's profile (no phone number is returned). */
  me(): Promise<UserResponse> {
    return apiClient.get<UserResponse>(`${BASE}/me`).then((r) => r.data)
  },

  /** Update own first/last name and (optional) private phone. */
  updateProfile(data: UpdateProfileRequest): Promise<UserResponse> {
    return apiClient.patch<UserResponse>(`${BASE}/me`, data).then((r) => r.data)
  },
}

export const userQueryKeys = {
  me: ['users', 'me'] as const,
}
