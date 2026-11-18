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
  /** Owner's own number; serialized only to the authenticated owner. */
  phone?: string | null
}

/** The refresh token never reaches JavaScript: it is an HttpOnly cookie (S-09). */
export interface AuthResponse {
  accessToken: string
  expiresIn: number
  user: UserResponse
}

export interface RegisterRequest {
  email: string
  password: string
  firstName: string
  lastName: string
  phone?: string
  /** Short activation code printed under the QR; required to create an account. */
  stickerCode: string
}

export interface LoginRequest {
  email: string
  password: string
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

// ---------- Orders ----------

/** Server-side pricing: SINGLE 20 TND / 1 sticker, DOUBLE 35 / 2, BUSINESS 350 / 20. */
export type StickerPackage = 'SINGLE' | 'DOUBLE' | 'BUSINESS'
export const STICKER_PACKAGES: StickerPackage[] = ['SINGLE', 'DOUBLE', 'BUSINESS']

export type OrderStatus = 'PLACED' | 'DELIVERED' | 'CANCELLED'

export type Governorate =
  | 'TUNIS'
  | 'ARIANA'
  | 'BEN_AROUS'
  | 'MANOUBA'
  | 'NABEUL'
  | 'ZAGHOUAN'
  | 'BIZERTE'
  | 'BEJA'
  | 'JENDOUBA'
  | 'LE_KEF'
  | 'SILIANA'
  | 'SOUSSE'
  | 'MONASTIR'
  | 'MAHDIA'
  | 'SFAX'
  | 'KAIROUAN'
  | 'KASSERINE'
  | 'SIDI_BOUZID'
  | 'GABES'
  | 'MEDENINE'
  | 'TATAOUINE'
  | 'GAFSA'
  | 'TOZEUR'
  | 'KEBILI'
export const GOVERNORATES: Governorate[] = [
  'TUNIS', 'ARIANA', 'BEN_AROUS', 'MANOUBA', 'NABEUL', 'ZAGHOUAN',
  'BIZERTE', 'BEJA', 'JENDOUBA', 'LE_KEF', 'SILIANA', 'SOUSSE',
  'MONASTIR', 'MAHDIA', 'SFAX', 'KAIROUAN', 'KASSERINE', 'SIDI_BOUZID',
  'GABES', 'MEDENINE', 'TATAOUINE', 'GAFSA', 'TOZEUR', 'KEBILI',
]

export interface OrderItemRequest {
  stickerPackage: StickerPackage
  quantity: number
}

export interface OrderCreateRequest {
  items: OrderItemRequest[]
  customerName: string
  mobile: string
  email: string
  governorate: Governorate
  deliveryAddress: string
  deliveryNotes?: string
}

/** Single sticker minted by an order — the raw token is shown exactly once. */
export interface StickerIssuedResponse {
  rawToken: string
  publicUrl: string
  /** base64 PNG data URI of the QR image. */
  imageDataUri: string
  /** Short human-enterable code printed under the QR; shown once. */
  activationCode: string
}

export interface OrderItemResponse {
  stickerPackage: StickerPackage
  quantity: number
  unitPrice: string
  stickersPerPack: number
  subtotal: string
}

export interface OrderResponse {
  reference: string
  status: OrderStatus
  totalAmount: string
  currency: string
  createdAt: ISOInstant
  items: OrderItemResponse[]
  stickers: StickerIssuedResponse[]
}

/** Token-free, PII-free order summary used for status checks. */
export interface OrderSummaryResponse {
  reference: string
  status: OrderStatus
  totalAmount: string
  currency: string
  stickerCount: number
  createdAt: ISOInstant
}

// ---------- Stickers ----------

export type StickerStatus = 'UNBOUND' | 'BOUND' | 'DEACTIVATED'

/** Whether a scanned/typed sticker code is still claimable. */
export type StickerResolveState = 'CLAIMABLE' | 'BOUND'

export interface StickerResolveRequest {
  /** Raw QR token (scan) or short activation code (manual entry). */
  code: string
}

export interface StickerResolveResponse {
  state: StickerResolveState
}

export interface ActivateStickerRequest {
  birthDate: string
  licensePlate: string
  nickname?: string
  brand?: string
  model?: string
  color?: string
}

export interface StickerView {
  id: UUID
  status: StickerStatus
  boundAt: ISOInstant | null
  deactivatedAt: ISOInstant | null
  /** The owner's own car — absent when the sticker isn't bound. */
  vehicle: VehicleResponse | null
  orderReference: string | null
}

// ---------- Public / contact ----------

export type ContactChannel = 'SMS'

/** Lifecycle state of the resolved token (physical sticker or legacy QR). */
export type QrPublicState = 'BOUND' | 'UNBOUND' | 'DEACTIVATED'

export interface QrPublicView {
  state: QrPublicState
  /** Null while the sticker is UNBOUND / DEACTIVATED — no data can leak. */
  vehicle: {
    nickname: string | null
    brand: string | null
    model: string | null
    color: string | null
  } | null
  /** Server-advertised channels, e.g. ['SMS']; empty when not BOUND. */
  channels: ContactChannel[]
}

export type ContactReason = 'BLOCKING' | 'LIGHTS' | 'LEFT_OPEN' | 'HIT_CAR' | 'WINDOWS_OPEN' | 'EV_CHARGE'

export interface ContactSubmitRequest {
  channel: ContactChannel
  message: string
  reason: ContactReason
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
  reason: string
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

/** Spring Data page envelope. */
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface AdminOrderView {
  reference: string
  status: OrderStatus
  totalAmount: string
  currency: string
  stickerCount: number
  customerName: string
  email: string
  mobile: string
  governorate: string
  deliveryAddress: string
  deliveryNotes: string | null
  createdAt: ISOInstant
  items: OrderItemResponse[]
}

export interface AdminContactView {
  id: string
  name: string
  email: string
  mobile: string | null
  reason: string
  note: string | null
  createdAt: ISOInstant
}

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

/** Admin fleet view: vehicle + owner identity + activity counters (never the owner's phone). */
export interface AdminVehicleView {
  id: UUID
  nickname: string | null
  brand: string | null
  model: string | null
  color: string | null
  licensePlate: string
  status: VehicleStatus
  createdAt: ISOInstant
  updatedAt: ISOInstant
  owner: {
    id: UUID
    firstName: string
    lastName: string
    email: string
    active: boolean
  }
  stickerCount: number
  conversationCount: number
  unreadCount: number
  lastContactAt: ISOInstant | null
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
  // Sales analytics
  ordersTotal: number
  ordersPlaced: number
  ordersDelivered: number
  ordersCancelled: number
  totalRevenue: number
  revenueThisMonth: number
  // Time series
  monthlySales: Array<{ month: string; orders: number; revenue: number }>
  // Geographic
  salesByGovernorate: Array<{ governorate: string; count: number; revenue: number }>
  // User behavior
  dailyRegistrations: Array<{ date: string; count: number }>
  hourlyActivity: Array<{ hour: number; count: number }>
  conversionRate: number
}

/** Admin QR-sticker view: order + owner + vehicle (never tokens, codes or phone). */
export interface AdminStickerView {
  id: UUID
  status: StickerStatus
  /** Backfilled from a pre-sticker QR code: no printed activation code. */
  legacy: boolean
  createdAt: ISOInstant
  boundAt: ISOInstant | null
  deactivatedAt: ISOInstant | null
  order: {
    reference: string
    customerName: string
    status: OrderStatus
    createdAt: ISOInstant
  } | null
  owner: {
    id: UUID
    firstName: string
    lastName: string
    email: string
    active: boolean
  } | null
  vehicle: {
    id: UUID
    nickname: string | null
    brand: string | null
    model: string | null
    color: string | null
    licensePlate: string
    status: VehicleStatus
  } | null
}

export type AdminStickerCounts = Record<StickerStatus, number>

// ---------- Admin dashboard ----------

export type DashboardDays = 7 | 30 | 90 | 365

/** A metric for the selected window and the window just before it. */
export interface DashboardKpi {
  value: number
  previous: number
}

export interface DashboardSlice {
  name: string
  count: number
  amount: string
}

export interface AdminDashboardResponse {
  days: DashboardDays
  granularity: 'day' | 'week' | 'month'
  timeZone: string
  generatedAt: ISOInstant
  kpis: {
    revenue: DashboardKpi
    deliveredRevenue: DashboardKpi
    orders: DashboardKpi
    averageOrderValue: DashboardKpi
    stickersSold: DashboardKpi
    activations: DashboardKpi
    newUsers: DashboardKpi
    newVehicles: DashboardKpi
    contacts: DashboardKpi
    deliveryRate: DashboardKpi
  }
  /** `bucket` is an ISO date (YYYY-MM-DD), start of the day/week/month. */
  sales: { bucket: string; orders: number; revenue: string; stickers: number }[]
  growth: { bucket: string; users: number; vehicles: number; activations: number }[]
  contacts: { bucket: string; sent: number; failed: number; other: number }[]
  ordersByStatus: DashboardSlice[]
  packageMix: DashboardSlice[]
  governorates: DashboardSlice[]
  contactReasons: DashboardSlice[]
  contactsByHour: number[]
  stickers: { issued: number; everActivated: number; bound: number; unbound: number; deactivated: number }
  snapshot: {
    usersTotal: number
    usersActive: number
    vehiclesTotal: number
    vehiclesActive: number
    ordersPending: number
    ordersPendingValue: string
    reportsOpen: number
    contactsFailed: number
  }
  recentOrders: {
    reference: string
    customerName: string
    governorate: string
    totalAmount: string
    currency: string
    status: OrderStatus
    createdAt: ISOInstant
  }[]
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

export const CONTACT_CHANNELS: ContactChannel[] = ['SMS']
export const REPORT_STATUSES: ReportStatus[] = ['OPEN', 'REVIEWED', 'CLOSED']
export const REPORT_REASONS: ReportReason[] = ['SPAM', 'ABUSE', 'OTHER']