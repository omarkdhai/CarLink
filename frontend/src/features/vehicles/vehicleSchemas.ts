import { z } from 'zod'
import i18n from '@/lib/i18n'

const t = (key: string) => i18n.t(key)

export const vehicleSchema = z.object({
  nickname: z.string().max(100).optional().or(z.literal('')),
  brand: z.string().max(100).optional().or(z.literal('')),
  model: z.string().max(100).optional().or(z.literal('')),
  color: z.string().max(50).optional().or(z.literal('')),
  licensePlate: z.string().trim().min(1, t('validation.licensePlateRequired')).max(20),
})

export type VehicleFormValues = z.infer<typeof vehicleSchema>