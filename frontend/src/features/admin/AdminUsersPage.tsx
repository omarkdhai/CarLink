import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Search, UserPlus, UserMinus, Shield, ShieldOff, Mail } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { adminApi, adminQueryKeys } from '@/services/adminApi'
import { useAuth } from '@/features/auth/AuthContext'
import { cn } from '@/lib/cn'
import type { Role } from '@/types'

type Action = { id: string; type: 'deactivate' | 'activate' | 'promote' | 'demote' } | null

export default function AdminUsersPage() {
  const { t } = useTranslation()
  const { user: me } = useAuth()
  const queryClient = useQueryClient()

  const [q, setQ] = useState('')
  const [active, setActive] = useState<'ALL' | 'true' | 'false'>('ALL')
  const [role, setRole] = useState<'ALL' | Role>('ALL')
  const [confirm, setConfirm] = useState<Action>(null)

  const filters = useMemo(
    () => ({
      q: q.trim() || undefined,
      active: active === 'ALL' ? undefined : active === 'true',
      role: role === 'ALL' ? undefined : role,
    }),
    [q, active, role],
  )

  const usersQuery = useQuery({
    queryKey: adminQueryKeys.users(filters),
    queryFn: () => adminApi.users.list(filters),
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })

  const mutation = useMutation({
    mutationFn: ({ id, type }: NonNullable<Action>) => {
      if (type === 'deactivate') return adminApi.users.deactivate(id)
      if (type === 'activate') return adminApi.users.activate(id)
      if (type === 'promote') return adminApi.users.changeRole(id, 'ADMIN')
      return adminApi.users.changeRole(id, 'USER')
    },
    onSuccess: () => {
      setConfirm(null)
      invalidate()
    },
  })

  const users = usersQuery.data ?? []

  return (
    <div>
      <PageHeader title={t('admin.users')} subtitle={t('admin.subtitle')} />

      {/* Filters */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center mb-6">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute start-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-fg" aria-hidden />
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder={t('admin.searchPlaceholder')}
            className="w-full h-11 rounded-lg border border-input-border bg-surface ps-9 pe-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary focus:border-transparent"
            aria-label={t('common.search')}
          />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <FilterChip active={active === 'ALL'} onClick={() => setActive('ALL')}>{t('common.all')}</FilterChip>
          <FilterChip active={active === 'true'} onClick={() => setActive('true')}>{t('admin.active')}</FilterChip>
          <FilterChip active={active === 'false'} onClick={() => setActive('false')}>{t('admin.inactive')}</FilterChip>
          <span className="mx-1 text-muted-fg/50" aria-hidden>|</span>
          <FilterChip active={role === 'ALL'} onClick={() => setRole('ALL')}>{t('common.all')}</FilterChip>
          <FilterChip active={role === 'USER'} onClick={() => setRole('USER')}>{t('admin.roles.USER')}</FilterChip>
          <FilterChip active={role === 'ADMIN'} onClick={() => setRole('ADMIN')}>{t('admin.roles.ADMIN')}</FilterChip>
        </div>
      </div>

      {usersQuery.isError && <ErrorState message={t('errors.generic')} onRetry={() => usersQuery.refetch()} />}

      {usersQuery.isPending ? (
        <div className="bg-surface border border-border rounded-lg divide-y divide-border">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="flex items-center gap-4 p-4">
              <div className="h-10 w-10 rounded-full bg-muted animate-pulse" />
              <div className="flex-1 space-y-2">
                <div className="h-4 w-2/3 rounded bg-muted animate-pulse" />
                <div className="h-3 w-1/3 rounded bg-muted animate-pulse" />
              </div>
            </div>
          ))}
        </div>
      ) : users.length === 0 ? (
        <div className="bg-surface border border-border rounded-lg">
          <EmptyState icon={<UserPlus className="h-7 w-7" aria-hidden />} title={t('admin.noUsers')} />
        </div>
      ) : (
        <div className="bg-surface border border-border rounded-lg overflow-hidden">
          <ul className="divide-y divide-border">
            {users.map((u) => {
              const isMe = u.id === me?.id
              return (
                <li key={u.id} className="p-4 sm:p-5">
                  <div className="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4">
                    <div className="h-10 w-10 rounded-full bg-primary-soft text-primary flex items-center justify-center font-bold shrink-0">
                      {`${u.firstName[0] ?? ''}${u.lastName[0] ?? ''}`.toUpperCase()}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 flex-wrap">
                        <p className="font-bold text-heading truncate">
                          {u.firstName} {u.lastName}
                        </p>
                        {isMe && <Badge tone="primary">{t('admin.actor')}</Badge>}
                      </div>
                      <p className="text-sm text-muted-fg flex items-center gap-1.5 truncate">
                        <Mail className="h-3.5 w-3.5 shrink-0" aria-hidden />
                        {u.email}
                      </p>
                    </div>
                    <div className="flex items-center gap-2 flex-wrap">
                      <Badge tone={u.active ? 'success' : 'neutral'}>
                        {u.active ? t('admin.active') : t('admin.inactive')}
                      </Badge>
                      <Badge tone={u.role === 'ADMIN' ? 'primary' : 'neutral'}>{t(`admin.roles.${u.role}`)}</Badge>
                      <span className="text-xs text-muted-fg whitespace-nowrap">
                        {u.vehicleCount} {t('admin.vehiclesCount')}
                      </span>
                    </div>
                    <div className="flex items-center gap-2 sm:ps-2">
                      {u.active ? (
                        <Button variant="outline" size="sm" disabled={isMe} onClick={() => setConfirm({ id: u.id, type: 'deactivate' })}>
                          <UserMinus className="h-4 w-4" aria-hidden />
                          {t('admin.deactivate')}
                        </Button>
                      ) : (
                        <Button variant="outline" size="sm" onClick={() => setConfirm({ id: u.id, type: 'activate' })}>
                          <UserPlus className="h-4 w-4" aria-hidden />
                          {t('admin.activate')}
                        </Button>
                      )}
                      {u.role === 'ADMIN' ? (
                        <Button variant="ghost" size="sm" disabled={isMe} onClick={() => setConfirm({ id: u.id, type: 'demote' })}>
                          <ShieldOff className="h-4 w-4" aria-hidden />
                          {t('admin.demote')}
                        </Button>
                      ) : (
                        <Button variant="ghost" size="sm" onClick={() => setConfirm({ id: u.id, type: 'promote' })}>
                          <Shield className="h-4 w-4" aria-hidden />
                          {t('admin.promote')}
                        </Button>
                      )}
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        </div>
      )}

      <ConfirmDialog
        open={Boolean(confirm)}
        title={confirmTitle(confirm)}
        description={confirmDescription(confirm)}
        confirmLabel={confirmLabel(confirm)}
        tone={confirm?.type === 'deactivate' ? 'danger' : 'primary'}
        loading={mutation.isPending}
        onConfirm={() => mutation.mutate(confirm!)}
        onCancel={() => setConfirm(null)}
      />
    </div>
  )

  function confirmTitle(a: Action) {
    switch (a?.type) {
      case 'deactivate':
        return t('admin.deactivate')
      case 'activate':
        return t('admin.activate')
      case 'promote':
        return t('admin.promote')
      case 'demote':
        return t('admin.demote')
      default:
        return ''
    }
  }

  function confirmDescription(a: Action) {
    switch (a?.type) {
      case 'deactivate':
        return t('admin.deactivateConfirm')
      case 'activate':
        return t('admin.activate')
      case 'promote':
        return t('admin.promoteConfirm')
      case 'demote':
        return t('admin.demoteConfirm')
      default:
        return ''
    }
  }

  function confirmLabel(a: Action) {
    return confirmTitle(a) || t('common.confirm')
  }
}

function FilterChip({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        'px-3 h-9 rounded-full border text-sm font-medium transition-colors touch-target',
        active ? 'border-primary bg-primary-soft text-primary' : 'border-border text-muted-fg hover:text-foreground',
      )}
    >
      {children}
    </button>
  )
}
