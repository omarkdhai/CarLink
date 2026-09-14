import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery } from '@tanstack/react-query'
import { CarFront, Check, ShieldAlert, MessageCircle, Smartphone, Lock } from 'lucide-react'
import { Logo } from '@/components/layout/Logo'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Alert } from '@/components/ui/Alert'
import { useCarColor } from '@/hooks/useCarColor'
import { publicQrApi, publicQrQueryKeys } from '@/services/publicQrApi'
import { toApiError } from '@/services/errors'
import { http } from '@/services/apiClient'
import type { ContactChannel, ReportReason } from '@/types'

const REASONS = ['BLOCKING', 'LIGHTS', 'OPEN_WINDOW', 'PARKING', 'OTHER'] as const
type ReasonKey = (typeof REASONS)[number]

const REPORT_REASONS: ReportReason[] = ['SPAM', 'ABUSE', 'OTHER']

/**
 * Core product: the mobile-first public page behind a scanned QR (/c/:token).
 * Visitor picks a channel + message to reach the owner; nothing personal is
 * shared and the phone number is never revealed.
 */
export default function PublicContactPage() {
  const { t } = useTranslation()
  const { token = '' } = useParams<{ token: string }>()

  const view = useQuery({
    queryKey: publicQrQueryKeys.view(token),
    queryFn: () => publicQrApi.view(token),
    enabled: Boolean(token),
    retry: false,
  })

  const [channel, setChannel] = useState<ContactChannel>('WHATSAPP')
  const [message, setMessage] = useState('')
  const [selectedReason, setSelectedReason] = useState<ReasonKey | null>(null)
  const [fieldError, setFieldError] = useState<string | null>(null)

  const [done, setDone] = useState<{ conversationId: string; method: ContactChannel } | null>(null)

  // Report flow
  const [showReport, setShowReport] = useState(false)
  const [reportReason, setReportReason] = useState<ReportReason>('SPAM')
  const [reportDetails, setReportDetails] = useState('')
  const [reportDone, setReportDone] = useState(false)

  const contactMutation = useMutation({
    mutationFn: () => publicQrApi.submit(token, { channel, message: message.trim() }),
    onSuccess: (data) => setDone({ conversationId: data.conversationId, method: channel }),
    onError: (err) => setFieldError(http.humanizeError(err)),
  })

  const reportMutation = useMutation({
    mutationFn: () =>
      publicQrApi.report(token, {
        conversationId: done!.conversationId,
        reason: reportReason,
        details: reportDetails.trim() || undefined,
      }),
    onSuccess: () => setReportDone(true),
    onError: (err) => setFieldError(http.humanizeError(err)),
  })

  const vehicle = view.data?.vehicle
  const color = useCarColor(vehicle?.color ?? null)

  function pickReason(reason: ReasonKey) {
    setSelectedReason((prev) => (prev === reason ? null : reason))
    setMessage(t(`contact.reasons.${reason}`))
  }

  function handleSubmit() {
    setFieldError(null)
    if (!message.trim()) {
      setFieldError(t('contact.messageRequired'))
      return
    }
    contactMutation.mutate()
  }

  function resetForm() {
    setDone(null)
    setMessage('')
    setSelectedReason(null)
    setFieldError(null)
    setShowReport(false)
    setReportDone(false)
    setReportDetails('')
  }

  // ---- Loading ----
  if (view.isPending) {
    return (
      <Shell>
        <div className="bg-surface rounded-2xl border border-border shadow-card p-6 space-y-4">
          <div className="h-9 w-9 rounded-xl bg-primary/20 animate-pulse" />
          <div className="h-6 w-2/3 rounded bg-muted animate-pulse" />
          <div className="h-4 w-1/2 rounded bg-muted animate-pulse" />
          <div className="h-24 rounded-lg bg-muted animate-pulse" />
          <div className="h-12 rounded-lg bg-muted animate-pulse" />
        </div>
      </Shell>
    )
  }

  // ---- Not active / not found ----
  if (view.isError) {
    const notFound = toApiError(view.error).status === 404
    return (
      <Shell>
        <Card>
          <Logo link={false} className="justify-center" />
          <div className="mt-6 text-center">
            <div className="mx-auto h-14 w-14 rounded-full bg-warning-soft text-warning flex items-center justify-center mb-4">
              <ShieldAlert className="h-7 w-7" aria-hidden />
            </div>
            <h1 className="text-xl font-bold text-heading">{t('contact.contactNotActive')}</h1>
            <p className="text-sm text-muted-fg mt-2">{t(notFound ? 'contact.contactNotActiveHint' : 'errors.generic')}</p>
          </div>
        </Card>
      </Shell>
    )
  }

  // ---- Success ----
  if (done) {
    return (
      <Shell>
        <Card>
          <Logo link={false} className="justify-center" />
          <div className="mt-6 text-center">
            <div className="mx-auto h-16 w-16 rounded-full bg-success-soft text-success flex items-center justify-center mb-4">
              <Check className="h-8 w-8" aria-hidden />
            </div>
            <h1 className="text-xl font-bold text-heading">{t('contact.contactSuccess')}</h1>
            <p className="text-sm text-muted-fg mt-2">
              {t('contact.contactSuccessHint', { method: t(`contact.${done.method.toLowerCase()}`) })}
            </p>
            <Button className="mt-6 w-full" onClick={resetForm}>
              {t('contact.sendAnother')}
            </Button>
            <button
              onClick={() => {
                setShowReport(true)
                setReportDone(false)
              }}
              className="mt-4 text-xs text-muted-fg underline underline-offset-2 hover:text-foreground"
            >
              {t('contact.reportTitle')}
            </button>
          </div>
        </Card>
        {showReport && <ReportPanel {...reportPanelProps()} />}
      </Shell>
    )
  }

  // ---- Form ----
  return (
    <Shell>
      <Card>
        <Logo link={false} className="justify-center" />

        {/* Vehicle summary */}
        <div className="mt-6 flex items-center gap-4 rounded-xl border border-border bg-surface p-4">
          <div
            className="h-12 w-12 shrink-0 rounded-xl flex items-center justify-center"
            style={{ backgroundColor: color.soft, color: color.main }}
          >
            <CarFront className="h-6 w-6" aria-hidden />
          </div>
          <div className="min-w-0">
            <p className="font-bold text-heading truncate">{vehicle?.nickname || t('contact.publicTitle')}</p>
            <p className="text-sm text-muted-fg truncate">{vehicleLabel(vehicle)}</p>
          </div>
        </div>

        {fieldError && (
          <div className="mt-4">
            <Alert tone="error">{fieldError}</Alert>
          </div>
        )}

        <div className="mt-5">
          <h2 className="text-sm font-bold text-heading mb-2">{t('contact.reason')}</h2>
          <div className="flex flex-wrap gap-2">
            {REASONS.map((r) => (
              <button
                key={r}
                type="button"
                aria-pressed={selectedReason === r}
                onClick={() => pickReason(r)}
                className={
                  'px-3 h-9 rounded-full border text-sm font-medium transition-colors touch-target ' +
                  (selectedReason === r
                    ? 'border-primary bg-primary-soft text-primary'
                    : 'border-border text-muted-fg hover:border-primary hover:text-primary')
                }
              >
                {t(`contact.reasons.${r}`)}
              </button>
            ))}
          </div>
        </div>

        <div className="mt-4">
          <label className="block text-sm font-bold text-heading mb-2" htmlFor="pub-msg">
            {t('contact.message')}
          </label>
          <Textarea
            id="pub-msg"
            value={message}
            onChange={(e) => {
              setMessage(e.target.value)
              if (selectedReason && e.target.value !== t(`contact.reasons.${selectedReason}`)) {
                setSelectedReason(null)
              }
            }}
            placeholder={t('contact.messagePlaceholder')}
            maxLength={500}
            rows={3}
            aria-invalid={Boolean(fieldError)}
          />
        </div>

        <div className="mt-5">
          <h2 className="text-sm font-bold text-heading mb-2">{t('contact.method')}</h2>
          <div className="grid grid-cols-2 gap-3">
            <ChannelButton
              active={channel === 'WHATSAPP'}
              onClick={() => setChannel('WHATSAPP')}
              label={t('contact.whatsapp')}
              icon={<MessageCircle className="h-5 w-5" aria-hidden />}
              className="border-whatsapp/60 text-whatsapp hover:bg-whatsapp/5"
              activeClass="bg-whatsapp text-white border-whatsapp"
            />
            <ChannelButton
              active={channel === 'SMS'}
              onClick={() => setChannel('SMS')}
              label={t('contact.sms')}
              icon={<Smartphone className="h-5 w-5" aria-hidden />}
              className="border-sms/60 text-sms hover:bg-sms/5"
              activeClass="bg-sms text-white border-sms"
            />
          </div>
        </div>

        <Button
          className="w-full mt-5"
          variant={channel === 'WHATSAPP' ? 'whatsapp' : 'sms'}
          loading={contactMutation.isPending}
          onClick={handleSubmit}
        >
          {t('contact.sendContact')}
        </Button>

        <p className="flex items-center justify-center gap-1.5 text-xs text-muted-fg mt-4">
          <Lock className="h-3.5 w-3.5" aria-hidden />
          {t('contact.privacyNote')}
        </p>
      </Card>

      {showReport && <ReportPanel {...reportPanelProps()} />}
    </Shell>
  )

  function reportPanelProps() {
    return {
      reason: reportReason,
      details: reportDetails,
      loading: reportMutation.isPending,
      done: reportDone,
      onChangeReason: setReportReason,
      onChangeDetails: setReportDetails,
      onSubmit: () => reportMutation.mutate(),
      onClose: () => setShowReport(false),
      error: fieldError,
    }
  }
}

/* ---------------------------------- bits ---------------------------------- */

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-dvh bg-gradient-to-b from-primary-soft/60 to-background flex items-center justify-center p-4">
      <div className="w-full max-w-md">{children}</div>
    </div>
  )
}

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-surface rounded-2xl border border-border shadow-card p-6 sm:p-7">{children}</div>
  )
}

function ChannelButton({
  active,
  onClick,
  label,
  icon,
  className,
  activeClass,
}: {
  active: boolean
  onClick: () => void
  label: string
  icon: React.ReactNode
  className: string
  activeClass: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={
        'flex items-center justify-center gap-2 h-12 rounded-xl border-2 font-semibold text-sm transition-all touch-target ' +
        (active ? activeClass : className)
      }
    >
      {icon}
      {label}
    </button>
  )
}

function ReportPanel({
  reason,
  details,
  loading,
  done,
  error,
  onChangeReason,
  onChangeDetails,
  onSubmit,
  onClose,
}: {
  reason: ReportReason
  details: string
  loading: boolean
  done: boolean
  error: string | null
  onChangeReason: (r: ReportReason) => void
  onChangeDetails: (d: string) => void
  onSubmit: () => void
  onClose: () => void
}) {
  const { t } = useTranslation()

  return (
    <div className="mt-4 rounded-xl border border-border bg-surface p-5">
      <div className="flex items-start gap-3">
        <div className="h-9 w-9 shrink-0 rounded-full bg-destructive-soft text-destructive flex items-center justify-center">
          <ShieldAlert className="h-4 w-4" aria-hidden />
        </div>
        <div className="flex-1 min-w-0">
          <h3 className="text-sm font-bold text-heading">{t('contact.reportTitle')}</h3>
          <p className="text-xs text-muted-fg mt-0.5">{t('contact.reportHint')}</p>
        </div>
      </div>

      {done ? (
        <div className="mt-4 rounded-lg bg-success-soft text-success px-4 py-3 text-sm font-medium">
          {t('contact.reportSuccess')}
        </div>
      ) : (
        <>
          {error && (
            <div className="mt-4">
              <Alert tone="error">{error}</Alert>
            </div>
          )}
          <p className="text-xs font-bold text-heading mt-4 mb-2">{t('contact.reportReasonLabel')}</p>
          <div className="grid grid-cols-3 gap-2">
            {REPORT_REASONS.map((r) => (
              <button
                key={r}
                type="button"
                aria-pressed={reason === r}
                onClick={() => onChangeReason(r)}
                className={
                  'px-2 h-10 rounded-lg border text-xs font-medium transition-colors touch-target ' +
                  (reason === r
                    ? 'border-destructive bg-destructive-soft text-destructive'
                    : 'border-border text-muted-fg hover:border-destructive')
                }
              >
                {t(`contact.reportReasons.${r}`)}
              </button>
            ))}
          </div>

          <label className="block text-xs font-bold text-heading mt-4 mb-2" htmlFor="report-details">
            {t('contact.reportDetails')}
          </label>
          <Textarea
            id="report-details"
            value={details}
            onChange={(e) => onChangeDetails(e.target.value)}
            placeholder={t('contact.reportDetailsPlaceholder')}
            rows={3}
            maxLength={1000}
          />

          <div className="flex justify-end gap-2 mt-4">
            <Button variant="ghost" onClick={onClose} disabled={loading}>
              {t('common.cancel')}
            </Button>
            <Button variant="danger" onClick={onSubmit} loading={loading}>
              {t('contact.reportSubmit')}
            </Button>
          </div>
        </>
      )}
    </div>
  )
}

function vehicleLabel(vehicle: { nickname: string | null; brand: string | null; model: string | null; color: string | null } | undefined) {
  if (!vehicle) return ''
  const brandModel = [vehicle.brand, vehicle.model].filter(Boolean).join(' ')
  return [brandModel, vehicle.color].filter(Boolean).join(' · ')
}
