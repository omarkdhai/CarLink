import apiClient from '@/services/apiClient'
import type {
  AdminOrderStickerView,
  AdminDashboardResponse,
  AdminContactView,
  DashboardDays,
  AdminOrderView,
  AdminStickerCounts,
  AdminStickerView,
  AdminStatsResponse,
  AdminUserResponse,
  AdminVehicleView,
  AuditLogResponse,
  MessageResponse,
  PageResponse,
  ReportDetailResponse,
  ReportStatus,
  ReportSummaryResponse,
  Role,
} from '@/types'

const ADMIN = '/admin'

export interface AdminUserFilters {
  q?: string
  active?: boolean
  role?: Role
}

export interface AuditLogFilters {
  action?: string
  entityType?: string
  limit?: number
}

export const adminApi = {
  users: {
    list(filters: AdminUserFilters = {}): Promise<AdminUserResponse[]> {
      return apiClient.get<AdminUserResponse[]>(`${ADMIN}/users`, { params: filters }).then((r) => r.data)
    },
    get(id: string): Promise<AdminUserResponse> {
      return apiClient.get<AdminUserResponse>(`${ADMIN}/users/${id}`).then((r) => r.data)
    },
    deactivate(id: string): Promise<MessageResponse> {
      return apiClient.post<MessageResponse>(`${ADMIN}/users/${id}/deactivate`).then((r) => r.data)
    },
    activate(id: string): Promise<MessageResponse> {
      return apiClient.post<MessageResponse>(`${ADMIN}/users/${id}/activate`).then((r) => r.data)
    },
    changeRole(id: string, role: Role): Promise<MessageResponse> {
      return apiClient.post<MessageResponse>(`${ADMIN}/users/${id}/role`, { role }).then((r) => r.data)
    },
  },

  vehicles: {
    list(
      page = 0,
      size = 30,
      q?: string,
      status?: string,
      sort = 'createdAt,desc',
    ): Promise<PageResponse<AdminVehicleView>> {
      const params: Record<string, unknown> = { page, size, sort }
      if (q && q.trim()) params.q = q.trim()
      if (status) params.status = status
      return apiClient
        .get<PageResponse<AdminVehicleView>>(`${ADMIN}/vehicles`, { params })
        .then((r) => r.data)
    },
    archive(id: string): Promise<MessageResponse> {
      return apiClient.post<MessageResponse>(`${ADMIN}/vehicles/${id}/archive`).then((r) => r.data)
    },
    restore(id: string): Promise<MessageResponse> {
      return apiClient.post<MessageResponse>(`${ADMIN}/vehicles/${id}/restore`).then((r) => r.data)
    },
  },

  reports: {
    list(status?: ReportStatus): Promise<ReportSummaryResponse[]> {
      const params = status ? { status } : undefined
      return apiClient.get<ReportSummaryResponse[]>(`${ADMIN}/reports`, { params }).then((r) => r.data)
    },
    get(id: string): Promise<ReportDetailResponse> {
      return apiClient.get<ReportDetailResponse>(`${ADMIN}/reports/${id}`).then((r) => r.data)
    },
    changeStatus(id: string, status: ReportStatus): Promise<MessageResponse> {
      return apiClient.post<MessageResponse>(`${ADMIN}/reports/${id}/status`, { status }).then((r) => r.data)
    },
    /** Download all reports as CSV. */
    exportCsv(status?: ReportStatus): Promise<Blob> {
      const params = status ? { status } : undefined
      return apiClient
        .get<Blob>(`${ADMIN}/reports/export`, { params, responseType: 'blob' })
        .then((r) => r.data)
    },
  },

  contacts: {
    list(
      page = 0,
      size = 30,
      q?: string,
      sort = 'createdAt,desc',
    ): Promise<PageResponse<AdminContactView>> {
      const params: Record<string, unknown> = { page, size, sort }
      if (q && q.trim()) params.q = q.trim()
      return apiClient
        .get<PageResponse<AdminContactView>>(`${ADMIN}/contacts`, { params })
        .then((r) => r.data)
    },
  },

  orders: {
    list(
      page = 0,
      size = 30,
      q?: string,
      status?: string,
      sort = 'createdAt,desc',
    ): Promise<PageResponse<AdminOrderView>> {
      const params: Record<string, unknown> = { page, size, sort }
      if (q && q.trim()) params.q = q.trim()
      if (status) params.status = status
      return apiClient
        .get<PageResponse<AdminOrderView>>(`${ADMIN}/orders`, { params })
        .then((r) => r.data)
    },
    updateStatus(reference: string, status: string): Promise<void> {
      return apiClient
        .patch<void>(`${ADMIN}/orders/${reference}/status`, { status })
        .then(() => undefined)
    },
    /** QR and activation code of each sticker in the order (audited server-side). */
    stickers(reference: string): Promise<AdminOrderStickerView[]> {
      return apiClient
        .get<AdminOrderStickerView[]>(`${ADMIN}/orders/${reference}/stickers`)
        .then((r) => r.data)
    },
  },

  stats: {
    overview(): Promise<AdminStatsResponse> {
      return apiClient.get<AdminStatsResponse>(`${ADMIN}/stats/overview`).then((r) => r.data)
    },
    dashboard(days: DashboardDays): Promise<AdminDashboardResponse> {
      return apiClient
        .get<AdminDashboardResponse>(`${ADMIN}/stats/dashboard`, { params: { days } })
        .then((r) => r.data)
    },
  },

  auditLogs: {
    list(filters: AuditLogFilters = {}): Promise<AuditLogResponse[]> {
      return apiClient.get<AuditLogResponse[]>(`${ADMIN}/audit-logs`, { params: filters }).then((r) => r.data)
    },
  },

  stickers: {
    list(
      page = 0,
      size = 30,
      q?: string,
      status?: string,
      sort = 'createdAt,desc',
    ): Promise<PageResponse<AdminStickerView>> {
      const params: Record<string, unknown> = { page, size, sort }
      if (q && q.trim()) params.q = q.trim()
      if (status) params.status = status
      return apiClient
        .get<PageResponse<AdminStickerView>>(`${ADMIN}/stickers`, { params })
        .then((r) => r.data)
    },
    counts(): Promise<AdminStickerCounts> {
      return apiClient.get<AdminStickerCounts>(`${ADMIN}/stickers/counts`).then((r) => r.data)
    },
    deactivate(id: string): Promise<MessageResponse> {
      return apiClient.post<MessageResponse>(`${ADMIN}/stickers/${id}/deactivate`).then((r) => r.data)
    },
  },
}

export const adminQueryKeys = {
  users: (filters: AdminUserFilters = {}) => ['admin', 'users', filters] as const,
  reports: (status?: ReportStatus) => ['admin', 'reports', status ?? 'ALL'] as const,
  reportDetail: (id: string) => ['admin', 'reports', 'detail', id] as const,
  stats: ['admin', 'stats', 'overview'] as const,
  dashboard: (days: DashboardDays) => ['admin', 'stats', 'dashboard', days] as const,
  auditLogs: (filters: AuditLogFilters = {}) => ['admin', 'audit-logs', filters] as const,
  stickerCounts: ['admin', 'stickers', 'counts'] as const,
}
