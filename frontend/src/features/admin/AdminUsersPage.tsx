import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { BadgeCheck, CarFront, Shield, ShieldOff, UserMinus, UserPlus, Users } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { adminApi, adminQueryKeys } from '@/services/adminApi'
import { http } from '@/services/apiClient'
import { useAuth } from '@/features/auth/AuthContext'
import type { AdminUserResponse, Role } from '@/types'
import {
  DataList,
  DateCell,
  EmptyResults,
  Identity,
  SearchBox,
  StatusTabs,
  Toolbar,
  useAdminFormat,
  useListParams,
  useToast,
  type Column,
} from './components/AdminList'

type ActionType = 'deactivate' | 'activate' | 'promote' | 'demote'
type Action = { user: AdminUserResponse; type: ActionType } | null

function fullName(u: AdminUserResponse) {
  return `${u.firstName} ${u.lastName}`.trim() || u.email
}

export default function AdminUsersPage() {
  const { t } = useTranslation()
  const f = useAdminFormat()
  const { user: me } = useAuth()
  const queryClient = useQueryClient()
  const toast = useToast()
  const list = useListParams()
  const [role, setRole] = useState<'' | Role>('')
  const [confirm, setConfirm] = useState<Action>(null)

  // status tab: '' (all) | 'true' (active) | 'false' (inactive)
  const filters = useMemo(
    () => ({
      q: list.q || undefined,
      active: list.status === '' ? undefined : list.status === 'true',
      role: role || undefined,
    }),
    [list.q, list.status, role],
  )

  const usersQuery = useQuery({
    queryKey: adminQueryKeys.users(filters),
    queryFn: () => adminApi.users.list(filters),
    placeholderData: keepPreviousData,
  })

  const mutation = useMutation({
    mutationFn: ({ user, type }: NonNullable<Action>) => {
      if (type === 'deactivate') return adminApi.users.deactivate(user.id)
      if (type === 'activate') return adminApi.users.activate(user.id)
      if (type === 'promote') return adminApi.users.changeRole(user.id, 'ADMIN')
      return adminApi.users.changeRole(user.id, 'USER')
    },
    onSuccess: (_d, { user, type }) => {
      setConfirm(null)
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
      queryClient.invalidateQueries({ queryKey: ['admin', 'stats'] })
      toast.show(t(`admin.people.done.${type}`, { name: fullName(user) }))
    },
  })

  if (usersQuery.isError) return <ErrorState message={t('errors.generic')} onRetry={() => usersQuery.refetch()} />

  const users = usersQuery.data ?? []
  const hasFilters = list.hasFilters || Boolean(role)

  const actions = (u: AdminUserResponse) => {
    const isMe = u.id === me?.id
    const stop = (type: ActionType) => (e: React.MouseEvent) => {
      e.stopPropagation()
      setConfirm({ user: u, type })
    }
    return (
      <div className="flex justify-end gap-1">
        {u.role === 'ADMIN' ? (
          <Button variant="ghost" size="sm" disabled={isMe} onClick={stop('demote')} title={isMe ? t('admin.people.selfHint') : undefined}>
            <ShieldOff className="h-4 w-4" aria-hidden />
            {t('admin.demote')}
          </Button>
        ) : (
          <Button variant="ghost" size="sm" onClick={stop('promote')}>
            <Shield className="h-4 w-4" aria-hidden />
            {t('admin.promote')}
          </Button>
        )}
        {u.active ? (
          <Button
            variant="ghost"
            size="sm"
            disabled={isMe}
            title={isMe ? t('admin.people.selfHint') : undefined}
            className="text-destructive! hover:bg-destructive-soft"
            onClick={stop('deactivate')}
          >
            <UserMinus className="h-4 w-4" aria-hidden />
            {t('admin.deactivate')}
          </Button>
        ) : (
          <Button variant="secondary" size="sm" onClick={stop('activate')}>
            <UserPlus className="h-4 w-4" aria-hidden />
            {t('admin.activate')}
          </Button>
        )}
      </div>
    )
  }

  const identity = (u: AdminUserResponse) => (
    <Identity
      avatar={fullName(u)}
      primary={
        <span className="inline-flex items-center gap-1.5">
          {fullName(u)}
          {u.id === me?.id && <Badge tone="primary">{t('admin.people.you')}</Badge>}
        </span>
      }
      secondary={
        <span className="inline-flex items-center gap-1">
          {u.email}
          {u.emailVerified && <BadgeCheck className="h-3.5 w-3.5 text-success shrink-0" aria-label={t('admin.people.verified')} />}
        </span>
      }
    />
  )

  const columns: Column<AdminUserResponse>[] = [
    { key: 'user', header: t('admin.user'), cell: identity },
    {
      key: 'role',
      header: t('admin.role'),
      cell: (u) => (
        <Badge tone={u.role === 'ADMIN' ? 'primary' : 'neutral'}>
          {u.role === 'ADMIN' && <Shield className="h-3 w-3 me-1" aria-hidden />}
          {t(`admin.roles.${u.role}`)}
        </Badge>
      ),
    },
    {
      key: 'status',
      header: t('vehicle.status'),
      cell: (u) => <Badge tone={u.active ? 'success' : 'neutral'}>{u.active ? t('admin.active') : t('admin.inactive')}</Badge>,
    },
    {
      key: 'vehicles',
      header: t('admin.vehicles'),
      numeric: true,
      cell: (u) => (
        <span className="inline-flex items-center gap-1.5">
          <CarFront className="h-4 w-4 text-muted-fg" aria-hidden />
          {f.num(u.vehicleCount)}
        </span>
      ),
    },
    { key: 'joined', header: t('admin.memberSince'), cell: (u) => <DateCell iso={u.createdAt} /> },
    { key: 'actions', header: '', className: 'text-end', cell: actions },
  ]

  return (
    <div>
      <PageHeader title={t('admin.users')} subtitle={t('admin.people.subtitle')} />

      <Toolbar>
        <SearchBox value={list.input} onChange={list.setInput} placeholder={t('admin.searchPlaceholder')} />
        <StatusTabs
          label={t('vehicle.status')}
          value={list.status}
          onChange={list.setStatus}
          options={[
            { value: '', label: t('common.all') },
            { value: 'true', label: t('admin.active'), dot: 'bg-success' },
            { value: 'false', label: t('admin.inactive'), dot: 'bg-muted-fg' },
          ]}
        />
        <StatusTabs
          label={t('admin.role')}
          value={role}
          onChange={(v) => setRole(v as '' | Role)}
          options={[
            { value: '', label: t('admin.people.allRoles') },
            { value: 'USER', label: t('admin.roles.USER') },
            { value: 'ADMIN', label: t('admin.roles.ADMIN') },
          ]}
        />
      </Toolbar>

      <div className={usersQuery.isPlaceholderData ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
        <DataList
          rows={users}
          columns={columns}
          rowKey={(u) => u.id}
          loading={usersQuery.isPending}
          empty={
            <EmptyResults
              icon={<Users className="h-6 w-6" aria-hidden />}
              title={t('admin.noUsers')}
              hint={hasFilters ? t('admin.list.noResultsHint') : undefined}
              onClear={
                hasFilters
                  ? () => {
                      list.clear()
                      setRole('')
                    }
                  : undefined
              }
            />
          }
          card={(u) => (
            <div className="space-y-3">
              <div className="flex items-start justify-between gap-3">
                {identity(u)}
                <Badge tone={u.active ? 'success' : 'neutral'}>{u.active ? t('admin.active') : t('admin.inactive')}</Badge>
              </div>
              <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-fg">
                <span>{t(`admin.roles.${u.role}`)}</span>
                <span className="inline-flex items-center gap-1">
                  <CarFront className="h-3.5 w-3.5" aria-hidden />
                  {f.num(u.vehicleCount)}
                </span>
                <span>{t('admin.people.joined', { date: f.date(u.createdAt) })}</span>
              </div>
              {actions(u)}
            </div>
          )}
        />
      </div>

      {users.length > 0 && (
        <p className="text-sm text-muted-fg mt-4">{t('admin.people.count', { count: users.length })}</p>
      )}

      <ConfirmDialog
        open={Boolean(confirm)}
        title={confirm ? t(`admin.${confirm.type}`) : ''}
        description={confirm ? t(`admin.people.confirm.${confirm.type}`, { name: fullName(confirm.user) }) : ''}
        confirmLabel={confirm ? t(`admin.${confirm.type}`) : t('common.confirm')}
        tone={confirm?.type === 'deactivate' || confirm?.type === 'demote' ? 'danger' : 'primary'}
        loading={mutation.isPending}
        onConfirm={() => confirm && mutation.mutate(confirm)}
        onCancel={() => {
          setConfirm(null)
          mutation.reset()
        }}
      />
      {mutation.isError && confirm && (
        <p role="alert" className="mt-3 text-sm text-destructive">
          {http.humanizeError(mutation.error)}
        </p>
      )}
      {toast.node}
    </div>
  )
}
