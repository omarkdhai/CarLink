import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { LayoutGrid, LogIn, Menu, ShoppingBag, X } from 'lucide-react'
import { Logo } from '@/components/layout/Logo'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'
import { PageTransition } from '@/components/shared/PageTransition'
import { buttonClasses } from '@/components/ui/Button'
import { useAuth } from '@/features/auth/AuthContext'
import { cn } from '@/lib/cn'

const NAV_LINKS = [
  { to: '/', key: 'home' },
  { to: '/pricing', key: 'pricing' },
  { to: '/about', key: 'about' },
  { to: '/contact', key: 'contact' },
] as const

const FOOTER_LABEL = 'mb-3 text-xs font-semibold uppercase tracking-[0.12em] text-primary/80 rtl:tracking-normal'
const FOOTER_LINK = 'inline-flex min-h-9 items-center text-sm text-heading transition-colors hover:text-primary'

/** Centered marketing links (desktop). Client-side links with an active state. */
function LandingNav() {
  const { t } = useTranslation()
  return (
    <nav aria-label={t('nav.main')} className="hidden lg:flex absolute left-1/2 -translate-x-1/2 items-center gap-1">
      {NAV_LINKS.map(({ to, key }) => (
        <NavLink
          key={to}
          to={to}
          end
          className={({ isActive }) =>
            cn(
              'h-10 px-3.5 inline-flex items-center rounded-lg text-sm font-semibold transition-colors',
              isActive ? 'text-primary bg-primary-soft' : 'text-muted-fg hover:text-heading hover:bg-muted/60',
            )
          }
        >
          {t(`navbar.${key}`)}
        </NavLink>
      ))}
    </nav>
  )
}

/** Minimal chrome for non-app pages (landing, auth). */
export function PublicLayout({ children }: { children: ReactNode }) {
  const { t } = useTranslation()
  const { isAuthenticated } = useAuth()
  const location = useLocation()
  const mainRef = useRef<HTMLElement>(null)
  const [menuOpen, setMenuOpen] = useState(false)

  useEffect(() => {
    window.scrollTo(0, 0)
    const main = mainRef.current
    if (!main) return
    if (typeof IntersectionObserver === 'undefined') {
      // Ancient browsers: never hide content.
      main.querySelectorAll('section').forEach((s) => s.classList.add('is-revealed'))
      return
    }
    // Observe the rendered page's sections. On client-side navigation
    // (e.g. the mobile drawer <Link>) the layout stays mounted, so re-run on
    // every route change — otherwise the next page's sections stay hidden.
    const reveal = (el: Element) => {
      if (!el.classList.contains('is-revealed')) el.classList.add('is-revealed')
    }
    const io = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            reveal(entry.target)
            io.unobserve(entry.target)
          }
        }
      },
      { threshold: 0.08, rootMargin: '0px 0px -60px 0px' },
    )
    main.querySelectorAll('section').forEach((s) => io.observe(s))
    // Follow DOM mutations too: internal state swaps (e.g. the order stepper
    // mounting step 2's <section> without a route change) would otherwise leave
    // the new section unobserved — and invisible under `opacity: 0`.
    const mo = new MutationObserver((records) => {
      for (const record of records) {
        for (const node of record.addedNodes) {
          if (!(node instanceof Element)) continue
          const sections =
            node.tagName === 'SECTION' ? [node] : Array.from(node.querySelectorAll('section'))
          sections
            .filter((s) => !s.classList.contains('is-revealed'))
            .forEach((s) => io.observe(s))
        }
      }
    })
    mo.observe(main, { childList: true, subtree: true })
    return () => {
      io.disconnect()
      mo.disconnect()
    }
  }, [location.key])

  return (
    <div className="min-h-screen flex flex-col">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:z-[100] focus:m-2 focus:px-4 focus:py-2 focus:bg-primary focus:text-white focus:rounded-lg"
      >
        {t('nav.skipToContent')}
      </a>
      <header className="sticky top-0 z-40 navbar-glass border-b border-border/60">
        <div className="relative mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
          <Logo size="md" />
          <LandingNav />
          <div className="flex items-center gap-2">
            <div className="hidden sm:block">
              <LanguageSwitcher />
            </div>
            <Link
              to={isAuthenticated ? '/dashboard' : '/login'}
              className="hidden lg:inline-flex items-center gap-2 h-10 px-3.5 rounded-xl text-sm font-semibold text-foreground hover:bg-muted/60 hover:text-primary transition-colors"
            >
              {isAuthenticated ? <LayoutGrid className="h-4 w-4" aria-hidden /> : <LogIn className="h-4 w-4" aria-hidden />}
              {isAuthenticated ? t('navbar.mySpace') : t('navbar.login')}
            </Link>
            <Link to="/order" className={cn(buttonClasses({ size: 'sm' }), 'hidden sm:inline-flex h-10')}>
              <ShoppingBag className="h-4 w-4" aria-hidden />
              {t('navbar.order')}
            </Link>
            <button
              type="button"
              className="lg:hidden inline-flex items-center justify-center h-11 w-11 rounded-xl border border-border bg-surface text-foreground hover:border-primary/50 hover:text-primary transition-colors"
              onClick={() => setMenuOpen((o) => !o)}
              aria-label={menuOpen ? t('common.close') : t('navbar.menu')}
              aria-expanded={menuOpen}
              aria-controls="public-menu"
            >
              {menuOpen ? <X className="h-5 w-5" aria-hidden /> : <Menu className="h-5 w-5" aria-hidden />}
            </button>
          </div>
        </div>

        {/* Mobile menu */}
        {menuOpen && (
          <div id="public-menu" className="lg:hidden absolute top-full start-0 end-0 bg-surface border-b border-border/60 shadow-pop px-4 pt-3 pb-5 page-enter">
            <nav aria-label={t('nav.main')} className="flex flex-col gap-1">
              {NAV_LINKS.map(({ to, key }) => (
                <NavLink
                  key={to}
                  to={to}
                  end
                  onClick={() => setMenuOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      'px-3 h-12 flex items-center rounded-lg text-base font-semibold transition-colors',
                      isActive ? 'bg-primary-soft text-primary' : 'text-foreground hover:bg-muted',
                    )
                  }
                >
                  {t(`navbar.${key}`)}
                </NavLink>
              ))}
            </nav>
            <div className="mt-4 grid grid-cols-2 gap-2">
              <Link
                to={isAuthenticated ? '/dashboard' : '/login'}
                onClick={() => setMenuOpen(false)}
                className={buttonClasses({ variant: 'outline' })}
              >
                {isAuthenticated ? t('navbar.mySpace') : t('navbar.login')}
              </Link>
              <Link to="/order" onClick={() => setMenuOpen(false)} className={buttonClasses()}>
                {t('navbar.order')}
              </Link>
            </div>
            <div className="mt-4 flex items-center justify-between gap-3 border-t border-border pt-4 sm:hidden">
              <span className="text-sm text-muted-fg">{t('settings.language')}</span>
              <LanguageSwitcher />
            </div>
          </div>
        )}
      </header>
      <main id="main-content" ref={mainRef} className="reveal-host flex-1 flex flex-col bg-transparent">
        <PageTransition key={location.pathname} className="flex-1 flex flex-col">{children}</PageTransition>
      </main>
      {/* Footer */}
      <footer className="mt-auto border-t border-border bg-surface">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 py-10 sm:py-12">
          <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-[1.3fr_1fr_1fr_1fr]">
            <div>
              <Logo size="md" />
              <p className="mt-3 max-w-[28ch] text-sm leading-relaxed text-muted-fg">{t('landing.footerTagline')}</p>
              <Link to="/order" className={cn(buttonClasses({ size: 'sm' }), 'mt-5')}>
                <ShoppingBag className="h-4 w-4" aria-hidden />
                {t('navbar.order')}
              </Link>
            </div>
            <FooterColumn
              title={t('footer.pagesTitle')}
              links={[
                { to: '/', label: t('navbar.home') },
                { to: '/pricing', label: t('navbar.pricing') },
                { to: '/about', label: t('navbar.about') },
                { to: '/contact', label: t('navbar.contact') },
              ]}
            />
            <FooterColumn
              title={t('footer.accountTitle')}
              links={[
                { to: '/login', label: t('navbar.login') },
                { to: '/register', label: t('auth.createAccount') },
                { to: '/order', label: t('navbar.order') },
              ]}
            />
            <FooterColumn
              title={t('footer.legalTitle')}
              links={[
                { to: '/privacy', label: t('footer.privacyLink') },
                { to: '/terms', label: t('footer.termsLink') },
                { to: '/communication', label: t('footer.commsLink') },
              ]}
            />
          </div>
          <p className="mt-10 border-t border-border pt-6 text-sm text-muted-fg tabular-nums">
            &copy; {new Date().getFullYear()} CarLink. {t('footer.rightsReserved')}
          </p>
        </div>
      </footer>
    </div>
  )
}

function FooterColumn({ title, links }: { title: string; links: { to: string; label: string }[] }) {
  return (
    <div>
      <p className={FOOTER_LABEL}>{title}</p>
      <nav aria-label={title} className="flex flex-col">
        {links.map(({ to, label }) => (
          <Link key={to} to={to} className={FOOTER_LINK}>
            {label}
          </Link>
        ))}
      </nav>
    </div>
  )
}
