import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, QrCode, Download, Printer, Link2, Check, ShieldAlert, RefreshCw, Power } from 'lucide-react'
import { vehicleApi, vehicleQueryKeys } from '@/services/vehicleApi'
import { qrApi, qrQueryKeys } from '@/services/qrApi'
import { toApiError } from '@/services/errors'
import { http } from '@/services/apiClient'
import { Button } from '@/components/ui/Button'
import { Badge } from '@/components/ui/Badge'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card'
import { Alert } from '@/components/ui/Alert'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EmptyState } from '@/components/shared/EmptyState'
import type { QrIssuedResponse } from '@/types'

export default function QrManagementPage() {
  const { t } = useTranslation()
  const { id = '' } = useParams<{ id: string }>()
  const queryClient = useQueryClient()

  const [issued, setIssued] = useState<QrIssuedResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<'regenerate' | 'deactivate' | null>(null)
  const [copied, setCopied] = useState(false)

  const vehicle = useQuery({
    queryKey: vehicleQueryKeys.detail(id),
    queryFn: () => vehicleApi.get(id),
    enabled: Boolean(id),
    retry: false,
  })

  const current = useQuery({
    queryKey: qrQueryKeys.current(id),
    queryFn: () => qrApi.current(id),
    enabled: Boolean(id),
    retry: false,
  })

  const history = useQuery({
    queryKey: qrQueryKeys.history(id),
    queryFn: () => qrApi.history(id),
    enabled: Boolean(id),
  })

  const currentStatus = current.data
  const noActiveQr = current.isError && toApiError(current.error).status === 404

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: qrQueryKeys.current(id) })
    queryClient.invalidateQueries({ queryKey: qrQueryKeys.history(id) })
  }

  const issueMutation = useMutation({
    mutationFn: () => qrApi.issue(id),
    onSuccess: (data) => {
      setIssued(data)
      setSuccess(t('qr.generateSuccess'))
      setError(null)
      setCopied(false)
      invalidate()
    },
    onError: (err) => setError(http.humanizeError(err)),
  })

  const deactivateMutation = useMutation({
    mutationFn: () => qrApi.deactivate(id),
    onSuccess: () => {
      setConfirm(null)
      setIssued(null)
      setSuccess(t('qr.deactivateSuccess'))
      setError(null)
      invalidate()
    },
    onError: (err) => {
      setConfirm(null)
      setError(http.humanizeError(err))
    },
  })

  async function handleCopy(url: string) {
    try {
      await navigator.clipboard.writeText(url)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setError(t('errors.generic'))
    }
  }

  const reveal = issued
  const busy = issueMutation.isPending || deactivateMutation.isPending

  return (
    <div className="max-w-3xl mx-auto">
      <Link
        to={`/vehicles/${id}`}
        className="inline-flex items-center gap-1.5 text-sm font-medium text-muted-fg hover:text-primary mb-4 transition-colors"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        {t('vehicle.details')}
      </Link>

      <Card>
        <CardHeader>
          <div>
            <CardTitle>{t('qr.title')}</CardTitle>
            <CardDescription>
              {vehicle.data?.licensePlate}
              {vehicle.data?.nickname ? ` · ${vehicle.data.nickname}` : ''}
            </CardDescription>
          </div>
        </CardHeader>

        <CardContent>
          {error && (
            <div className="mb-4">
              <Alert tone="error">{error}</Alert>
            </div>
          )}
          {success && !reveal && (
            <div className="mb-4">
              <Alert tone="success">{success}</Alert>
            </div>
          )}

          {/* One-time reveal immediately after generation */}
          {reveal && (
            <div className="rounded-xl border-2 border-primary bg-primary-soft/50 p-6 mb-6">
              <div className="flex items-start gap-3 mb-4">
                <ShieldAlert className="h-5 w-5 text-primary shrink-0 mt-0.5" aria-hidden />
                <div>
                  <p className="font-bold text-heading">{t('qr.showOnce')}</p>
                  <p className="text-sm text-muted-fg">{t('qr.showOnceHint')}</p>
                </div>
              </div>

              <div className="flex flex-col items-center gap-5">
                <img
                  src={reveal.imageDataUri}
                  alt="QR code"
                  className="h-56 w-56 rounded-lg bg-white p-2 shadow-card"
                />

                <div className="w-full space-y-2">
                  <label className="text-xs font-semibold text-muted-fg">{t('qr.publicUrl')}</label>
                  <div className="flex gap-2">
                    <code className="flex-1 min-w-0 truncate rounded-lg border border-border bg-surface px-3 py-2.5 text-sm text-foreground">
                      {reveal.publicUrl}
                    </code>
                    <Button variant="secondary" onClick={() => handleCopy(reveal.publicUrl)}>
                      {copied ? <Check className="h-4 w-4" aria-hidden /> : <Link2 className="h-4 w-4" aria-hidden />}
                      {copied ? t('common.copied') : t('qr.copyLink')}
                    </Button>
                  </div>
                </div>

                <div className="flex flex-wrap justify-center gap-2 w-full">
                  <Button variant="secondary" onClick={() => downloadPng(reveal.imageDataUri, filenameFor(vehicle.data?.licensePlate))}>
                    <Download className="h-4 w-4" aria-hidden />
                    {t('qr.downloadPng')}
                  </Button>
                  <Button variant="secondary" onClick={() => printQr(reveal.imageDataUri)}>
                    <Printer className="h-4 w-4" aria-hidden />
                    {t('qr.print')}
                  </Button>
                </div>

                <Button
                  className="w-full sm:w-auto"
                  onClick={() => {
                    setIssued(null)
                    setSuccess(t('qr.generateSuccess'))
                  }}
                >
                  {t('qr.savedIt')}
                </Button>
              </div>
            </div>
          )}

          {/* Active status / controls */}
          {!reveal && (
            <div className="mb-6">
              {current.isPending ? (
                <div className="space-y-3">
                  <div className="h-5 w-1/3 rounded bg-muted animate-pulse" />
                  <div className="h-9 w-2/3 rounded bg-muted animate-pulse" />
                </div>
              ) : noActiveQr ? (
                <EmptyState
                  icon={<QrCode className="h-7 w-7" aria-hidden />}
                  title={t('qr.noActive')}
                  hint={t('qr.noActiveHint')}
                  action={
                    <Button onClick={() => issueMutation.mutate()} loading={issueMutation.isPending}>
                      <QrCode className="h-4 w-4" aria-hidden />
                      {t('qr.generate')}
                    </Button>
                  }
                />
              ) : current.isError ? (
                <EmptyState icon={<QrCode className="h-7 w-7" aria-hidden />} title={t('errors.generic')} />
              ) : (
                currentStatus && (
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 rounded-xl border border-border bg-surface p-5">
                    <div className="flex items-center gap-4">
                      <div className="h-12 w-12 rounded-xl bg-success-soft text-success flex items-center justify-center">
                        <QrCode className="h-6 w-6" aria-hidden />
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <Badge tone="success">{t('qr.active')}</Badge>
                        </div>
                        <p className="text-sm text-muted-fg mt-1">
                          {t('qr.activeSince')} {formatDate(currentStatus.activatedAt)}
                        </p>
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      <Button variant="secondary" onClick={() => setConfirm('regenerate')}>
                        <RefreshCw className="h-4 w-4" aria-hidden />
                        {t('qr.regenerate')}
                      </Button>
                      <Button variant="outline" onClick={() => setConfirm('deactivate')}>
                        <Power className="h-4 w-4" aria-hidden />
                        {t('qr.disable')}
                      </Button>
                    </div>
                  </div>
                )
              )}
            </div>
          )}

          {/* How-to */}
          <div className="rounded-xl bg-muted p-5 mb-6">
            <h3 className="text-sm font-bold text-heading mb-3">{t('qr.howToUse')}</h3>
            <ol className="list-decimal list-inside space-y-1.5 text-sm text-muted-fg">
              <li>{t('qr.howToUse1')}</li>
              <li>{t('qr.howToUse2')}</li>
              <li>{t('qr.howToUse3')}</li>
            </ol>
          </div>

          {/* History */}
          <div>
            <h3 className="text-base font-bold text-heading mb-3">{t('qr.history')}</h3>
            {history.isPending ? (
              <div className="h-24 rounded-lg bg-muted animate-pulse" />
            ) : !history.data || history.data.length === 0 ? (
              <p className="text-sm text-muted-fg">{t('qr.historyEmpty')}</p>
            ) : (
              <ul className="divide-y divide-border border border-border rounded-lg">
                {history.data.map((row) => (
                  <li key={row.id} className="flex items-center justify-between gap-3 p-3.5 text-sm">
                    <span className="text-muted-fg">{formatDate(row.activatedAt)}</span>
                    <Badge tone={row.active ? 'success' : 'neutral'}>
                      {row.active ? t('qr.active') : t('qr.disabled')}
                    </Badge>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </CardContent>
      </Card>

      <ConfirmDialog
        open={confirm === 'regenerate'}
        title={t('qr.regenerate')}
        description={t('qr.regenerateConfirm')}
        confirmLabel={t('qr.regenerate')}
        tone="primary"
        loading={busy}
        onConfirm={() => issueMutation.mutate()}
        onCancel={() => setConfirm(null)}
      />
      <ConfirmDialog
        open={confirm === 'deactivate'}
        title={t('qr.disable')}
        description={t('qr.deactivateConfirm')}
        confirmLabel={t('qr.disable')}
        tone="danger"
        loading={busy}
        onConfirm={() => deactivateMutation.mutate()}
        onCancel={() => setConfirm(null)}
      />
    </div>
  )
}

function downloadPng(dataUri: string, filename: string) {
  const a = document.createElement('a')
  a.href = dataUri
  a.download = filename
  a.click()
}

function printQr(dataUri: string) {
  const w = window.open('', '_blank', 'width=420,height=460')
  if (!w) return
  w.document.write(
    `<html><body style="margin:0;display:flex;align-items:center;justify-content:center;min-height:100vh;background:#fff"><img src="${dataUri}" style="width:80vw;max-width:320px"/></body></html>`,
  )
  w.document.close()
  w.focus()
  w.print()
}

function filenameFor(licensePlate?: string) {
  const base = (licensePlate || 'qrcode').replace(/[^a-zA-Z0-9-]/g, '').toUpperCase()
  return `carlink-${base}.png`
}

function formatDate(iso: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}
