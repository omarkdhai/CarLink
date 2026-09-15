import apiClient from '@/services/apiClient'
import type {
  Governorate,
  OrderCreateRequest,
  OrderItemRequest,
  OrderResponse,
  OrderSummaryResponse,
  StickerPackage,
} from '@/types'

const BASE = '/public/orders'

export const orderApi = {
  /** Places a guest COD order. Returns raw sticker tokens exactly once. */
  createOrder(data: OrderCreateRequest): Promise<OrderResponse> {
    return apiClient.post<OrderResponse>(BASE, data).then((r) => r.data)
  },

  /** Token-free, PII-free summary for post-checkout status. */
  getOrder(reference: string): Promise<OrderSummaryResponse> {
    return apiClient.get<OrderSummaryResponse>(`${BASE}/${reference}`).then((r) => r.data)
  },
}

export const orderQueryKeys = {
  create: ['order', 'create'] as const,
  summary: (reference: string) => ['order', 'summary', reference] as const,
}

/** Local package pricing (must match StickerPackage enum server-side). */
export const PACK_PRICES: Record<StickerPackage, { amount: number; stickers: number }> = {
  SINGLE: { amount: 20.00, stickers: 1 },
  DOUBLE: { amount: 35.00, stickers: 2 },
  BUSINESS: { amount: 350.00, stickers: 20 },
}

export const PACK_LABELS: Record<StickerPackage, string> = {
  SINGLE: 'landing.pricingPack1Name',
  DOUBLE: 'landing.pricingPack2Name',
  BUSINESS: 'landing.pricingPack3Name',
}

export const PACK_SUBTITLES: Record<StickerPackage, string> = {
  SINGLE: 'landing.pricingPack1Subtitle',
  DOUBLE: 'landing.pricingPack2Subtitle',
  BUSINESS: 'landing.pricingPack3Subtitle',
}

export const PACK_BADGES: Record<StickerPackage, string | null> = {
  SINGLE: null,
  DOUBLE: 'landing.pricingPack2Badge',
  BUSINESS: 'landing.pricingPack3Badge',
}

/** All 24 Tunisian governorates. */
export const GOVERNORATE_OPTIONS: { value: Governorate; label: string }[] = [
  { value: 'TUNIS', label: 'Tunis' },
  { value: 'ARIANA', label: 'Ariana' },
  { value: 'BEN_AROUS', label: 'Ben Arous' },
  { value: 'MANOUBA', label: 'Manouba' },
  { value: 'NABEUL', label: 'Nabeul' },
  { value: 'ZAGHOUAN', label: 'Zaghouan' },
  { value: 'BIZERTE', label: 'Bizerte' },
  { value: 'BEJA', label: 'Beja' },
  { value: 'JENDOUBA', label: 'Jendouba' },
  { value: 'LE_KEF', label: 'Le Kef' },
  { value: 'SILIANA', label: 'Siliana' },
  { value: 'SOUSSE', label: 'Sousse' },
  { value: 'MONASTIR', label: 'Monastir' },
  { value: 'MAHDIA', label: 'Mahdia' },
  { value: 'SFAX', label: 'Sfax' },
  { value: 'KAIROUAN', label: 'Kairouan' },
  { value: 'KASSERINE', label: 'Kasserine' },
  { value: 'SIDI_BOUZID', label: 'Sidi Bouzid' },
  { value: 'GABES', label: 'Gabes' },
  { value: 'MEDENINE', label: 'Medenine' },
  { value: 'TATAOUINE', label: 'Tataouine' },
  { value: 'GAFSA', label: 'Gafsa' },
  { value: 'TOZEUR', label: 'Tozeur' },
  { value: 'KEBILI', label: 'Kebili' },
]

/** Builds an OrderCreateRequest from the 3-step wizard state. */
export function buildOrderRequest(params: {
  items: OrderItemRequest[]
  customerName: string
  mobile: string
  email: string
  governorate: Governorate
  deliveryAddress: string
  deliveryNotes?: string
}): OrderCreateRequest {
  return {
    items: params.items,
    customerName: params.customerName.trim(),
    mobile: params.mobile.trim(),
    email: params.email.toLowerCase().trim(),
    governorate: params.governorate,
    deliveryAddress: params.deliveryAddress.trim(),
    deliveryNotes: params.deliveryNotes?.trim() || undefined,
  }
}