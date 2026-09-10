import { useState } from 'react'
import { NavLink, Outlet, Link, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  Users,
  Car,
  QrCode,
  Flag,
  ScrollText,
  ShieldCheck,
  Menu,
  X,
  ArrowLeft,
  LogOut,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/AuthContext'
import { Logo } from '@/components/layout/Logo'
import { cn } from '@/lib/cn'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'

const adminNav = [
  { to: '/admin', key: 'nav.adminDashboard', icon: LayoutDashboard, end: true },
  { to: '/admin/users', key: 'nav.adminUsers', icon: Users },
  { to: '/admin/vehicles', key: 'nav.adminVehicles', icon: Car },
  { to: '/admin/qr-codes', key: 'nav.adminQrCodes', icon: QrCode },
  { to: '/admin/reports', key: 'nav.adminReports', icon: Flag },
  { to: '/admin/audit-logs', key: 'nav.adminAuditLogs', icon: ScrollText },
]

function AdminNav({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation()
  return (
    <nav className="space-y-1 px-3" aria-label={t('nav.admin')}>
      {adminNav.map(({ to, key, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
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
          <Icon className="h-5 w-5 shrink-0" aria-hidden />
          {t(key)}
        </NavLink>
      ))}
    </nav>
  )
}

export function AdminLayout() {
  const { t } = useTranslation()
  const { signOut, user } = useAuth()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)

  async function handleSignOut() {
    await signOut()
    navigate('/login', { replace: true })
  }

  const initials = user ? `${user.firstName[0] ?? ''}${user.lastName[0] ?? ''}`.toUpperCase() : '?'

  return (
    <div className="min-h-screen">
      {/* Desktop sidebar */}
      <aside className="hidden lg:flex fixed inset-y-0 start-0 w-64 border-e border-border bg-surface/70 backdrop-blur">
        <div className="flex flex-col flex-1">
          <div className="px-5 pt-5 pb-4 flex flex-col gap-3">
            <Logo />
            <Link
              to="/dashboard"
              className="inline-flex items-center gap-2 text-sm font-medium text-muted-fg hover:text-primary transition-colors"
            >
              <ArrowLeft className="h-4 w-4" aria-hidden />
              {t('nav.dashboard')}
            </Link>
          </div>
          <div className="flex-1 overflow-y-auto py-2">
            <div className="px-3 mb-1 flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-muted-fg/70">
              <ShieldCheck className="h-4 w-4" aria-hidden />
              {t('nav.admin')}
            </div>
            <AdminNav />
          </div>
        </div>
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="lg:hidden fixed inset-0 z-50">
          <div className="absolute inset-0 bg-heading/40 backdrop-blur-[1px]" onClick={() => setMobileOpen(false)} />
          <aside className="absolute inset-y-0 start-0 w-72 bg-surface shadow-pop flex flex-col">
            <button
              className="absolute end-3 top-4 h-9 w-9 rounded-lg text-muted-fg hover:bg-muted flex items-center justify-center"
              onClick={() => setMobileOpen(false)}
              aria-label={t('common.close')}
            >
              <X className="h-5 w-5" />
            </button>
            <div className="px-5 pt-5 pb-4">
              <Logo />
            </div>
            <AdminNav onNavigate={() => setMobileOpen(false)} />
          </aside>
        </div>
      )}

      <div className="lg:ps-64">
        <header className="sticky top-0 z-40 flex items-center justify-between gap-3 px-4 sm:px-6 h-16 border-b border-border bg-surface/80 backdrop-blur">
          <button
            className="lg:hidden h-11 w-11 rounded-lg text-muted-fg hover:bg-muted flex items-center justify-center"
            onClick={() => setMobileOpen(true)}
            aria-label="Menu"
          >
            <Menu className="h-5 w-5" />
          </button>
          <div className="flex items-center gap-2 ms-auto">
            <LanguageSwitcher />
            <button
              className="inline-flex items-center gap-2 h-9 px-2 rounded-lg hover:bg-muted transition-colors"
              onClick={handleSignOut}
              aria-label={t('auth.logout')}
            >
              <LogOut className="h-4 w-4 text-muted-fg" aria-hidden />
              <span className="hidden sm:inline text-sm font-medium text-muted-fg">{t('auth.logout')}</span>
            </button>
            <Link to="/profile" className="flex items-center gap-2.5 ps-2" aria-label={t('nav.profile')}>
              <span className="h-8 w-8 rounded-full bg-primary-soft text-primary flex items-center justify-center text-sm font-bold">
                {initials}
              </span>
            </Link>
          </div>
        </header>

        <main className="px-4 sm:px-6 lg:px-8 py-6 max-w-7xl mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}