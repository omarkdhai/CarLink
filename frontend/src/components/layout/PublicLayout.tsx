import { useEffect, useRef, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { LogIn } from 'lucide-react'
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
  const mainRef = useRef<HTMLElement>(null)

  useEffect(() => {
    const main = mainRef.current
    if (!main) return
    const sections = Array.from(main.querySelectorAll('section'))
    if (typeof IntersectionObserver === 'undefined') {
      // Ancient browsers: never hide content.
      sections.forEach((s) => s.classList.add('is-revealed'))
      return
    }
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
  }, [])

  return (
    <div className="min-h-screen flex flex-col">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:z-[100] focus:m-2 focus:px-4 focus:py-2 focus:bg-primary focus:text-white focus:rounded-lg"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-40 navbar-glass border-b border-border/60">
        <div className="relative mx-auto flex h-16 max-w-6xl items-center justify-between px-9 sm:px-10">
          <Logo />
          <LandingNav />
          <div className="flex items-center gap-3">
            <LanguageSwitcher />
            <Link
              to="/login"
              className="inline-flex items-center gap-2 h-10 px-3.5 rounded-xl border border-border bg-surface text-sm font-semibold text-foreground hover:border-primary/50 hover:text-primary transition-colors"
            >
              <LogIn className="h-4 w-4" aria-hidden />
              {t('navbar.login')}
            </Link>
          </div>
        </div>
      </header>
      <main id="main-content" ref={mainRef} className="reveal-host flex-1 flex flex-col bg-transparent">
        {children}
      </main>
      <footer className="py-4 text-center text-xs text-muted-fg">
        © {new Date().getFullYear()} CarLink · {t('landing.footerTagline')}
      </footer>
    </div>
  )
}