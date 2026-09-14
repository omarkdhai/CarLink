import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { ScrollText, Search } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { Badge } from '@/components/ui/Badge'
import { adminApi, adminQueryKeys } from '@/services/adminApi'
import { formatDateTime } from '@/lib/format'

const LIMITS = [25, 50, 100]

export default function AdminAuditLogsPage() {
  const { t } = useTranslation()
  const [action, setAction] = useState('')
  const [entityType, setEntityType] = useState('')
  const [limit, setLimit] = useState(100)
  const [applied, setApplied] = useState({ action: '', entityType: '' })

  const filters = {
    action: applied.action || undefined,
    entityType: applied.entityType || undefined,
    limit,
  }

  const logsQuery = useQuery({
    queryKey: adminQueryKeys.auditLogs(filters),
    queryFn: () => adminApi.auditLogs.list(filters),
  })

  function apply() {
    setApplied({ action: action.trim(), entityType: entityType.trim() })
  }

  const logs = logsQuery.data ?? []

  return (
    <div>
      <PageHeader title={t('admin.auditLogs')} subtitle={t('admin.subtitle')} />

      {/* Filters */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end mb-6">
        <div className="flex-1 max-w-xs">
          <label className="block text-xs font-bold text-muted-fg mb-1.5">{t('admin.auditAction')}</label>
          <input
            value={action}
            onChange={(e) => setAction(e.target.value)}
            placeholder={t('admin.auditAction')}
            className="w-full h-11 rounded-lg border border-input-border bg-surface px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>
        <div className="flex-1 max-w-xs">
          <label className="block text-xs font-bold text-muted-fg mb-1.5">{t('admin.entityType')}</label>
          <input
            value={entityType}
            onChange={(e) => setEntityType(e.target.value)}
            placeholder={t('admin.entityType')}
            className="w-full h-11 rounded-lg border border-input-border bg-surface px-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
          />
        </div>
        <div>
          <label className="block text-xs font-bold text-muted-fg mb-1.5">{t('admin.limit')}</label>
          <div className="inline-flex rounded-lg border border-border bg-surface p-1">
            {LIMITS.map((n) => (
              <button
                key={n}
                onClick={() => setLimit(n)}
                aria-pressed={limit === n}
                className={
                  'px-3 h-9 rounded-md text-sm font-semibold transition-colors touch-target ' +
                  (limit === n ? 'bg-primary text-white' : 'text-muted-fg hover:text-foreground')
                }
              >
                {n}
              </button>
            ))}
          </div>
        </div>
        <button
          onClick={apply}
          className="inline-flex items-center gap-2 h-11 px-4 rounded-lg bg-primary text-white font-semibold text-sm hover:bg-primary-hover"
        >
          <Search className="h-4 w-4" aria-hidden />
          {t('common.search')}
        </button>
      </div>

      {logsQuery.isError && <ErrorState message={t('errors.generic')} onRetry={() => logsQuery.refetch()} />}

      {logsQuery.isPending ? (
        <div className="bg-surface border border-border rounded-lg divide-y divide-border">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="p-4 space-y-2">
              <div className="h-4 w-1/2 rounded bg-muted animate-pulse" />
              <div className="h-3 w-1/3 rounded bg-muted animate-pulse" />
            </div>
          ))}
        </div>
      ) : logs.length === 0 ? (
        <div className="bg-surface border border-border rounded-lg">
          <EmptyState icon={<ScrollText className="h-7 w-7" aria-hidden />} title={t('admin.auditEmpty')} />
        </div>
      ) : (
        <div className="bg-surface border border-border rounded-lg overflow-x-auto">
          <table className="w-full text-sm min-w-[640px]">
            <thead>
              <tr className="text-start text-xs text-muted-fg border-b border-border">
                <th className="text-start font-bold px-4 py-3">{t('admin.auditAction')}</th>
                <th className="text-start font-bold px-4 py-3">{t('admin.entityType')}</th>
                <th className="text-start font-bold px-4 py-3">{t('admin.actor')}</th>
                <th className="text-start font-bold px-4 py-3">{t('admin.details')}</th>
                <th className="text-end font-bold px-4 py-3">{t('common.date')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {logs.map((log) => (
                <tr key={log.id} className="align-top">
                  <td className="px-4 py-3">
                    <Badge tone="neutral">{log.action}</Badge>
                  </td>
                  <td className="px-4 py-3 text-foreground">{log.entityType}</td>
                  <td className="px-4 py-3 text-muted-fg">{log.actorEmail ?? '—'}</td>
                  <td className="px-4 py-3 text-muted-fg max-w-[280px]">
                    {log.details ? (
                      <code className="text-xs break-words whitespace-pre-wrap">{JSON.stringify(log.details)}</code>
                    ) : (
                      <span className="text-muted-fg/60">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-muted-fg text-end whitespace-nowrap">{formatDateTime(log.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
