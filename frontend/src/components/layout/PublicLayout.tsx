import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { LogIn, Menu, X } from 'lucide-react'
import { Logo } from '@/components/layout/Logo'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'

const NAV_LINKS = [
  { to: '/', key: 'home' },
  { to: '/pricing', key: 'pricing' },
  { to: '/about', key: 'about' },
  { to: '/contact', key: 'contact' },
] as const

/** Centered anchor links for the public marketing navbar (desktop only). */
function LandingNav() {
  const { t } = useTranslation()
  return (
    <nav
      aria-label="Main"
      className="hidden lg:flex absolute left-1/2 -translate-x-1/2 items-center gap-6"
    >
      <a
        href="/"
        className="text-sm font-medium text-muted-fg hover:text-primary transition-colors"
      >
        {t('navbar.home')}
      </a>
      <a
        href="/pricing"
        className="text-sm font-medium text-muted-fg hover:text-primary transition-colors"
      >
        {t('navbar.pricing')}
      </a>
      <a
        href="/about"
        className="text-sm font-medium text-muted-fg hover:text-primary transition-colors"
      >
        {t('navbar.about')}
      </a>
      <a
        href="/contact"
        className="text-sm font-medium text-muted-fg hover:text-primary transition-colors"
      >
        {t('navbar.contact')}
      </a>
    </nav>
  )
}

/** Minimal chrome for non-app pages (landing, auth). */
export function PublicLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const location = useLocation()
  const mainRef = useRef<HTMLElement>(null)
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => {
    window.scrollTo(0, 0)
    const main = mainRef.current
    if (!main) return
    const sections = Array.from(main.querySelectorAll('section'))
    if (typeof IntersectionObserver === 'undefined') {
      // Ancient browsers: never hide content.
      sections.forEach((s) => s.classList.add('is-revealed'))
      return
    }
    // Observe the newly rendered page's sections. On client-side navigation
    // (e.g. the mobile drawer <Link>) the layout stays mounted, so re-run on
    // every route change — otherwise the next page's sections stay hidden.
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            entry.target.classList.add('is-revealed')
            io.unobserve(entry.target)
          }
        }
      },
      { threshold: 0.08, rootMargin: '0px 0px -60px 0px' },
    )
    sections.forEach((s) => io.observe(s))
    return () => io.disconnect()
  }, [location.key])

  return (
    <div className="min-h-screen flex flex-col">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:z-[100] focus:m-2 focus:px-4 focus:py-2 focus:bg-primary focus:text-white focus:rounded-lg"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-40 navbar-glass border-b border-border/60">
        <div className="relative mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-9">
          <Logo />
          <LandingNav />
          <div className="flex items-center gap-2 sm:gap-3">
            <LanguageSwitcher />
            <Link
              to="/login"
              className="hidden lg:inline-flex items-center gap-2 h-10 px-3.5 rounded-xl border border-border bg-surface text-sm font-semibold text-foreground hover:border-primary/50 hover:text-primary transition-colors"
            >
              <LogIn className="h-4 w-4" aria-hidden />
              {t('navbar.login')}
            </Link>
            <button
              type="button"
              className="lg:hidden inline-flex items-center justify-center h-10 w-10 rounded-xl border border-border bg-surface text-foreground hover:border-primary/50 hover:text-primary transition-colors"
              onClick={() => setMenuOpen((o) => !o)}
              aria-label={menuOpen ? t('common.close') : 'Menu'}
              aria-expanded={menuOpen}
              aria-haspopup="menu"
            >
              {menuOpen ? <X className="h-5 w-5" aria-hidden /> : <Menu className="h-5 w-5" aria-hidden />}
            </button>
          </div>
        </div>

        {/* Mobile drawer */}
        {menuOpen && (
          <div className="lg:hidden absolute top-full start-0 end-0 navbar-glass border-b border-border/60 shadow-pop px-4 py-3 pb-4">
            <nav aria-label="Main" className="flex flex-col gap-1">
              {NAV_LINKS.map(({ to, key }) => (
                <Link
                  key={to}
                  to={to}
                  onClick={() => setMenuOpen(false)}
                  className="px-3 h-11 flex items-center rounded-lg text-sm font-medium text-muted-fg hover:bg-muted hover:text-foreground transition-colors"
                >
                  {t(`navbar.${key}`)}
                </Link>
              ))}
            </nav>
            <Link
              to="/login"
              onClick={() => setMenuOpen(false)}
              className="mt-2 flex items-center justify-center gap-2 w-full h-11 rounded-xl border border-border bg-surface text-sm font-semibold text-foreground hover:border-primary/50 hover:text-primary transition-colors"
            >
              <LogIn className="h-4 w-4" aria-hidden />
              {t('navbar.login')}
            </Link>
          </div>
        )}
      </header>
      <main id="main-content" ref={mainRef} className="reveal-host flex-1 flex flex-col bg-transparent">
        {children}
      </main>
      <footer className="border-t border-border bg-white/40">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-12">
          <div className="grid gap-8 sm:grid-cols-2 lg:grid-cols-4">
            {/* Brand column */}
            <div>
              <Logo link={false} />
              <p className="text-sm text-muted-fg mt-3 leading-relaxed max-w-xs">
                {t('landing.footerTagline')}
              </p>
            </div>

            {/* Pages column */}
            <div>
              <h4 className="font-semibold text-heading text-sm mb-3">{t('footer.pagesTitle')}</h4>
              <nav className="space-y-2">
                {[
                  { to: '/', label: t('navbar.home') },
                  { to: '/pricing', label: t('navbar.pricing') },
                  { to: '/about', label: t('navbar.about') },
                  { to: '/contact', label: t('navbar.contact') },
                ].map(({ to, label }) => (
                  <Link
                    key={to}
                    to={to}
                    className="block text-sm text-muted-fg hover:text-primary transition-colors"
                  >
                    {label}
                  </Link>
                ))}
              </nav>
            </div>

            {/* Legal column */}
            <div>
              <h4 className="font-semibold text-heading text-sm mb-3">{t('footer.legalTitle')}</h4>
              <nav className="space-y-2">
                {[
                  { to: '/privacy', label: t('footer.privacyLink') },
                  { to: '/terms', label: t('footer.termsLink') },
                  { to: '/communication', label: t('footer.commsLink') },
                ].map(({ to, label }) => (
                  <Link
                    key={to}
                    to={to}
                    className="block text-sm text-muted-fg hover:text-primary transition-colors"
                  >
                    {label}
                  </Link>
                ))}
              </nav>
            </div>

            {/* Account column */}
            <div>
              <h4 className="font-semibold text-heading text-sm mb-3">{t('footer.accountTitle')}</h4>
              <div className="flex flex-col gap-2">
                <Link
                  to="/login"
                  className="inline-flex items-center justify-center h-10 px-4 rounded-xl border border-border bg-surface text-sm font-semibold text-foreground hover:border-primary/50 hover:text-primary transition-colors"
                >
                  {t('navbar.login')}
                </Link>
                <Link
                  to="/register"
                  className="inline-flex items-center justify-center h-10 px-4 rounded-xl bg-primary text-sm font-semibold text-white hover:bg-primary/90 transition-colors"
                >
                  {t('auth.createAccount')}
                </Link>
              </div>
            </div>
          </div>

          {/* Bottom bar */}
          <div className="mt-4 pt-4 border-t border-border flex flex-col sm:flex-row items-center justify-between gap-4 text-xs text-muted-fg">
            <p>&copy; {new Date().getFullYear()} CarLink. {t('footer.rightsReserved')}</p>
            
          </div>
        </div>
      </footer>
    </div>
  )
}