import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { PasswordInput } from '@/components/ui/PasswordInput'
import { clearSession } from '@/services/tokenStore'
import { userApi } from '@/services/userApi'
import { useTranslation } from 'react-i18next'
import { useMutation } from '@tanstack/react-query'
import { Bell, Check, ChevronRight, Info, KeyRound, Languages, Trash2 } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/Toast'
import { SectionCard } from '@/features/account/OwnerUI'
import { useAuth } from '@/features/auth/AuthContext'
import { authApi } from '@/services/authApi'
import { http } from '@/services/apiClient'
import { cn } from '@/lib/cn'

const languages = [
  { code: 'fr', label: 'Français', region: 'FR' },
  { code: 'en', label: 'English', region: 'EN' },
  { code: 'ar', label: 'العربية', region: 'AR' },
]

export default function SettingsPage() {
  const { t, i18n } = useTranslation()
  const { user } = useAuth()
  const toast = useToast()
  const [resetSent, setResetSent] = useState(false)

  const currentLang = i18n.language?.split('-')[0] || 'fr'

  const changeLanguage = (code: string) => {
    void i18n.changeLanguage(code)
    try {
      localStorage.setItem('carlink.lang', code)
    } catch {
      /* storage unavailable: the choice still applies for this session */
    }
  }

  // Account deletion (security plan S-17): password-confirmed, then the local
  // session is dropped and the owner lands on the sign-in page with a notice.
  const navigate = useNavigate()
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deletePassword, setDeletePassword] = useState('')
  const [deleteError, setDeleteError] = useState<string | null>(null)
  const openDelete = () => {
    setDeletePassword('')
    setDeleteError(null)
    setDeleteOpen(true)
  }
  const deleteAccount = useMutation({
    mutationFn: (password: string) => userApi.deleteAccount(password),
    onSuccess: () => {
      setDeleteOpen(false)
      clearSession()
      navigate('/login', { replace: true, state: { accountDeleted: true } })
    },
    onError: (err) => setDeleteError(http.humanizeError(err)),
  })

  const reset = useMutation({
    mutationFn: () => authApi.requestPasswordReset(user?.email ?? ''),
    onSuccess: () => {
      setResetSent(true)
      toast.show(t('settings.resetSent'))
    },
    onError: (err) => toast.show(http.humanizeError(err), true),
  })

  return (
    <div className="motion-stagger space-y-6 sm:space-y-8 max-w-3xl">
      <PageHeader title={t('settings.title')} subtitle={t('settings.subtitle')} />

      <SettingsSection icon={Languages} tone="bg-primary-soft text-primary" title={t('settings.language')} description={t('settings.languageHint')}>
        <div role="radiogroup" aria-label={t('settings.language')} className="grid gap-3 sm:grid-cols-3">
          {languages.map((lang) => {
            const active = currentLang === lang.code
            return (
              <button
                key={lang.code}
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => changeLanguage(lang.code)}
                className={cn(
                  'flex items-center gap-3 h-14 px-4 rounded-xl border-2 text-start transition-colors',
                  active ? 'border-primary bg-primary-soft/50' : 'border-border hover:border-primary/40',
                )}
              >
                <span
                  className={cn(
                    'h-8 w-8 shrink-0 rounded-lg flex items-center justify-center text-xs font-bold',
                    active ? 'bg-primary text-white' : 'bg-muted text-muted-fg',
                  )}
                  aria-hidden
                >
                  {lang.region}
                </span>
                <span className={cn('flex-1 text-sm font-semibold', active ? 'text-primary' : 'text-foreground')} lang={lang.code}>
                  {lang.label}
                </span>
                {active && <Check className="h-5 w-5 text-primary" aria-hidden />}
              </button>
            )
          })}
        </div>
      </SettingsSection>

      <SettingsSection icon={KeyRound} tone="bg-accent-soft text-accent" title={t('settings.security')} description={t('settings.securityHint')}>
        <div className="flex flex-col sm:flex-row sm:items-center gap-4 rounded-xl border border-border p-4">
          <div className="min-w-0 flex-1">
            <p className="font-semibold text-heading">{t('settings.password')}</p>
            <p className="text-sm text-muted-fg mt-1 leading-relaxed">
              {resetSent ? t('settings.resetSentHint', { email: user?.email }) : t('settings.passwordHint')}
            </p>
          </div>
          <Button variant="outline" loading={reset.isPending} disabled={resetSent || !user?.email} onClick={() => reset.mutate()}>
            {resetSent ? t('settings.resetSentShort') : t('settings.changePassword')}
          </Button>
        </div>
      </SettingsSection>

      <SettingsSection icon={Bell} tone="bg-success-soft text-success" title={t('notifications.title')} description={t('settings.alertsHint')}>
        <Link to="/profile" className="flex items-center gap-3 rounded-xl border border-border p-4 hover:border-primary/40 hover:bg-primary-soft/30 transition-colors">
          <div className="min-w-0 flex-1">
            <p className="font-semibold text-heading">{t('settings.alertsPhone')}</p>
            <p className="text-sm text-muted-fg mt-1 leading-relaxed">{t('settings.alertsPhoneHint')}</p>
          </div>
          <ChevronRight className="h-5 w-5 shrink-0 text-muted-fg rtl:rotate-180" aria-hidden />
        </Link>
      </SettingsSection>

      <SettingsSection icon={Trash2} tone="bg-destructive-soft text-destructive" title={t('settings.dangerZone')} description={t('settings.deleteAccountHint')} danger>
        <div className="flex items-start gap-3 rounded-xl bg-muted/60 p-4 mb-4">
          <Info className="h-4 w-4 text-muted-fg shrink-0 mt-0.5" aria-hidden />
          <p className="text-sm text-muted-fg leading-relaxed">{t('settings.deleteAccountDetails')}</p>
        </div>
        <Button variant="danger" onClick={openDelete}>
          <Trash2 className="h-4 w-4" aria-hidden />
          {t('settings.deleteAccount')}
        </Button>
      </SettingsSection>
      <ConfirmDialog
        open={deleteOpen}
        title={t('settings.deleteAccountConfirmTitle')}
        confirmLabel={t('settings.deleteAccountConfirm')}
        loading={deleteAccount.isPending}
        onCancel={() => setDeleteOpen(false)}
        onConfirm={() => {
          if (!deletePassword) {
            setDeleteError(t('validation.passwordRequired'))
            return
          }
          deleteAccount.mutate(deletePassword)
        }}
        description={
          <span className="block">
            <span className="block">{t('settings.deleteAccountConfirmBody')}</span>
            <span className="mt-4 block">
              <Field label={t('auth.password')} error={deleteError ?? undefined}>
                <PasswordInput
                  autoComplete="current-password"
                  value={deletePassword}
                  onChange={(e) => {
                    setDeletePassword(e.target.value)
                    setDeleteError(null)
                  }}
                />
              </Field>
            </span>
          </span>
        }
      />
      {toast.node}
    </div>
  )
}

function SettingsSection({
  icon: Icon,
  tone,
  title,
  description,
  danger,
  children,
}: {
  icon: typeof Bell
  tone: string
  title: string
  description: string
  danger?: boolean
  children: React.ReactNode
}) {
  return (
    <SectionCard title={title} description={description} icon={Icon} iconTone={tone} className={danger ? 'border-destructive/30' : undefined}>
      {children}
    </SectionCard>
  )
}
