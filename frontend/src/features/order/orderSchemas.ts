import { z } from 'zod'
import i18n from '@/lib/i18n'
import { GOVERNORATES, type Governorate, type StickerPackage } from '@/types'

const t = (key: string) => i18n.t(key)

export const TN_DIAL_CODE = '+216'
const tnMobile = /^\d{8}$/

/** Compose a full E.164 number (backend contract) from the local 8 digits. */
export function toFullMobile(local: string): string {
  return `${TN_DIAL_CODE}${local.replace(/\D/g, '').replace(/^216/, '')}`
}

/** Step 2 of checkout — personal + delivery details. Mirrors OrderCreateRequest. */
export const orderDetailsSchema = z.object({
  customerName: z.string().trim().min(1, t('validation.nameRequired')).max(100),
  mobile: z
    .string()
    .trim()
    .min(1, t('validation.mobileRequired'))
    .max(8, t('validation.mobileDigits'))
    .regex(tnMobile, t('validation.mobileDigits')),
  email: z.string().trim().min(1, t('validation.emailRequired')).email(t('validation.email')),
  governorate: z
    .string()
    .min(1, t('validation.governorateRequired'))
    .refine((v) => GOVERNORATES.includes(v as Governorate), t('validation.governorateRequired')),
  deliveryAddress: z.string().trim().min(1, t('validation.addressRequired')).max(300),
  deliveryNotes: z.string().max(500).optional().or(z.literal('')),
})

export type OrderDetailsValues = z.infer<typeof orderDetailsSchema>

/** Packages side-by-side with the StickerPackage enum. */
export const PACKS: {
  key: StickerPackage
  price: number
  stickers: number
  badge: string | null
}[] = [
  { key: 'SINGLE', price: 20, stickers: 1, badge: null },
  { key: 'DOUBLE', price: 35, stickers: 2, badge: 'landing.pricingPack2Badge' },
  { key: 'BUSINESS', price: 350, stickers: 20, badge: 'landing.pricingPack3Badge' },
]

export const PACK_MAX_QTY = 50

export function packIsPreselect(value: string | null): value is StickerPackage {
  return value === 'SINGLE' || value === 'DOUBLE' || value === 'BUSINESS'
}