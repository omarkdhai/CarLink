import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, AlertCircle, Loader2 } from 'lucide-react'
import { useMutation } from '@tanstack/react-query'
import { verifyEmail } from '@/services/authApi'
import { buttonClasses } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { Logo } from '@/components/layout/Logo'

type State = 'pending' | 'success' | 'error'

/** Zero-click route — reads ?token= and verifies on mount. */
export default function VerifyEmailPage() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [state, setState] = useState<State>('pending')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: verifyEmail,
    onSuccess: () => setState('success'),
  })

  useEffect(() => {
    if (!token) {
      setState('error')
      setErrorMsg(t('auth.verifyEmailError'))
      return
    }
    mutation.mutate(token)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token])

  return (
    <div className="w-full max-w-sm mx-auto flex flex-col gap-6 justify-center my-auto py-8 text-center">
      <Logo className="justify-center" />
      <div className="bg-surface border border-border rounded-xl p-8 shadow-card flex flex-col items-center gap-4">
        {state === 'pending' && (
          <>
            <Loader2 className="h-10 w-10 animate-spin text-primary" aria-hidden />
            <p className="text-sm text-muted-fg">{t('common.loading')}</p>
          </>
        )}
        {state === 'success' && (
          <>
            <CheckCircle2 className="h-10 w-10 text-success" aria-hidden />
            <h1 className="text-xl font-bold text-heading">{t('auth.verifyEmailSuccess')}</h1>
            <Link to="/dashboard" className={buttonClasses()}>
              {t('auth.verifyEmailButton')}
            </Link>
          </>
        )}
        {state === 'error' && (
          <>
            <AlertCircle className="h-10 w-10 text-destructive" aria-hidden />
            <Alert tone="error">{errorMsg ?? t('auth.verifyEmailError')}</Alert>
          </>
        )}
      </div>
    </div>
  )
}