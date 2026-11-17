import { useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2, LinkIcon, LockKeyhole } from 'lucide-react'
import { resetPasswordSchema, type ResetPasswordValues } from '@/features/auth/authSchemas'
import { AuthFootnote, AuthHeader, AuthShell, PasswordChecklist } from '@/features/auth/AuthShell'
import { confirmPasswordReset } from '@/services/authApi'
import { http } from '@/services/apiClient'
import { Alert } from '@/components/ui/Alert'
import { Button, buttonClasses } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { PasswordInput } from '@/components/ui/PasswordInput'

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

  const mutation = useMutation({
    mutationFn: (newPassword: string) => confirmPasswordReset(token, newPassword),
    onSuccess: () => {
      setDone(true)
      setTimeout(() => navigate('/login'), 2500)
    },
  })

  const backToLogin = (
    <AuthFootnote>
      <Link to="/login" className="inline-flex items-center gap-1.5 font-semibold text-primary hover:underline">
        <ArrowLeft className="h-4 w-4 rtl:rotate-180" aria-hidden />
        {t('authShell.backToLogin')}
      </Link>
    </AuthFootnote>
  )

  if (!token) {
    return (
      <AuthShell>
        <AuthHeader
          icon={LinkIcon}
          tone="bg-destructive-soft text-destructive"
          title={t('authShell.linkInvalidTitle')}
          subtitle={t('auth.verifyEmailError')}
        />
        <Link to="/forgot-password" className={buttonClasses({ size: 'lg', fullWidth: true })}>
          {t('authShell.requestNewLink')}
        </Link>
        {backToLogin}
      </AuthShell>
    )
  }

  if (done) {
    return (
      <AuthShell>
        <AuthHeader
          icon={CheckCircle2}
          tone="bg-success-soft text-success"
          title={t('auth.passwordUpdated')}
          subtitle={t('authShell.redirecting')}
        />
        <Link to="/login" className={buttonClasses({ size: 'lg', fullWidth: true })}>
          {t('auth.signIn')}
        </Link>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <AuthHeader icon={LockKeyhole} title={t('auth.resetTitle')} subtitle={t('auth.resetSubtitle')} />
      <form onSubmit={handleSubmit((v) => mutation.mutate(v.newPassword))} className="space-y-5" noValidate>
        {mutation.isError && (
          <div role="alert">
            <Alert tone="error">{http.humanizeError(mutation.error)}</Alert>
          </div>
        )}
        <div>
          <Field label={t('auth.newPassword')} error={errors.newPassword?.message}>
            <PasswordInput
              autoComplete="new-password"
              autoFocus
              placeholder={t('auth.newPasswordPlaceholder')}
              error={Boolean(errors.newPassword)}
              {...register('newPassword')}
            />
          </Field>
          <PasswordChecklist password={password} />
        </div>
        <Field label={t('auth.confirmPassword')} error={errors.confirmPassword?.message}>
          <PasswordInput
            autoComplete="new-password"
            placeholder={t('auth.newPasswordPlaceholder')}
            error={Boolean(errors.confirmPassword)}
            {...register('confirmPassword')}
          />
        </Field>
        <Button type="submit" size="lg" fullWidth loading={mutation.isPending}>
          {t('auth.resetPassword')}
        </Button>
      </form>
      {backToLogin}
    </AuthShell>
  )
}
