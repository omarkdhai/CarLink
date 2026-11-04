import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { resetPasswordSchema, type ResetPasswordValues } from '@/features/auth/authSchemas'
import { confirmPasswordReset } from '@/services/authApi'
import { http } from '@/services/apiClient'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Logo } from '@/components/layout/Logo'

export default function ResetPasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [done, setDone] = useState(false)

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  })

  const password = watch('newPassword') ?? ''
  const checks = [
    { label: 'At least 8 characters', ok: password.length >= 8 },
    { label: 'At least 1 number', ok: /\d/.test(password) },
    { label: 'At least 1 special character', ok: /[^A-Za-z0-9]/.test(password) },
  ]
const mutation = useMutation({
    mutationFn: (newPassword: string) => confirmPasswordReset(token, newPassword),
    onSuccess: () => { setDone(true); setTimeout(() => navigate('/login'), 1500) },
  })

  function onSubmit(values: ResetPasswordValues) {
    mutation.mutate(values.newPassword)
  }

  if (!token) {
    return (
      <div className="w-full max-w-sm mx-auto">
        <Alert tone="error">{t('auth.verifyEmailError')}</Alert>
        <Link to="/forgot-password" className="mt-4 inline-block text-sm font-medium text-primary hover:underline">
          {t('auth.forgotPassword')}
        </Link>
      </div>
    )
  }

  return (
    <div className="w-full max-w-sm mx-auto flex flex-col gap-6 justify-center my-auto py-8">
      <div className="text-center">
        <Logo className="justify-center" />
        <h1 className="text-2xl font-bold text-heading mt-5">{t('auth.resetTitle')}</h1>
        <p className="text-sm text-muted-fg mt-1">{t('auth.resetSubtitle')}</p>
      </div>

      {done ? (
        <Alert tone="success">{t('auth.passwordUpdated')}</Alert>
      ) : (
        <>
          {mutation.isError && <Alert tone="error">{http.humanizeError(mutation.error)}</Alert>}
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 bg-surface border border-border rounded-xl p-6 shadow-card" noValidate>
            <Field label={t('auth.newPassword')} hint={t('auth.passwordRule')} error={errors.newPassword?.message}>
              <Input
                type="password"
                autoComplete="new-password"
                placeholder={t('auth.newPasswordPlaceholder')}
                error={Boolean(errors.newPassword)}
                {...register('newPassword')}
              />
<ul className="mt-2 space-y-1 text-xs">
                {checks.map((c) => (
                  <li key={c.label} className={c.ok ? 'text-success' : 'text-muted-fg'}>
                    {c.ok ? '✓' : '•'} {c.label}
                  </li>
                ))}
              </ul>
            </Field>
            <Field label={t('auth.confirmPassword')} error={errors.confirmPassword?.message}>
              <Input
                type="password"
                autoComplete="new-password"
                placeholder={t('auth.newPasswordPlaceholder')}
                error={Boolean(errors.confirmPassword)}
                {...register('confirmPassword')}
              />
            </Field>
            <Button type="submit" fullWidth loading={mutation.isPending}>
              {t('auth.resetPassword')}
            </Button>
          </form>
        </>
      )}
    </div>
  )
}