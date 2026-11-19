import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { ArrowRight, ScanLine } from 'lucide-react'
import { useAuth } from '@/features/auth/AuthContext'
import { loginSchema, type LoginValues } from '@/features/auth/authSchemas'
import { AuthFootnote, AuthHeader, AuthShell } from '@/features/auth/AuthShell'
import { http } from '@/services/apiClient'
import { takeNextAfterAuth } from '@/services/nextTarget'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { PasswordInput } from '@/components/ui/PasswordInput'

interface LocationState {
  from?: { pathname?: string }
  /** Set by Settings after a successful account deletion (S-17). */
  accountDeleted?: boolean
}

export default function LoginPage() {
  const { t } = useTranslation()
  const { signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [submissionError, setSubmissionError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  async function onSubmit(values: LoginValues) {
    setSubmissionError(null)
    try {
      await signIn(values.email, values.password)
      // A pending sticker activation outranks the generic "from" redirect.
      const target = takeNextAfterAuth()
      const from = (location.state as LocationState | null)?.from?.pathname
      navigate(target ?? from ?? '/dashboard', { replace: true })
    } catch (err) {
      setSubmissionError(http.humanizeError(err))
    }
  }

  return (
    <AuthShell>
      <AuthHeader title={t('auth.loginTitle')} subtitle={t('auth.loginSubtitle')} />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
        {(location.state as LocationState | null)?.accountDeleted && !submissionError && (
          <Alert tone="success">{t('auth.accountDeleted')}</Alert>
        )}
        {submissionError && (
          <div role="alert">
            <Alert tone="error">{submissionError}</Alert>
          </div>
        )}
        <Field label={t('auth.email')} error={errors.email?.message}>
          <Input
            type="email"
            autoComplete="email"
            autoFocus
            placeholder={t('auth.emailPlaceholder')}
            error={Boolean(errors.email)}
            {...register('email')}
          />
        </Field>
        <div>
          <Field label={t('auth.password')} error={errors.password?.message}>
            <PasswordInput
              autoComplete="current-password"
              placeholder={t('auth.passwordPlaceholder')}
              error={Boolean(errors.password)}
              {...register('password')}
            />
          </Field>
          <div className="mt-2 text-end">
            <Link to="/forgot-password" className="text-sm font-semibold text-primary hover:underline">
              {t('auth.forgotPassword')}
            </Link>
          </div>
        </div>
        <Button type="submit" size="lg" fullWidth loading={isSubmitting}>
          {t('auth.login')}
          <ArrowRight className="h-4 w-4 rtl:rotate-180" aria-hidden />
        </Button>
      </form>

      <AuthFootnote>
        {t('auth.noAccount')}{' '}
        <Link to="/register" className="font-semibold text-primary hover:underline">
          {t('auth.createAccount')}
        </Link>
      </AuthFootnote>

      <Link
        to="/register-sticker"
        className="mt-6 flex items-center gap-3 rounded-2xl border border-dashed border-primary/40 bg-primary-soft/40 p-4 transition-colors hover:border-primary hover:bg-primary-soft"
      >
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-surface text-primary shadow-card" aria-hidden>
          <ScanLine className="h-5 w-5" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block text-sm font-semibold text-heading">{t('authShell.gotSticker')}</span>
          <span className="block text-sm text-muted-fg">{t('authShell.gotStickerHint')}</span>
        </span>
        <ArrowRight className="h-4 w-4 shrink-0 text-primary rtl:rotate-180" aria-hidden />
      </Link>
    </AuthShell>
  )
}
