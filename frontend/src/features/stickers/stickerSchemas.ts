import { z } from 'zod'
import i18n from '@/lib/i18n'

const t = (key: string) => i18n.t(key)

/**
 * Sticker activation form — birth date + the car's license plate (required)
 * with optional descriptive fields. Mirrors ActivateStickerRequest.
 */
export const activateStickerSchema = z.object({
  birthDate: z.string().min(1, t('validation.birthDateRequired')),
  licensePlate: z.string().trim().min(1, t('validation.licensePlateRequired')).max(20),
  nickname: z.string().max(100).optional().or(z.literal('')),
  brand: z.string().max(100).optional().or(z.literal('')),
  model: z.string().max(100).optional().or(z.literal('')),
  color: z.string().max(50).optional().or(z.literal('')),
})

export type ActivateStickerValues = z.infer<typeof activateStickerSchema>