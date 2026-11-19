import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, BellRing, Check, EyeOff, Lock, Smartphone, Zap } from 'lucide-react'
import { Logo } from '@/components/layout/Logo'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'
import { cn } from '@/lib/cn'
import { PASSWORD_MAX_BYTES, PASSWORD_MIN_LENGTH, isSimplePattern, passwordBytes, passwordLength } from '@/features/auth/passwordRules'

type Variant = 'account' | 'sticker'

/**
 * Split-screen frame for every authentication screen (sign in, sign up,
 * password reset, email verification, sticker activation).
 *
 * Design: UI/UX Pro Max "Trust & Authority" pattern in the app's Swiss
 * minimal style — a blue brand panel (desktop) that states the privacy
 * promise, and a calm form column. Existing colour tokens and fonts only.
 */
export function AuthShell({ children, variant = 'account' }: { children: ReactNode; variant?: Variant }) {
  const { t } = useTranslation()
  return (
    <div className="min-h-dvh bg-surface lg:grid lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <BrandPanel variant={variant} />

      <div className="flex min-h-dvh flex-col">
        {/* Top bar */}
        <header className="flex h-16 items-center justify-between gap-3 px-4 sm:px-8">
          <div className="lg:hidden">
            <Logo size="sm" />
          </div>
          <Link
            to="/"
            className="hidden lg:inline-flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm font-medium text-muted-fg transition-colors hover:text-primary"
          >
            <ArrowLeft className="h-4 w-4 rtl:rotate-180" aria-hidden />
            {t('authShell.backHome')}
          </Link>
          <LanguageSwitcher />
        </header>

        {/* Form column */}
        <main id="main-content" className="flex flex-1 items-center justify-center px-4 pb-10 pt-4 sm:px-8">
          <div className="auth-rise w-full max-w-[26rem]">{children}</div>
        </main>

        <footer className="flex flex-wrap items-center justify-center gap-x-5 gap-y-1 px-4 pb-6 text-xs text-muted-fg">
          <Link to="/privacy" className="hover:text-primary">
            {t('footer.privacyLink')}
          </Link>
          <Link to="/terms" className="hover:text-primary">
            {t('footer.termsLink')}
          </Link>
          <Link to="/contact" className="hover:text-primary">
            {t('navbar.contact')}
          </Link>
        </footer>
      </div>
    </div>
  )
}

function BrandPanel({ variant }: { variant: Variant }) {
  const { t } = useTranslation()
  const points = [
    { icon: EyeOff, key: 'point1' },
    { icon: Zap, key: 'point2' },
    { icon: Smartphone, key: 'point3' },
  ]
  return (
    <aside className="relative hidden overflow-hidden bg-primary text-white lg:flex lg:flex-col lg:justify-between lg:gap-14 lg:p-12 xl:p-14">
      {/* Background texture */}
      <div className="auth-grid pointer-events-none absolute inset-0" aria-hidden />
      <div className="pointer-events-none absolute -end-24 -top-24 h-80 w-80 rounded-full bg-white/10 blur-2xl" aria-hidden />
      <div className="pointer-events-none absolute -bottom-32 -start-16 h-96 w-96 rounded-full bg-primary-active/60 blur-3xl" aria-hidden />

      <div className="relative">
        <span className="inline-flex items-center justify-center rounded-2xl bg-white px-5 py-3 shadow-lg [&>a]:flex [&>a]:items-center">
          <Logo size="md" className="items-center justify-center" />
        </span>
      </div>

      <div className="relative max-w-md">
        <h2 className="text-4xl font-black leading-[1.08] tracking-[-0.03em] [text-wrap:balance] xl:text-[2.75rem]">
          {t(`authShell.${variant}Title`)}
        </h2>
        <p className="mt-4 text-lg leading-relaxed text-white/80">{t(`authShell.${variant}Text`)}</p>

        <ul className="mt-8 space-y-4">
          {points.map(({ icon: Icon, key }, i) => (
            <li key={key} className="auth-pop flex items-center gap-3" style={{ animationDelay: `${200 + i * 120}ms` }}>
              <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-white/15 ring-1 ring-white/20" aria-hidden>
                <Icon className="h-5 w-5" />
              </span>
              <span className="text-base font-medium text-white/90">{t(`authShell.${key}`)}</span>
            </li>
          ))}
        </ul>
      </div>

      {/* Product moment: the alert an owner receives */}
      <div className="relative pt-6">
        <div className="auth-pop" style={{ animationDelay: '650ms' }}>
          <div className="auth-float w-full max-w-sm rounded-2xl bg-white p-4 text-heading shadow-[0_24px_60px_rgba(15,23,42,0.35)]">
            <div className="flex items-start gap-3">
              <span className="auth-pulse grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-accent text-white" aria-hidden>
                <BellRing className="h-5 w-5" />
              </span>
              <div className="min-w-0 flex-1">
                <div className="flex items-center justify-between gap-2">
                  <p className="text-sm font-bold">CarLink</p>
                  <span className="text-xs text-muted-fg">{t('authShell.alertTime')}</span>
                </div>
                <p className="mt-1 text-sm leading-snug text-foreground">{t('authShell.alertText')}</p>
                <p className="mt-2 inline-flex items-center gap-1.5 text-xs font-medium text-success">
                  <Lock className="h-3.5 w-3.5" aria-hidden />
                  {t('authShell.alertPrivate')}
                </p>
              </div>
            </div>
          </div>
        </div>
        <p className="mt-8 text-sm text-white/60">&copy; {new Date().getFullYear()} CarLink</p>
      </div>
    </aside>
  )
}

/** Title block at the top of every auth form: optional icon tile, H1, lead. */
export function AuthHeader({
  icon: Icon,
  tone = 'bg-primary-soft text-primary',
  title,
  subtitle,
}: {
  icon?: typeof Lock
  tone?: string
  title: string
  subtitle?: ReactNode
}) {
  return (
    <div className="mb-8">
      {Icon && (
        <span className={cn('mb-5 grid h-12 w-12 place-items-center rounded-2xl', tone)} aria-hidden>
          <Icon className="h-6 w-6" />
        </span>
      )}
      <h1 className="text-3xl font-bold leading-tight tracking-[-0.02em] text-heading sm:text-[2rem]">{title}</h1>
      {subtitle && <p className="mt-2 text-base leading-relaxed text-muted-fg">{subtitle}</p>}
    </div>
  )
}

/** Small centred line under a form ("No account yet? Create one"). */
export function AuthFootnote({ children }: { children: ReactNode }) {
  return <p className="mt-8 text-center text-sm text-muted-fg">{children}</p>
}

/** Live checklist for the password rule (security plan S-14): length, not a simple pattern. */
export function PasswordChecklist({ password }: { password: string }) {
  const { t } = useTranslation()
  const checks = [
    { key: 'length', ok: passwordLength(password) >= PASSWORD_MIN_LENGTH && passwordBytes(password) <= PASSWORD_MAX_BYTES },
    { key: 'pattern', ok: password.length > 0 && !isSimplePattern(password) },
  ]
  return (
    <ul className="mt-3 grid gap-1.5 sm:grid-cols-2" aria-label={t('authShell.pwTitle')}>
      {checks.map((c) => (
        <li
          key={c.key}
          className={cn('flex items-center gap-1.5 text-xs font-medium transition-colors', c.ok ? 'text-success' : 'text-muted-fg')}
        >
          <span
            className={cn(
              'grid h-4 w-4 shrink-0 place-items-center rounded-full transition-colors',
              c.ok ? 'bg-success text-white' : 'border border-border',
            )}
            aria-hidden
          >
            {c.ok && <Check className="h-3 w-3" />}
          </span>
          {t(`authShell.pw.${c.key}`)}
          <span className="sr-only">{c.ok ? t('authShell.pwOk') : t('authShell.pwMissing')}</span>
        </li>
      ))}
    </ul>
  )
}
