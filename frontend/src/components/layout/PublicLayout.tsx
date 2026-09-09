import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Logo } from '@/components/layout/Logo'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'

/** Centered anchor links for the public marketing navbar. */
function LandingNav() {
  const { t } = useTranslation()
  return (
    <nav
      aria-label="Main"
      className="hidden md:flex absolute left-1/2 -translate-x-1/2 items-center gap-6"
    >
      <a
        href="/"
        className="text-sm font-medium text-muted-fg hover:text-primary transition-colors"
      >
        {t('navbar.home')}
      </a>
    </nav>
  )
}

/** Minimal chrome for non-app pages (landing, auth). */
export function PublicLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:z-[100] focus:m-2 focus:px-4 focus:py-2 focus:bg-primary focus:text-white focus:rounded-lg"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-40 border-b border-border bg-surface/80 backdrop-blur">
        <div className="relative mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <Logo />
          <LandingNav />
          <LanguageSwitcher />
        </div>
      </header>
      <main id="main-content" className="flex-1 flex flex-col px-4 py-8">
        {children}
      </main>
      <footer className="py-4 text-center text-xs text-muted-fg">
        © {new Date().getFullYear()} CarLink · {t('landing.footerTagline')}
      </footer>
    </div>
  )
}