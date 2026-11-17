import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { ArrowLeft, KeyRound, MailCheck } from 'lucide-react'
import { forgotPasswordSchema, type ForgotPasswordValues } from '@/features/auth/authSchemas'
import { AuthFootnote, AuthHeader, AuthShell } from '@/features/auth/AuthShell'
import { requestPasswordReset } from '@/services/authApi'
import { http } from '@/services/apiClient'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'

export default function ForgotPasswordPage() {
  const { t } = useTranslation()
  const [sentTo, setSentTo] = useState<string | null>(null)

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
    onSuccess: (_d, email) => setSentTo(email),
  })

  const backToLogin = (
    <AuthFootnote>
      <Link to="/login" className="inline-flex items-center gap-1.5 font-semibold text-primary hover:underline">
        <ArrowLeft className="h-4 w-4 rtl:rotate-180" aria-hidden />
        {t('authShell.backToLogin')}
      </Link>
    </AuthFootnote>
  )

  if (sentTo) {
    return (
      <AuthShell>
        <AuthHeader
          icon={MailCheck}
          tone="bg-success-soft text-success"
          title={t('authShell.checkInbox')}
          subtitle={t('authShell.checkInboxText', { email: sentTo })}
        />
        <div className="rounded-2xl bg-muted/50 p-4 text-sm leading-relaxed text-muted-fg">{t('authShell.checkSpam')}</div>
        <Button variant="outline" size="lg" fullWidth className="mt-5" loading={mutation.isPending} onClick={() => mutation.mutate(sentTo)}>
          {t('authShell.resend')}
        </Button>
        {backToLogin}
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <AuthHeader icon={KeyRound} title={t('auth.forgotTitle')} subtitle={t('auth.forgotSubtitle')} />
      <form onSubmit={handleSubmit((v) => mutation.mutate(v.email))} className="space-y-5" noValidate>
        {mutation.isError && (
          <div role="alert">
            <Alert tone="error">{http.humanizeError(mutation.error)}</Alert>
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
        <Button type="submit" size="lg" fullWidth loading={mutation.isPending}>
          {t('auth.sendResetLink')}
        </Button>
      </form>
      {backToLogin}
    </AuthShell>
  )
}
