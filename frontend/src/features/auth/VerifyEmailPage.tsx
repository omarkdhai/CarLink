import { useEffect, useRef } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, Loader2, MailX } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { verifyEmail } from '@/services/authApi'
import { http } from '@/services/apiClient'
import { useAuth } from '@/features/auth/AuthContext'
import { AuthHeader, AuthShell } from '@/features/auth/AuthShell'
import { buttonClasses } from '@/components/ui/Button'

/**
 * Zero-click route — reads ?token= and verifies on mount. Works signed in or
 * out (owners usually open the email while logged in).
 */
export default function VerifyEmailPage() {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const sent = useRef(false)

  const mutation = useMutation({ mutationFn: verifyEmail })

  useEffect(() => {
    // The token is single-use: never send it twice (StrictMode double-mount).
    if (!token || sent.current) return
    sent.current = true
    mutation.mutate(token)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  const next = isAuthenticated ? '/dashboard' : '/login'

  if (!token || mutation.isError) {
    return (
      <AuthShell>
        <AuthHeader
          icon={MailX}
          tone="bg-destructive-soft text-destructive"
          title={t('authShell.linkInvalidTitle')}
          subtitle={mutation.isError ? http.humanizeError(mutation.error) : t('auth.verifyEmailError')}
        />
        <Link to={next} className={buttonClasses({ size: 'lg', fullWidth: true })}>
          {isAuthenticated ? t('auth.verifyEmailButton') : t('auth.signIn')}
        </Link>
      </AuthShell>
    )
  }

  if (mutation.isSuccess) {
    return (
      <AuthShell>
        <AuthHeader icon={CheckCircle2} tone="bg-success-soft text-success" title={t('auth.verifyEmailSuccess')} subtitle={t('authShell.verifiedText')} />
        <Link to={next} className={buttonClasses({ size: 'lg', fullWidth: true })}>
          {isAuthenticated ? t('auth.verifyEmailButton') : t('auth.signIn')}
        </Link>
      </AuthShell>
    )
  }

  return (
    <AuthShell>
      <div role="status" aria-live="polite" className="flex flex-col items-center py-10 text-center">
        <Loader2 className="h-10 w-10 animate-spin text-primary" aria-hidden />
        <p className="mt-4 text-base font-semibold text-heading">{t('auth.verifyEmailTitle')}</p>
        <p className="mt-1 text-sm text-muted-fg">{t('common.loading')}</p>
      </div>
    </AuthShell>
  )
}
