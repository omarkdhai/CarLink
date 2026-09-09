import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Flag, Download, ChevronDown, Globe, MessageSquare } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { EmptyState } from '@/components/shared/EmptyState'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Alert } from '@/components/ui/Alert'
import { adminApi, adminQueryKeys } from '@/services/adminApi'
import { http } from '@/services/apiClient'
import { cn } from '@/lib/cn'
import { formatDateTime } from '@/lib/format'
import type { ReportDetailResponse, ReportReason, ReportStatus, ReportSummaryResponse } from '@/types'

const STATUS_TONE: Record<ReportStatus, BadgeTone> = { OPEN: 'danger', REVIEWED: 'warning', CLOSED: 'success' }
const REASON_TONE: Record<ReportReason, BadgeTone> = { SPAM: 'warning', ABUSE: 'danger', OTHER: 'neutral' }
const STATUSES: ReportStatus[] = ['OPEN', 'REVIEWED', 'CLOSED']

export default function AdminReportsPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [filter, setFilter] = useState<'ALL' | ReportStatus>('ALL')
  const [expanded, setExpanded] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [noticeTone, setNoticeTone] = useState<'success' | 'error'>('success')

  const reportsQuery = useQuery({
    queryKey: adminQueryKeys.reports(filter === 'ALL' ? undefined : filter),
    queryFn: () => adminApi.reports.list(filter === 'ALL' ? undefined : filter),
  })

  const detailQuery = useQuery({
    queryKey: adminQueryKeys.reportDetail(expanded ?? ''),
    queryFn: () => adminApi.reports.get(expanded!),
    enabled: Boolean(expanded),
  })

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: ReportStatus }) => adminApi.reports.changeStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'reports'] })
      setNotice(t('admin.exportSuccess'))
      setNoticeTone('success')
      setTimeout(() => setNotice(null), 2500)
    },
    onError: (err) => {
      setNotice(http.humanizeError(err))
      setNoticeTone('error')
    },
  })

  function exportCsv() {
    adminApi.reports
      .exportCsv(filter === 'ALL' ? undefined : filter)
      .then((blob) => {
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = 'reports.csv'
        a.click()
        URL.revokeObjectURL(url)
        setNotice(t('admin.exportSuccess'))
        setNoticeTone('success')
        setTimeout(() => setNotice(null), 2500)
      })
      .catch((err) => {
        setNotice(http.humanizeError(err))
        setNoticeTone('error')
      })
  }

  const reports = reportsQuery.data ?? []

  return (
    <div>
      <PageHeader
        title={t('admin.reports')}
        subtitle={t('admin.subtitle')}
        actions={
          <Button variant="secondary" onClick={exportCsv}>
            <Download className="h-4 w-4" aria-hidden />
            {t('admin.exportCsv')}
          </Button>
        }
      />

      {notice && (
        <div className="mb-4">
          <Alert tone={noticeTone}>{notice}</Alert>
        </div>
      )}

      {/* Status filter */}
      <div className="inline-flex rounded-lg border border-border bg-surface p-1 mb-6" role="tablist" aria-label={t('admin.reportsTitle')}>
        {(['ALL', ...STATUSES] as const).map((s) => (
          <button
            key={s}
            role="tab"
            aria-selected={filter === s}
            onClick={() => setFilter(s)}
            className={cn(
              'px-4 h-9 rounded-md text-sm font-semibold transition-colors touch-target',
              filter === s ? 'bg-primary text-white' : 'text-muted-fg hover:text-foreground',
            )}
          >
            {s === 'ALL' ? t('common.all') : t(`admin.reportStatus.${s}`)}
          </button>
        ))}
      </div>

      {reportsQuery.isError && <ErrorState message={t('errors.generic')} onRetry={() => reportsQuery.refetch()} />}

      {reportsQuery.isPending ? (
        <div className="bg-surface border border-border rounded-lg divide-y divide-border">
          {[0, 1, 2].map((i) => (
            <div key={i} className="p-4 space-y-2">
              <div className="h-4 w-1/3 rounded bg-muted animate-pulse" />
              <div className="h-3 w-1/2 rounded bg-muted animate-pulse" />
            </div>
          ))}
        </div>
      ) : reports.length === 0 ? (
        <div className="bg-surface border border-border rounded-lg">
          <EmptyState icon={<Flag className="h-7 w-7" aria-hidden />} title={t('admin.reportsEmpty')} />
        </div>
      ) : (
        <ul className="bg-surface border border-border rounded-lg divide-y divide-border">
          {reports.map((r) => (
            <ReportRow
              key={r.id}
              report={r}
              expanded={expanded === r.id}
              detail={expanded === r.id ? detailQuery.data : undefined}
              detailLoading={expanded === r.id && detailQuery.isPending}
              onToggle={() => setExpanded(expanded === r.id ? null : r.id)}
              onStatus={(status) => statusMutation.mutate({ id: r.id, status })}
              statusLoading={statusMutation.isPending}
            />
          ))}
        </ul>
      )}
    </div>
  )
}

function ReportRow({
  report,
  expanded,
  detail,
  detailLoading,
  onToggle,
  onStatus,
  statusLoading,
}: {
  report: ReportSummaryResponse
  expanded: boolean
  detail?: ReportDetailResponse
  detailLoading: boolean
  onToggle: () => void
  onStatus: (s: ReportStatus) => void
  statusLoading: boolean
}) {
  const { t } = useTranslation()
  return (
    <li>
      <button onClick={onToggle} className="w-full text-start p-4 hover:bg-muted transition-colors" aria-expanded={expanded}>
        <div className="flex items-center gap-3 flex-wrap">
          <Badge tone={REASON_TONE[report.reason]}>{t(`admin.reasonLabel.${report.reason}`)}</Badge>
          <Badge tone={STATUS_TONE[report.status]}>{t(`admin.reportStatus.${report.status}`)}</Badge>
          {report.vehicleNickname && <span className="text-sm font-semibold text-foreground">{report.vehicleNickname}</span>}
          <span className="text-sm text-muted-fg ms-auto">{formatDateTime(report.createdAt)}</span>
          <ChevronDown className={cn('h-4 w-4 text-muted-fg transition-transform', expanded && 'rotate-180')} aria-hidden />
        </div>
        {report.details && <p className="text-sm text-muted-fg mt-2 line-clamp-2">{report.details}</p>}
      </button>

      {expanded && (
        <div className="px-4 pb-4">
          <div className="rounded-lg border border-border bg-muted/50 p-4">
            <div className="grid gap-3 sm:grid-cols-2 text-sm mb-4">
              <Meta icon={<Flag className="h-4 w-4" aria-hidden />} label={t('admin.reason')} value={t(`admin.reasonLabel.${report.reason}`)} />
              <Meta icon={<Globe className="h-4 w-4" aria-hidden />} label={t('admin.reporterIp')} value={report.reporterIp} />
            </div>

            {detailLoading ? (
              <div className="h-16 rounded bg-muted animate-pulse" />
            ) : detail ? (
              <div>
                {detail.conversation ? (
                  <>
                    <p className="text-xs font-bold text-heading uppercase tracking-wide mb-2">{t('admin.lastMessage')}</p>
                    <div className="rounded-lg bg-surface border border-border px-4 py-3 mb-3">
                      <p className="text-[15px] leading-relaxed whitespace-pre-wrap break-words">{detail.conversation.lastMessageContent}</p>
                    </div>
                    <div className="flex items-center gap-3 text-sm text-muted-fg flex-wrap">
                      <span className="inline-flex items-center gap-1.5">
                        <MessageSquare className="h-4 w-4" aria-hidden />
                        {detail.conversation.messageCount} {t('admin.messageCount')}
                      </span>
                      <span className="text-xs">{formatDateTime(detail.conversation.createdAt)}</span>
                    </div>
                  </>
                ) : (
                  <p className="text-sm text-muted-fg">{t('admin.deletedConversation')}</p>
                )}
              </div>
            ) : null}

            <div className="flex flex-wrap gap-2 mt-4">
              {STATUSES.filter((s) => s !== report.status).map((s) => (
                <Button key={s} variant="outline" size="sm" disabled={statusLoading} onClick={() => onStatus(s)}>
                  {t(`admin.reportStatus.${s}`)}
                </Button>
              ))}
            </div>
          </div>
        </div>
      )}
    </li>
  )
}

function Meta({ icon, label, value }: { icon: React.ReactNode; label: string; value: string }) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-muted-fg">{icon}</span>
      <span className="text-muted-fg">{label} :</span>
      <span className="font-medium text-foreground">{value}</span>
    </div>
  )
}
