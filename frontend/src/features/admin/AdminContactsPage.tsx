import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Copy, Inbox, Mail, MessageSquareText, Phone, Reply, User } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { adminApi } from '@/services/adminApi'
import { http } from '@/services/apiClient'
import { Badge } from '@/components/ui/Badge'
import { buttonClasses, Button } from '@/components/ui/Button'
import type { AdminContactView } from '@/types'
import {
  DataList,
  DateCell,
  DetailDialog,
  DetailSection,
  EmptyResults,
  Identity,
  InfoGrid,
  Muted,
  Pagination,
  SearchBox,
  SortSelect,
  Toolbar,
  useAdminFormat,
  useListParams,
  useToast,
  type Column,
} from './components/AdminList'

const PAGE_SIZE = 30

/** Free-form reason from the public contact form, shown in sentence case. */
function reasonLabel(reason: string) {
  const r = (reason ?? '').replace(/_/g, ' ').toLowerCase()
  return r.charAt(0).toUpperCase() + r.slice(1)
}

export default function AdminContactsPage() {
  const { t } = useTranslation()
  const f = useAdminFormat()
  const toast = useToast()
  const list = useListParams({ sort: 'createdAt,desc' })
  const [selected, setSelected] = useState<AdminContactView | null>(null)

  const q = useQuery({
    queryKey: ['admin', 'contacts', list.page, list.q, list.sort],
    queryFn: () => adminApi.contacts.list(list.page, PAGE_SIZE, list.q, list.sort),
    placeholderData: keepPreviousData,
  })

  if (q.isError) return <ErrorState message={http.humanizeError(q.error)} onRetry={() => q.refetch()} />

  const replyHref = (c: AdminContactView) =>
    `mailto:${c.email}?subject=${encodeURIComponent(t('admin.contacts.replySubject', { reason: reasonLabel(c.reason) }))}`

  const columns: Column<AdminContactView>[] = [
    { key: 'sender', header: t('admin.contacts.sender'), cell: (c) => <Identity avatar={c.name} primary={c.name} secondary={c.email} /> },
    { key: 'reason', header: t('admin.contacts.subject'), cell: (c) => <Badge tone="primary">{reasonLabel(c.reason)}</Badge> },
    {
      key: 'message',
      header: t('admin.contacts.message'),
      className: 'max-w-md',
      cell: (c) => (c.note ? <p className="text-muted-fg line-clamp-2">{c.note}</p> : <Muted />),
    },
    { key: 'phone', header: t('admin.orders.phone'), cell: (c) => (c.mobile ? <span className="whitespace-nowrap">{c.mobile}</span> : <Muted />) },
    { key: 'received', header: t('admin.contacts.received'), cell: (c) => <DateCell iso={c.createdAt} /> },
  ]

  const data = q.data
  const contacts = data?.content ?? []

  return (
    <div>
      <PageHeader title={t('nav.adminContacts')} subtitle={t('admin.contacts.subtitle')} />

      <Toolbar>
        <SearchBox value={list.input} onChange={list.setInput} placeholder={t('admin.contacts.searchPlaceholder')} />
        <div className="sm:ms-auto">
          <SortSelect
            value={list.sort}
            onChange={list.setSort}
            options={[
              { value: 'createdAt,desc', label: t('vehicle.sortNewest') },
              { value: 'createdAt,asc', label: t('vehicle.sortOldest') },
            ]}
          />
        </div>
      </Toolbar>

      <div className={q.isPlaceholderData ? 'opacity-60 transition-opacity' : 'transition-opacity'}>
        <DataList
          rows={contacts}
          columns={columns}
          rowKey={(c) => c.id}
          rowLabel={(c) => `${c.name}, ${reasonLabel(c.reason)}`}
          onOpen={setSelected}
          loading={q.isPending}
          empty={
            <EmptyResults
              icon={<Inbox className="h-6 w-6" aria-hidden />}
              title={list.hasFilters ? t('admin.contacts.noMatch') : t('admin.contacts.empty')}
              hint={list.hasFilters ? t('admin.list.noResultsHint') : t('admin.contacts.emptyHint')}
              onClear={list.hasFilters ? list.clear : undefined}
            />
          }
          card={(c) => (
            <div className="space-y-2">
              <div className="flex items-start justify-between gap-3">
                <Identity avatar={c.name} primary={c.name} secondary={f.dateTime(c.createdAt)} />
                <Badge tone="primary">{reasonLabel(c.reason)}</Badge>
              </div>
              {c.note && <p className="text-sm text-muted-fg line-clamp-2">{c.note}</p>}
            </div>
          )}
        />
      </div>

      {data && (
        <Pagination page={list.page} size={PAGE_SIZE} total={data.totalElements} totalPages={data.totalPages} onPage={list.setPage} />
      )}

      <DetailDialog
        open={Boolean(selected)}
        onClose={() => setSelected(null)}
        title={selected?.name ?? ''}
        badges={selected && <Badge tone="primary">{reasonLabel(selected.reason)}</Badge>}
        subtitle={selected && t('admin.contacts.receivedOn', { date: f.dateTime(selected.createdAt) })}
        footer={
          selected && (
            <>
              <Button
                size="sm"
                variant="ghost"
                className="me-auto"
                onClick={() => {
                  void navigator.clipboard?.writeText(selected.email)
                  toast.show(t('admin.contacts.emailCopied'))
                }}
              >
                <Copy className="h-4 w-4" aria-hidden />
                {t('admin.contacts.copyEmail')}
              </Button>
              <a href={replyHref(selected)} className={buttonClasses({ size: 'sm' })}>
                <Reply className="h-4 w-4" aria-hidden />
                {t('admin.contacts.reply')}
              </a>
            </>
          )
        }
      >
        {selected && (
          <>
            <DetailSection icon={MessageSquareText} title={t('admin.contacts.message')}>
              <blockquote className="rounded-xl bg-muted/50 border-s-4 border-primary px-4 py-3 text-sm leading-relaxed whitespace-pre-wrap">
                {selected.note || <Muted />}
              </blockquote>
            </DetailSection>
            <DetailSection icon={User} title={t('admin.contacts.sender')}>
              <InfoGrid
                items={[
                  { label: t('admin.fleet.ownerName'), value: selected.name },
                  {
                    label: t('auth.email'),
                    value: (
                      <a href={`mailto:${selected.email}`} className="inline-flex items-center gap-1.5 text-primary hover:underline break-all">
                        <Mail className="h-3.5 w-3.5 shrink-0" aria-hidden />
                        {selected.email}
                      </a>
                    ),
                  },
                  {
                    label: t('admin.orders.phone'),
                    value: selected.mobile ? (
                      <a href={`tel:${selected.mobile}`} className="inline-flex items-center gap-1.5 text-primary hover:underline">
                        <Phone className="h-3.5 w-3.5" aria-hidden />
                        {selected.mobile}
                      </a>
                    ) : (
                      <Muted />
                    ),
                  },
                  { label: t('admin.contacts.received'), value: f.dateTime(selected.createdAt) },
                ]}
              />
            </DetailSection>
          </>
        )}
      </DetailDialog>
      {toast.node}
    </div>
  )
}
