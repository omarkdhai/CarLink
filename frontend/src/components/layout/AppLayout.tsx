import { useState, useEffect } from 'react'
import { NavLink, Outlet, useNavigate, useLocation } from 'react-router-dom'
import {
  LayoutGrid,
  Car,
  MessageSquareText,
  Bell,
  UserCircle,
  Settings,
  ShieldCheck,
  QrCode,
  Menu,
  X,
  LogOut,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/AuthContext'
import { Logo } from '@/components/layout/Logo'
import { cn } from '@/lib/cn'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'
import { PageTransition } from '@/components/shared/PageTransition'

const ownerNav = [
  { to: '/dashboard', key: 'nav.dashboard', icon: LayoutGrid },
  { to: '/stickers', key: 'nav.stickers', icon: QrCode },
  { to: '/vehicles', key: 'nav.vehicles', icon: Car },
  { to: '/messages', key: 'nav.messages', icon: MessageSquareText },
  { to: '/notifications', key: 'nav.notifications', icon: Bell },
]

const accountNav = [
  { to: '/profile', key: 'nav.profile', icon: UserCircle },
  { to: '/settings', key: 'nav.settings', icon: Settings },
]

function SidebarContent({ isAdmin, onNavigate }: { isAdmin: boolean; onNavigate?: () => void }) {
  const { t } = useTranslation()
  const Section = ({ items, label }: { items: typeof ownerNav; label?: string }) => (
    <nav aria-label={label} className="space-y-1">
      {items.map(({ to, key, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          onClick={onNavigate}
          end={to === '/dashboard'}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 h-11 px-3 rounded-lg text-sm font-medium transition-colors',
              isActive
                ? 'bg-primary-soft text-primary'
                : 'text-muted-fg hover:bg-muted hover:text-foreground',
            )
          }
        >
          <Icon className="h-5 w-5 shrink-0" aria-hidden />
          {t(key)}
        </NavLink>
      ))}
    </nav>
  )

  return (
    <div className="flex flex-col h-full">
      <div className="px-5 pt-5 pb-4">
        <Logo />
      </div>
      <div className="flex-1 overflow-y-auto px-3 space-y-6 py-2">
        <Section items={ownerNav} label="Main" />
        {isAdmin && (
          <div>
            <p className="px-3 mb-1 text-xs font-semibold uppercase tracking-wide text-muted-fg/70">
              {t('nav.admin')}
            </p>
            <nav className="space-y-1" aria-label={t('nav.admin')}>
              <NavLink
                to="/admin"
                onClick={onNavigate}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 h-11 px-3 rounded-lg text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-accent-soft text-accent'
                      : 'text-muted-fg hover:bg-muted hover:text-foreground',
                  )
                }
              >
                <ShieldCheck className="h-5 w-5 shrink-0" aria-hidden />
                {t('nav.adminDashboard')}
              </NavLink>
            </nav>
          </div>
        )}
        <Section items={accountNav} label={t('nav.profile')} />
      </div>
    </div>
  )
}

export function AppLayout() {
  const { t } = useTranslation()
  const { isAdmin, signOut } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)

  useEffect(() => {
    window.scrollTo(0, 0)
  }, [location.key])

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:z-[100] focus:m-2 focus:px-4 focus:py-2 focus:bg-primary focus:text-white focus:rounded-lg">
        Skip to content
      </a>
      {/* Desktop sidebar */}
      <aside className="hidden lg:flex fixed inset-y-0 start-0 w-64 border-e border-border bg-surface/70 backdrop-blur">
        <div className="flex flex-col flex-1">
          <SidebarContent isAdmin={isAdmin} />
        </div>
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="lg:hidden fixed inset-0 z-50">
          <div className="absolute inset-0 bg-heading/40 backdrop-blur-[1px] drawer-backdrop-enter" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 start-0 w-72 bg-surface shadow-pop flex flex-col drawer-panel-enter">
            <button
              className="absolute end-3 top-4 h-9 w-9 rounded-lg text-muted-fg hover:bg-muted flex items-center justify-center touch-target"
              onClick={() => setMobileOpen(false)}
              aria-label={t('common.close')}
            >
              <X className="h-5 w-5" />
            </button>
            <SidebarContent isAdmin={isAdmin} onNavigate={() => setMobileOpen(false)} />
            <div className="px-3 py-3 border-t border-border">
              <button
                className="flex items-center gap-3 h-11 px-3 w-full rounded-lg text-sm font-medium text-muted-fg hover:bg-destructive-soft hover:text-destructive transition-colors touch-target"
                onClick={() => {
                  setMobileOpen(false)
                  handleSignOut()
                }}
              >
                <LogOut className="h-5 w-5 shrink-0" aria-hidden />
                {t('auth.logout')}
              </button>
            </div>
          </aside>
        </div>
      )}

      {/* Main column */}
      <div className="lg:ps-64">
        {/* Top bar - cleaner mobile style */}
        <header className="sticky top-0 z-40 flex items-center justify-between gap-3 px-3 sm:px-6 h-14 sm:h-16 border-b border-border/60 navbar-glass">
          <div className="flex items-center gap-2">
            <button
              className="lg:hidden h-10 w-10 rounded-xl text-muted-fg hover:bg-muted flex items-center justify-center touch-target"
              onClick={() => setMobileOpen(true)}
              aria-label="Menu"
            >
              <Menu className="h-5 w-5" />
            </button>
            <div className="lg:hidden">
              <Logo />
            </div>
          </div>
          <div className="flex items-center gap-1.5 sm:gap-2">
            <div className="hidden sm:block">
              <LanguageSwitcher />
            </div>
            <button
              className="inline-flex items-center gap-2 h-9 px-2 rounded-lg hover:bg-muted transition-colors"
              onClick={handleSignOut}
              aria-label={t('auth.logout')}
            >
              <LogOut className="h-4 w-4 text-muted-fg" aria-hidden />
              <span className="hidden sm:inline text-sm font-medium text-muted-fg">{t('auth.logout')}</span>
            </button>
          </div>
        </header>

        <main id="main-content" className="px-3 sm:px-6 lg:px-8 pt-4 pb-6 max-w-6xl mx-auto">
          <PageTransition key={location.pathname}>
            <Outlet />
          </PageTransition>
        </main>
      </div>
    </div>
  )
}