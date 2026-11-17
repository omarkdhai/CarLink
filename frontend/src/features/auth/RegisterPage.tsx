import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { ArrowRight, ShieldCheck } from 'lucide-react'
import { useAuth } from '@/features/auth/AuthContext'
import { registerSchema, type RegisterValues } from '@/features/auth/authSchemas'
import { AuthFootnote, AuthHeader, AuthShell, PasswordChecklist } from '@/features/auth/AuthShell'
import { ApiRequestError, toApiError } from '@/services/errors'
import { http } from '@/services/apiClient'
import { takeNextAfterAuth } from '@/services/nextTarget'
import { joinPhone } from '@/lib/phone'
import { Alert } from '@/components/ui/Alert'
import { Button, buttonClasses } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { PasswordInput } from '@/components/ui/PasswordInput'
import { PhoneFields } from '@/components/ui/PhoneFields'

export default function RegisterPage() {
  const { t } = useTranslation()
  const { signUp } = useAuth()
  const navigate = useNavigate()
  const [submissionError, setSubmissionError] = useState<ApiRequestError | null>(null)

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { email: '', password: '', firstName: '', lastName: '', phoneCode: '+216', phoneNumber: '', stickerCode: '' },
  })

  const password = watch('password') ?? ''

  async function onSubmit(values: RegisterValues) {
    setSubmissionError(null)
    try {
      await signUp({
        email: values.email,
        password: values.password,
        firstName: values.firstName,
        lastName: values.lastName,
        phone: joinPhone(values.phoneCode, values.phoneNumber) || undefined,
        stickerCode: values.stickerCode,
      })
      navigate(takeNextAfterAuth() ?? '/dashboard', { replace: true })
    } catch (err) {
      setSubmissionError(toApiError(err))
    }
  }

  return (
    <AuthShell>
      <AuthHeader title={t('auth.registerTitle')} subtitle={t('auth.registerSubtitle')} />

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-5" noValidate>
        {submissionError?.code === 'EMAIL_ALREADY_REGISTERED' ? (
          <div role="alert">
            <Alert tone="error" title={t('auth.emailAlreadyRegistered')}>
              <Link to="/login" className={buttonClasses({ variant: 'outline', size: 'sm' }) + ' mt-2'} data-cy="sign-in-instead">
                {t('auth.signInInstead')}
              </Link>
            </Alert>
          </div>
        ) : (
          submissionError && (
            <div role="alert">
              <Alert tone="error">{http.humanizeError(submissionError)}</Alert>
            </div>
          )
        )}

        <Field label={t('authShell.stickerCode')} hint={t('authShell.stickerCodeHint')} error={errors.stickerCode?.message}>
          <Input
            placeholder={t('registerSticker.codePlaceholder')}
            autoCapitalize="characters"
            spellCheck={false}
            maxLength={32}
            className="font-mono uppercase tracking-widest"
            error={Boolean(errors.stickerCode)}
            {...register('stickerCode')}
          />
        </Field>

        <div className="grid gap-5 sm:grid-cols-2">
          <Field label={t('auth.firstName')} error={errors.firstName?.message}>
            <Input placeholder={t('auth.firstNamePlaceholder')} autoComplete="given-name" error={Boolean(errors.firstName)} {...register('firstName')} />
          </Field>
          <Field label={t('auth.lastName')} error={errors.lastName?.message}>
            <Input placeholder={t('auth.lastNamePlaceholder')} autoComplete="family-name" error={Boolean(errors.lastName)} {...register('lastName')} />
          </Field>
        </div>

        <Field label={t('auth.email')} error={errors.email?.message}>
          <Input type="email" autoComplete="email" placeholder={t('auth.emailPlaceholder')} error={Boolean(errors.email)} {...register('email')} />
        </Field>

        <div>
          <Field label={t('auth.password')} error={errors.password?.message}>
            <PasswordInput
              autoComplete="new-password"
              placeholder={t('auth.newPasswordPlaceholder')}
              error={Boolean(errors.password)}
              {...register('password')}
            />
          </Field>
          <PasswordChecklist password={password} />
        </div>

        <Field
          label={t('auth.phone')}
          optional={t('common.optional')}
          hint={t('auth.phoneHint')}
          error={errors.phoneCode?.message || errors.phoneNumber?.message}
        >
          <PhoneFields
            register={register}
            codeName="phoneCode"
            nationalName="phoneNumber"
            codeError={errors.phoneCode?.message}
            nationalError={errors.phoneNumber?.message}
            nationalPlaceholder={t('auth.phoneNumberPlaceholder')}
          />
        </Field>

        <Button type="submit" size="lg" fullWidth loading={isSubmitting}>
          {t('auth.register')}
          <ArrowRight className="h-4 w-4 rtl:rotate-180" aria-hidden />
        </Button>

        <p className="flex items-start gap-2 text-xs leading-relaxed text-muted-fg">
          <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden />
          <span>
            {t('authShell.termsLead')}{' '}
            <Link to="/terms" className="font-semibold text-primary hover:underline">
              {t('footer.termsLink')}
            </Link>{' '}
            · {' '}
            <Link to="/privacy" className="font-semibold text-primary hover:underline">
              {t('footer.privacyLink')}
            </Link>
          </span>
        </p>
      </form>

      <AuthFootnote>
        {t('auth.haveAccount')}{' '}
        <Link to="/login" className="font-semibold text-primary hover:underline">
          {t('auth.signIn')}
        </Link>
      </AuthFootnote>
    </AuthShell>
  )
}
