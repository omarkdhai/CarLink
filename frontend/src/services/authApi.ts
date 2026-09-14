import apiClient from '@/services/apiClient'
import type {
  AuthResponse,
  LoginRequest,
  MessageResponse,
  RegisterRequest,
} from '@/types'

const BASE = '/auth'

export const authApi = {
  login(email: string, password: string): Promise<AuthResponse> {
    const body: LoginRequest = { email, password }
    return apiClient.post<AuthResponse>(`${BASE}/login`, body).then((r) => r.data)
  },

  register(data: RegisterRequest): Promise<AuthResponse> {
    return apiClient.post<AuthResponse>(`${BASE}/register`, data).then((r) => r.data)
  },

  refresh(refreshToken: string): Promise<AuthResponse> {
    return apiClient.post<AuthResponse>(`${BASE}/refresh`, { refreshToken }).then((r) => r.data)
  },

  logout(refreshToken?: string): Promise<MessageResponse> {
    const body = refreshToken ? { refreshToken } : {}
    return apiClient.post<MessageResponse>(`${BASE}/logout`, body).then((r) => r.data)
  },

  requestPasswordReset(email: string): Promise<MessageResponse> {
    return apiClient.post<MessageResponse>(`${BASE}/password-reset`, { email }).then((r) => r.data)
  },

  confirmPasswordReset(token: string, newPassword: string): Promise<MessageResponse> {
    return apiClient
      .post<MessageResponse>(`${BASE}/password-reset/confirm`, { token, newPassword })
      .then((r) => r.data)
  },

  verifyEmail(token: string): Promise<MessageResponse> {
    return apiClient.post<MessageResponse>(`${BASE}/verify-email`, { token }).then((r) => r.data)
  },
}

export const { login, register, refresh, logout, requestPasswordReset, confirmPasswordReset, verifyEmail } = authApi