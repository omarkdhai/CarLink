// TypeScript mirror of the CarLink backend contracts.
// Source of truth: backend controllers + DTOs (Spring Boot /api/v1).

export type UUID = string
export type ISOInstant = string

// ---------- Shared ----------

export interface MessageResponse {
  message: string
}

export type Role = 'USER' | 'ADMIN'

export interface ApiError {
  timestamp: ISOInstant
  status: number
  code: string
  message: string
  fieldErrors?: FieldError[]
}

export interface FieldError {
  field: string
  message: string
}

// ---------- Auth ----------

export interface UserResponse {
  id: UUID
  email: string
  firstName: string
  lastName: string
  role: Role
  emailVerified: boolean
  createdAt: ISOInstant
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserResponse
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  phone?: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RefreshRequest {
  refreshToken: string
}

// ---------- User ----------

export interface UpdateProfileRequest {
  firstName: string
  lastName: string
  phone?: string
}

// ---------- Vehicles ----------

export type VehicleStatus = 'ACTIVE' | 'ARCHIVED'

export interface VehicleResponse {
  id: UUID
  nickname: string | null
  brand: string | null
  model: string | null
  color: string | null
  licensePlate: string
  status: VehicleStatus
  createdAt: ISOInstant
  updatedAt: ISOInstant
}

export interface CreateVehicleRequest {
  nickname?: string
  brand?: string
  model?: string
  color?: string
  licensePlate: string
}

export interface UpdateVehicleRequest {
  nickname?: string
  brand?: string
  model?: string
  color?: string
  licensePlate?: string
}

// ---------- QR ----------

export interface QrStatusResponse {
  id: UUID
  vehicleId: UUID
  active: boolean
  activatedAt: ISOInstant
  deactivatedAt: ISOInstant | null
}

export interface QrIssuedResponse {
  /** Shown exactly once after generation. Never returned again. */
  rawToken: string
  /** The URL the QR encodes; the public page lives at {publicUrl}/c/{token}. */
  publicUrl: string
  /** base64 PNG data URI of the QR image. */
  imageDataUri: string
  qr: QrStatusResponse
}

// ---------- Public / contact ----------

export type ContactChannel = 'WHATSAPP' | 'SMS'

export interface QrPublicView {
  vehicle: {
    nickname: string | null
    brand: string | null
    model: string | null
    color: string | null
  }
  /** e.g. ['WHATSAPP', 'SMS'] — never a phone number. */
  channels: ContactChannel[]
}

export interface ContactSubmitRequest {
  channel: ContactChannel
  message: string
}

export interface ContactSubmitResponse {
  conversationId: UUID
  message: string
}

export type ReportReason = 'SPAM' | 'ABUSE' | 'OTHER'

export interface ReportSubmitRequest {
  conversationId: UUID
  reason: ReportReason
  details?: string
}

// ---------- Conversations ----------

export type ConversationStatus = 'PENDING' | 'SENT' | 'FAILED' | 'EXPIRED'

export interface ConversationSummaryResponse {
  id: UUID
  vehicleId: UUID
  vehicleNickname: string | null
  channel: ContactChannel
  status: ConversationStatus
  unread: boolean
  expiresAt: ISOInstant
  createdAt: ISOInstant
  lastMessagePreview: string | null
}

export interface ConversationMessageResponse {
  id: UUID
  content: string
  createdAt: ISOInstant
}

export interface ConversationDetailResponse {
  id: UUID
  vehicleId: UUID
  vehicleNickname: string | null
  channel: ContactChannel
  status: ConversationStatus
  unread: boolean
  expiresAt: ISOInstant
  createdAt: ISOInstant
  messages: ConversationMessageResponse[]
}

// ---------- Admin ----------

export interface AdminUserResponse {
  id: UUID
  email: string
  firstName: string
  lastName: string
  role: Role
  active: boolean
  emailVerified: boolean
  createdAt: ISOInstant
  vehicleCount: number
}

export type ReportStatus = 'OPEN' | 'REVIEWED' | 'CLOSED'

export interface ReportSummaryResponse {
  id: UUID
  reason: ReportReason
  status: ReportStatus
  details: string | null
  reporterIp: string
  createdAt: ISOInstant
  conversationId: UUID | null
  conversationChannel: ContactChannel | null
  vehicleNickname: string | null
}

export interface ReportDetailResponse {
  id: UUID
  reason: ReportReason
  status: ReportStatus
  details: string | null
  reporterIp: string
  createdAt: ISOInstant
  conversation: {
    conversationId: UUID
    channel: ContactChannel
    conversationStatus: ConversationStatus
    createdAt: ISOInstant
    vehicleNickname: string | null
    messageCount: number
    lastMessageContent: string
  } | null
}

export interface AdminStatsResponse {
  usersTotal: number
  usersActive: number
  usersAdmins: number
  vehiclesTotal: number
  vehiclesActive: number
  qrTotal: number
  qrActive: number
  conversationsTotal: number
  conversationsPending: number
  conversationsSent: number
  conversationsFailed: number
  conversationsExpired: number
  messagesTotal: number
  reportsOpen: number
  reportsReviewed: number
  reportsClosed: number
  reportsOpenVsTotal: number
}

export interface AuditLogResponse {
  id: UUID
  action: string
  entityType: string
  entityId: UUID
  actorEmail: string | null
  ipAddress: string
  userAgent: string
  details: Record<string, unknown> | null
  createdAt: ISOInstant
}

// Enumerations shared with server enum names.

export const CONTACT_CHANNELS: ContactChannel[] = ['WHATSAPP', 'SMS']
export const REPORT_STATUSES: ReportStatus[] = ['OPEN', 'REVIEWED', 'CLOSED']
export const REPORT_REASONS: ReportReason[] = ['SPAM', 'ABUSE', 'OTHER']