import apiClient from '@/services/apiClient'
import type {
  AdminStatsResponse,
  AdminUserResponse,
  AuditLogResponse,
  MessageResponse,
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

  stats: {
    overview(): Promise<AdminStatsResponse> {
      return apiClient.get<AdminStatsResponse>(`${ADMIN}/stats/overview`).then((r) => r.data)
    },
  },

  auditLogs: {
    list(filters: AuditLogFilters = {}): Promise<AuditLogResponse[]> {
      return apiClient.get<AuditLogResponse[]>(`${ADMIN}/audit-logs`, { params: filters }).then((r) => r.data)
    },
  },
}

export const adminQueryKeys = {
  users: (filters: AdminUserFilters = {}) => ['admin', 'users', filters] as const,
  reports: (status?: ReportStatus) => ['admin', 'reports', status ?? 'ALL'] as const,
  reportDetail: (id: string) => ['admin', 'reports', 'detail', id] as const,
  stats: ['admin', 'stats', 'overview'] as const,
  auditLogs: (filters: AuditLogFilters = {}) => ['admin', 'audit-logs', filters] as const,
}
