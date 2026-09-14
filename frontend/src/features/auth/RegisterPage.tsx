import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/AuthContext'
import { registerSchema, type RegisterValues } from '@/features/auth/authSchemas'
import { http } from '@/services/apiClient'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Logo } from '@/components/layout/Logo'

export default function RegisterPage() {
  const { t } = useTranslation()
  const { signUp } = useAuth()
  const navigate = useNavigate()
  const [submissionError, setSubmissionError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: '', password: '', firstName: '', lastName: '', phone: '' },
  })

  async function onSubmit(values: RegisterValues) {
    setSubmissionError(null)
    try {
      await signUp({
        email: values.email,
        password: values.password,
        firstName: values.firstName,
        lastName: values.lastName,
        phone: values.phone?.trim() || undefined,
      })
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setSubmissionError(http.humanizeError(err))
    }
  }

  return (
    <div className="w-full max-w-md mx-auto flex flex-col gap-6 justify-center my-auto py-8">
      <div className="text-center">
        <Logo className="justify-center" />
        <h1 className="text-2xl font-bold text-heading mt-5">{t('auth.registerTitle')}</h1>
        <p className="text-sm text-muted-fg mt-1">{t('auth.registerSubtitle')}</p>
      </div>

      {submissionError && <Alert tone="error">{submissionError}</Alert>}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 bg-surface border border-border rounded-xl p-6 shadow-card" noValidate>
        <div className="grid sm:grid-cols-2 gap-4">
          <Field label={t('auth.firstName')} error={errors.firstName?.message}>
            <Input
              placeholder={t('auth.firstNamePlaceholder')}
              autoComplete="given-name"
              error={Boolean(errors.firstName)}
              {...register('firstName')}
            />
          </Field>
          <Field label={t('auth.lastName')} error={errors.lastName?.message}>
            <Input
              placeholder={t('auth.lastNamePlaceholder')}
              autoComplete="family-name"
              error={Boolean(errors.lastName)}
              {...register('lastName')}
            />
          </Field>
        </div>
        <Field label={t('auth.email')} error={errors.email?.message}>
          <Input
            type="email"
            autoComplete="email"
            placeholder={t('auth.emailPlaceholder')}
            error={Boolean(errors.email)}
            {...register('email')}
          />
        </Field>
        <Field label={t('auth.password')} hint={t('auth.passwordRule')} error={errors.password?.message}>
          <Input
            type="password"
            autoComplete="new-password"
            placeholder={t('auth.passwordPlaceholder')}
            error={Boolean(errors.password)}
            {...register('password')}
          />
        </Field>
        <Field label={t('auth.phone')} optional={t('common.optional')} hint={t('auth.phoneHint')} error={errors.phone?.message}>
          <Input
            type="tel"
            autoComplete="tel"
            placeholder={t('auth.phonePlaceholder')}
            inputMode="tel"
            error={Boolean(errors.phone)}
            {...register('phone')}
          />
        </Field>

        <Button type="submit" fullWidth loading={isSubmitting} className="mt-2">
          {t('auth.register')}
        </Button>
      </form>

      <p className="text-center text-sm text-muted-fg">
        {t('auth.haveAccount')}{' '}
        <Link to="/login" className="font-semibold text-primary hover:underline">
          {t('auth.signIn')}
        </Link>
      </p>
    </div>
  )
}