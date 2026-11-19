import { z } from 'zod'
import i18n from '@/lib/i18n'
import { PASSWORD_MAX_BYTES, PASSWORD_MIN_LENGTH, isSimplePattern, passwordBytes, passwordLength } from '@/features/auth/passwordRules'

/**
 * Client-side validation schemas (React Hook Form + Zod v4).
 * Mirrors the backend bean-validation rules (see RegisterRequest/LoginRequest).
 * Rely on backend validation as the source of truth; this layer is for UX.
 */

const t = (key: string) => i18n.t(key)

/** New-password rule (security plan S-14); the server also refuses common and breached ones. */
const newPassword = () =>
  z
    .string()
    .refine((v) => passwordLength(v) >= PASSWORD_MIN_LENGTH, t('auth.passwordTooShort'))
    .refine((v) => passwordBytes(v) <= PASSWORD_MAX_BYTES, t('errors.passwordTooLong'))
    .refine((v) => !isSimplePattern(v), t('errors.passwordTooCommon'))

const phoneCode = /^\+[1-9][0-9]{0,3}$/
const nationalNumber = /^[0-9]{6,14}$/

export const loginSchema = z.object({
  email: z.string().min(1, t('validation.emailRequired')).email(t('validation.email')),
  password: z.string().min(1, t('validation.passwordRequired')),
})

export type LoginValues = z.infer<typeof loginSchema>

export const registerSchema = z.object({
  email: z.string().min(1, t('validation.emailRequired')).email(t('validation.email')),
  password: newPassword(),
  firstName: z.string().min(1, t('validation.firstNameRequired')).max(100),
  lastName: z.string().min(1, t('validation.lastNameRequired')).max(100),
  stickerCode: z.string().trim().min(1, t('validation.stickerCodeRequired')).max(32),
  phoneCode: z
    .string()
    .refine((v) => v.trim() === '' || phoneCode.test(v.trim()), t('validation.phoneCodeInvalid')),
  phoneNumber: z
    .string()
    .optional()
    .refine((v) => !v || v.trim() === '' || nationalNumber.test(v.trim()), t('validation.phoneNumberInvalid')),
})

export type RegisterValues = z.infer<typeof registerSchema>

export const forgotPasswordSchema = z.object({
  email: z.string().min(1, t('validation.emailRequired')).email(t('validation.email')),
})

export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>

export const resetPasswordSchema = z
  .object({
    newPassword: newPassword(),
    confirmPassword: z.string().min(1, t('validation.passwordRequired')),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: t('authShell.passwordsMismatch'),
    path: ['confirmPassword'],
  })

export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>

// Public contact form schemas (Phase 5) — declared here so they stay colocated
// with the other Zod schemas.

export const publicContactSchema = z.object({
  reason: z.string().min(1, t('validation.reasonRequired')),
  channel: z.string().min(1, t('validation.methodRequired')),
  message: z.string().max(500, t('validation.messageTooLong')),
})

export type PublicContactValues = z.infer<typeof publicContactSchema>