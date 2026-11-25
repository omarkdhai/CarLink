import { useEffect, useRef, type RefObject } from 'react'
import { Link, NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom'
import {
  LayoutGrid,
  Car,
  MessageSquareText,
  Bell,
  UserCircle,
  Settings,
  ShieldCheck,
  QrCode,
  LogOut,
  House,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/AuthContext'
import { useAccountStatus } from '@/features/account/useAccountStatus'
import { Logo } from '@/components/layout/Logo'
import { cn } from '@/lib/cn'
import { canAnimate, gsap, useGSAP } from '@/lib/gsap'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'
import { PageTransition } from '@/components/shared/PageTransition'

type NavItem = { to: string; key: string; icon: typeof Car; badge?: 'messages' | 'notifications' }

const ownerNav: NavItem[] = [
  { to: '/dashboard', key: 'nav.dashboard', icon: LayoutGrid },
  { to: '/stickers', key: 'nav.stickers', icon: QrCode },
  { to: '/vehicles', key: 'nav.vehicles', icon: Car },
  { to: '/messages', key: 'nav.messages', icon: MessageSquareText, badge: 'messages' },
  { to: '/notifications', key: 'nav.notifications', icon: Bell, badge: 'notifications' },
]

const accountNav: NavItem[] = [
  { to: '/profile', key: 'nav.profile', icon: UserCircle },
  { to: '/settings', key: 'nav.settings', icon: Settings },
]

/** Phone tab bar: five top-level destinations, always labelled. */
const tabNav: (NavItem & { short: string; match?: string[] })[] = [
  { to: '/dashboard', key: 'nav.dashboard', short: 'nav.home', icon: LayoutGrid },
  { to: '/stickers', key: 'nav.stickers', short: 'nav.stickersShort', icon: QrCode },
  { to: '/vehicles', key: 'nav.vehicles', short: 'nav.vehiclesShort', icon: Car },
  { to: '/messages', key: 'nav.messages', short: 'nav.messages', icon: MessageSquareText, badge: 'messages' },
  { to: '/profile', key: 'nav.account', short: 'nav.account', icon: UserCircle, match: ['/profile', '/settings'] },
]

function CountBadge({ count, label, className }: { count: number; label: string; className?: string }) {
  if (count <= 0) return null
  return (
    <span
      aria-label={label}
      className={cn(
        'min-w-5 h-5 px-1.5 rounded-full bg-accent text-white text-[11px] font-bold leading-5 text-center tabular-nums',
        className,
      )}
    >
      {count > 99 ? '99+' : count}
    </span>
  )
}

function initials(first?: string, last?: string) {
  return `${first?.[0] ?? ''}${last?.[0] ?? ''}`.toUpperCase() || '?'
}

/** Instant moves for reduced-motion users and tests; eased glide otherwise. */
const glide = () =>
  canAnimate() && window.matchMedia('(prefers-reduced-motion: no-preference)').matches ? 0.45 : 0

/**
 * Slides the sidebar's active hairline to the link marked aria-current="page"
 * inside the sidebar (NavLink sets it). Hidden when no link is active.
 */
function useSideIndicator(
  containerRef: RefObject<HTMLDivElement | null>,
  indicatorRef: RefObject<HTMLSpanElement | null>,
  key: string,
) {
  useGSAP(
    () => {
      const box = containerRef.current
      const bar = indicatorRef.current
      if (!box || !bar) return
      const active = box.querySelector<HTMLElement>('a[aria-current="page"]')
      if (!active) {
        gsap.to(bar, { autoAlpha: 0, duration: glide() * 0.5 })
        return
      }
      const firstPlacement = gsap.getProperty(bar, 'opacity') === 0
      gsap.to(bar, {
        y: active.offsetTop + 8,
        height: active.offsetHeight - 16,
        autoAlpha: 1,
        duration: firstPlacement ? 0 : glide(),
        ease: 'power3.out',
        overwrite: true,
      })
    },
    { dependencies: [key] },
  )
}

export function AppLayout() {
  const { t } = useTranslation()
  const { isAdmin, signOut, user } = useAuth()
  const status = useAccountStatus()
  const navigate = useNavigate()
  const location = useLocation()
  const sideRef = useRef<HTMLDivElement>(null)
  const sideBarRef = useRef<HTMLSpanElement>(null)
  const tabBarRef = useRef<HTMLSpanElement>(null)
  useSideIndicator(sideRef, sideBarRef, location.pathname)

  const isTabActive = ({ to, match }: (typeof tabNav)[number]) =>
    match ? match.some((m) => location.pathname.startsWith(m)) : location.pathname === to || location.pathname.startsWith(`${to}/`)
  const activeTab = tabNav.findIndex(isTabActive)

  // Phone tab bar: the pill above the active tab glides between tabs.
  useGSAP(
    () => {
      const pill = tabBarRef.current
      if (!pill) return
      if (activeTab < 0) {
        gsap.to(pill, { autoAlpha: 0, duration: glide() * 0.5 })
        return
      }
      const rtl = document.documentElement.dir === 'rtl'
      const firstPlacement = gsap.getProperty(pill, 'opacity') === 0
      gsap.to(pill, {
        xPercent: (rtl ? -100 : 100) * activeTab,
        autoAlpha: 1,
        duration: firstPlacement ? 0 : glide(),
        ease: 'power3.out',
        overwrite: true,
      })
    },
    { dependencies: [activeTab] },
  )

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [location.key])

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  const counts = { messages: status.unread, notifications: status.unread + status.notices.length }
  const badgeLabel = (n: number) => t('nav.badge', { count: n })

  const sideLink = (item: NavItem, tone: 'primary' | 'accent' = 'primary') => {
    const Icon = item.icon
    const count = item.badge ? counts[item.badge] : 0
    return (
      <NavLink
        key={item.to}
        to={item.to}
        end={item.to === '/dashboard'}
        className={({ isActive }) =>
          cn(
            'flex items-center gap-3 h-11 px-3 rounded-lg text-sm font-medium transition-colors',
            isActive
              ? tone === 'primary'
                ? 'bg-primary-soft text-primary'
                : 'bg-accent-soft text-accent'
              : 'text-muted-fg hover:bg-muted hover:text-foreground',
          )
        }
      >
        <Icon className="h-5 w-5 shrink-0" aria-hidden />
        <span className="flex-1 truncate">{t(item.key)}</span>
        <CountBadge count={count} label={badgeLabel(count)} />
      </NavLink>
    )
  }

  return (
    <div className="min-h-screen">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:z-[100] focus:m-2 focus:px-4 focus:py-2 focus:bg-primary focus:text-white focus:rounded-lg">
        {t('nav.skipToContent')}
      </a>

      {/* Desktop sidebar */}
      <aside className="hidden lg:flex fixed inset-y-0 start-0 w-64 flex-col border-e border-border bg-surface/80 backdrop-blur">
        <div className="h-16 px-5 flex items-center border-b border-border/60">
          <Logo size="md" />
        </div>
        <div ref={sideRef} className="relative flex-1 overflow-y-auto px-3 py-5 space-y-6">
          <span
            ref={sideBarRef}
            className="pointer-events-none absolute start-0 top-0 !mt-0 h-0 w-[3px] rounded-e-full bg-primary opacity-0"
            aria-hidden
          />
          <nav aria-label={t('nav.main')} className="space-y-1">
            {ownerNav.map((item) => sideLink(item))}
          </nav>
          {isAdmin && (
            <div>
              <p className="px-3 mb-2 text-xs font-semibold uppercase tracking-wide text-muted-fg/80">{t('nav.admin')}</p>
              <nav className="space-y-1" aria-label={t('nav.admin')}>
                {sideLink({ to: '/admin', key: 'nav.adminDashboard', icon: ShieldCheck }, 'accent')}
              </nav>
            </div>
          )}
          <div>
            <p className="px-3 mb-2 text-xs font-semibold uppercase tracking-wide text-muted-fg/80">{t('nav.account')}</p>
            <nav aria-label={t('nav.account')} className="space-y-1">
              {accountNav.map((item) => sideLink(item))}
            </nav>
          </div>
          {/* Back to the public site (the landing page stays open while signed in) */}
          <Link
            to="/"
            className="flex items-center gap-3 h-11 px-3 rounded-lg text-sm font-medium text-muted-fg hover:bg-muted hover:text-foreground transition-colors"
          >
            <House className="h-5 w-5 shrink-0" aria-hidden />
            <span className="flex-1 truncate">{t('nav.backToSite')}</span>
          </Link>
        </div>
        {/* Signed-in user */}
        <div className="p-3 border-t border-border">
          <div className="flex items-center gap-3 rounded-xl p-2">
            <Link
              to="/profile"
              className="h-10 w-10 shrink-0 rounded-full bg-primary-soft text-primary flex items-center justify-center text-sm font-bold"
              aria-label={t('nav.profile')}
            >
              {initials(user?.firstName, user?.lastName)}
            </Link>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold text-heading truncate">
                {user?.firstName} {user?.lastName}
              </p>
              <p className="text-xs text-muted-fg truncate">{user?.email}</p>
            </div>
            <button
              onClick={handleSignOut}
              aria-label={t('auth.logout')}
              title={t('auth.logout')}
              className="h-10 w-10 shrink-0 rounded-lg flex items-center justify-center text-muted-fg hover:bg-destructive-soft hover:text-destructive transition-colors"
            >
              <LogOut className="h-5 w-5" aria-hidden />
            </button>
          </div>
        </div>
      </aside>

      {/* Main column */}
      <div className="lg:ps-64">
        <header className="sticky top-0 z-40 h-14 lg:h-16 flex items-center justify-between gap-3 px-4 sm:px-6 lg:px-8 border-b border-border/60 navbar-glass">
          <div className="lg:hidden">
            <Logo size="sm" />
          </div>
          <div className="hidden lg:block" />
          <div className="flex items-center gap-2">
            <div className="hidden sm:block">
              <LanguageSwitcher />
            </div>
            <Link
              to="/"
              aria-label={t('nav.backToSite')}
              title={t('nav.backToSite')}
              className="lg:hidden h-10 w-10 rounded-xl flex items-center justify-center text-muted-fg hover:bg-muted hover:text-foreground transition-colors"
            >
              <House className="h-5 w-5" aria-hidden />
            </Link>
            <NavLink
              to="/notifications"
              aria-label={`${t('nav.notifications')}${counts.notifications ? ` — ${badgeLabel(counts.notifications)}` : ''}`}
              className={({ isActive }) =>
                cn(
                  'relative h-10 w-10 rounded-xl flex items-center justify-center transition-colors',
                  isActive ? 'bg-primary-soft text-primary' : 'text-muted-fg hover:bg-muted hover:text-foreground',
                )
              }
            >
              <Bell className="h-5 w-5" aria-hidden />
              {counts.notifications > 0 && (
                <span className="absolute top-1.5 end-1.5 h-2.5 w-2.5 rounded-full bg-accent ring-2 ring-surface" aria-hidden />
              )}
            </NavLink>
            <Link
              to="/profile"
              aria-label={t('nav.profile')}
              className="lg:hidden h-10 w-10 rounded-full bg-primary-soft text-primary flex items-center justify-center text-sm font-bold"
            >
              {initials(user?.firstName, user?.lastName)}
            </Link>
          </div>
        </header>

        <main id="main-content" className="px-4 sm:px-6 lg:px-8 pt-6 sm:pt-8 pb-28 lg:pb-12 max-w-6xl mx-auto">
          <PageTransition key={location.pathname}>
            <Outlet />
          </PageTransition>
        </main>
      </div>

      {/* Phone tab bar */}
      <nav
        aria-label={t('nav.main')}
        className="lg:hidden fixed bottom-0 inset-x-0 z-40 border-t border-border bg-surface/95 backdrop-blur pb-[env(safe-area-inset-bottom)]"
      >
        <span
          ref={tabBarRef}
          className="pointer-events-none absolute start-0 top-0 flex w-1/5 justify-center opacity-0"
          aria-hidden
        >
          <span className="h-[3px] w-8 rounded-b-full bg-primary" />
        </span>
        <ul className="grid grid-cols-5 h-16">
          {tabNav.map((tab) => {
            const { to, short, icon: Icon, badge } = tab
            const active = isTabActive(tab)
            const count = badge ? counts[badge] : 0
            return (
              <li key={to}>
                <Link
                  to={to}
                  aria-current={active ? 'page' : undefined}
                  className={cn(
                    'h-full flex flex-col items-center justify-center gap-1 text-[11px] font-semibold transition-colors',
                    active ? 'text-primary' : 'text-muted-fg hover:text-foreground',
                  )}
                >
                  <span className="tab-pop relative">
                    <Icon className="h-6 w-6" aria-hidden />
                    <CountBadge count={count} label={badgeLabel(count)} className="absolute -top-1.5 -end-2.5 ring-2 ring-surface" />
                  </span>
                  <span className="truncate max-w-full px-1">{t(short)}</span>
                </Link>
              </li>
            )
          })}
        </ul>
      </nav>
    </div>
  )
}
