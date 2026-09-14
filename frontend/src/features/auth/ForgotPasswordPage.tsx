import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { forgotPasswordSchema, type ForgotPasswordValues } from '@/features/auth/authSchemas'
import { requestPasswordReset } from '@/services/authApi'
import { http } from '@/services/apiClient'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Logo } from '@/components/layout/Logo'
import { ArrowLeft } from 'lucide-react'

export default function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [sent, setSent] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: '' },
  })

  const mutation = useMutation({
    mutationFn: (email: string) => requestPasswordReset(email),
    onSuccess: () => setSent(true),
  })

  function onSubmit(values: ForgotPasswordValues) {
    mutation.mutate(values.email)
  }

  return (
    <div className="w-full max-w-sm mx-auto flex flex-col gap-6 justify-center my-auto py-8">
      <div className="text-center">
        <Logo className="justify-center" />
        <h1 className="text-2xl font-bold text-heading mt-5">{t('auth.forgotTitle')}</h1>
        <p className="text-sm text-muted-fg mt-1">{t('auth.forgotSubtitle')}</p>
      </div>

      {sent ? (
        <Alert tone="success" className="max-w-sm">
          {t('auth.resetLinkSent')}
        </Alert>
      ) : (
        <>
          {mutation.isError && <Alert tone="error">{http.humanizeError(mutation.error)}</Alert>}
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
            <Button type="submit" fullWidth loading={mutation.isPending}>
              {t('auth.sendResetLink')}
            </Button>
          </form>
        </>
      )}

      <Link to="/login" className="inline-flex items-center justify-center gap-1.5 text-sm font-medium text-muted-fg hover:text-primary transition-colors">
        <ArrowLeft className="h-4 w-4" aria-hidden />
        {t('auth.signIn')}
      </Link>
    </div>
  )
}