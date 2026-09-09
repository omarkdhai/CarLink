import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/AuthContext'
import { loginSchema, type LoginValues } from '@/features/auth/authSchemas'
import { http } from '@/services/apiClient'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Logo } from '@/components/layout/Logo'

interface LocationState {
  from?: { pathname?: string }
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
      const from = (location.state as LocationState | null)?.from?.pathname
      navigate(from ?? '/dashboard', { replace: true })
    } catch (err) {
      setSubmissionError(http.humanizeError(err))
    }
  }

  return (
    <div className="w-full max-w-sm mx-auto flex flex-col gap-6 justify-center my-auto py-8">
      <div className="text-center">
        <Logo className="justify-center" />
        <h1 className="text-2xl font-bold text-heading mt-5">{t('auth.loginTitle')}</h1>
        <p className="text-sm text-muted-fg mt-1">{t('auth.loginSubtitle')}</p>
      </div>

      {submissionError && <Alert tone="error">{submissionError}</Alert>}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 bg-surface border border-border rounded-xl p-6 shadow-card" noValidate>
        <Field label={t('auth.email')} error={errors.email?.message}>
          <Input
            type="email"
            autoComplete="email"
            placeholder={t('auth.emailPlaceholder')}
            error={Boolean(errors.email)}
            {...register('email')}
          />
        </Field>
        <Field label={t('auth.password')} error={errors.password?.message}>
          <Input
            type="password"
            autoComplete="current-password"
            placeholder={t('auth.passwordPlaceholder')}
            error={Boolean(errors.password)}
            {...register('password')}
          />
        </Field>
        <div className="text-end">
          <Link to="/forgot-password" className="text-sm font-medium text-primary hover:underline">
            {t('auth.forgotPassword')}
          </Link>
        </div>
        <Button type="submit" fullWidth loading={isSubmitting}>
          {t('auth.login')}
        </Button>
      </form>

      <p className="text-center text-sm text-muted-fg">
        {t('auth.noAccount')}{' '}
        <Link to="/register" className="font-semibold text-primary hover:underline">
          {t('auth.createAccount')}
        </Link>
      </p>
    </div>
  )
}