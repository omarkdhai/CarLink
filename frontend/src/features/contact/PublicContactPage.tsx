import { useState, type ReactNode } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle, AppWindow, BatteryCharging, CarFront, Check, DoorOpen, Flag, Lightbulb, ShieldAlert, Smartphone, Lock } from 'lucide-react'
import { Logo } from '@/components/layout/Logo'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Alert } from '@/components/ui/Alert'
import { useCarColor } from '@/hooks/useCarColor'
import { publicQrApi, publicQrQueryKeys } from '@/services/publicQrApi'
import { toApiError } from '@/services/errors'
import { http } from '@/services/apiClient'
import StickerActivation from '@/features/stickers/StickerActivation'
import type { ContactChannel, ReportReason } from '@/types'
import { SuccessMark } from '@/components/shared/SuccessMark'
import { useCaptcha } from '@/components/shared/useCaptcha'

const REASONS = ['BLOCKING', 'LIGHTS', 'LEFT_OPEN', 'HIT_CAR', 'WINDOWS_OPEN', 'EV_CHARGE'] as const
type ReasonKey = (typeof REASONS)[number]

const REASON_ICON: Record<ReasonKey, typeof CarFront> = {
  BLOCKING: CarFront,
  LIGHTS: Lightbulb,
  LEFT_OPEN: DoorOpen,
  HIT_CAR: AlertTriangle,
  WINDOWS_OPEN: AppWindow,
  EV_CHARGE: BatteryCharging,
}

const REPORT_REASONS: ReportReason[] = ['SPAM', 'ABUSE', 'OTHER']

const CHANNEL_UI: Record<
  ContactChannel,
  {
    labelKey: string
    icon: React.ReactNode
    className: string
    activeClass: string
    variant: 'sms'
  }
> = {
  SMS: {
    labelKey: 'contact.sms',
    icon: <Smartphone className="h-5 w-5" aria-hidden />,
    className: 'border-sms/60 text-sms hover:bg-sms/5',
    activeClass: 'bg-sms text-white border-sms',
    variant: 'sms',
  },
}

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

  const [channel, setChannel] = useState<ContactChannel | null>(null)
  const [message, setMessage] = useState('')
  const [selectedReason, setSelectedReason] = useState<ReasonKey | null>(null)
  const [fieldError, setFieldError] = useState<string | null>(null)

  const [done, setDone] = useState<{ conversationId: string; method: ContactChannel } | null>(null)

  // The backend advertises only the channels it will accept (SMS today).
  // Never hardcode a channel: the submit endpoint rejects anything else.
  const channels = view.data?.channels ?? []
  const activeChannel: ContactChannel | undefined = channel ?? channels[0]

  // Report flow
  const [showReport, setShowReport] = useState(false)
  const [reportReason, setReportReason] = useState<ReportReason>('SPAM')
  const [reportDetails, setReportDetails] = useState('')
  const [reportDone, setReportDone] = useState(false)

  const captcha = useCaptcha()
  const reportCaptcha = useCaptcha()
  const contactMutation = useMutation({
    mutationFn: () =>
      publicQrApi.submit(token, {
        channel: activeChannel!,
        message: message.trim(),
        reason: selectedReason!,
      }, captcha.headers()),
    onSettled: () => captcha.reset(),
    onSuccess: (data) => setDone({ conversationId: data.conversationId, method: activeChannel! }),
    onError: (err) => setFieldError(http.humanizeError(err)),
  })

  const reportMutation = useMutation({
    mutationFn: () =>
      publicQrApi.report({
        conversationId: done!.conversationId,
        reason: reportReason,
        details: reportDetails.trim() || undefined,
      }, reportCaptcha.headers()),
    onSettled: () => reportCaptcha.reset(),
    onSuccess: () => setReportDone(true),
    onError: (err) => setFieldError(http.humanizeError(err)),
  })

  const vehicle = view.data?.vehicle
  const color = useCarColor(vehicle?.color ?? null)

  function pickReason(reason: ReasonKey) {
    setSelectedReason((prev) => (prev === reason ? null : reason))
  }

  function handleSubmit() {
    setFieldError(null)
    if (!selectedReason) {
      setFieldError(t('contact.reasonRequired'))
      return
    }
    if (!activeChannel) {
      setFieldError(t('errors.generic'))
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

  // ---- Virgin / deactivated sticker → activation instead of a contact form ----
  if (view.data && view.data.state !== 'BOUND') {
    return <StickerActivation token={token} />
  }

  // ---- Success ----
  if (done) {
    return (
      <Shell>
        <Card>
          <Logo link={false} className="justify-center" />
          <div className="mt-6 text-center">
            <SuccessMark className="mb-4" />
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

        <div className="mt-6 text-center">
          <h1 className="text-2xl font-bold text-heading leading-tight">{t('contact.scanTitle')}</h1>
          <p className="mt-2 text-sm leading-relaxed text-muted-fg">{t('contact.scanSubtitle')}</p>
        </div>

        {/* Vehicle summary */}
        <div className="mt-6 flex items-center gap-4 rounded-xl border border-border bg-muted/30 p-4">
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
          <div className="mt-5">
            <Alert tone="error">{fieldError}</Alert>
          </div>
        )}

        {/* Step 1 — reason */}
        <fieldset className="mt-6">
          <legend className="mb-3 flex items-center gap-2 text-base font-bold text-heading">
            <StepNumber n={1} />
            {t('contact.reason')}
          </legend>
          <div role="radiogroup" aria-label={t('contact.reason')} className="grid gap-2 sm:grid-cols-2">
            {REASONS.map((r) => {
              const Icon = REASON_ICON[r]
              const active = selectedReason === r
              return (
                <button
                  key={r}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  onClick={() => pickReason(r)}
                  className={
                    'flex min-h-14 items-center gap-3 rounded-xl border-2 px-3 py-2.5 text-start text-sm font-semibold leading-snug transition-colors ' +
                    (active ? 'border-primary bg-primary-soft text-primary' : 'border-border text-foreground hover:border-primary/40')
                  }
                >
                  <span
                    className={'grid h-9 w-9 shrink-0 place-items-center rounded-lg ' + (active ? 'bg-primary text-white' : 'bg-muted text-muted-fg')}
                    aria-hidden
                  >
                    {active ? <Check className="h-5 w-5" /> : <Icon className="h-5 w-5" />}
                  </span>
                  <span className="min-w-0">{t(`contact.reasons.${r}`)}</span>
                </button>
              )
            })}
          </div>
        </fieldset>

        {/* Step 2 — optional note */}
        <div className="mt-6">
          <label className="mb-3 flex items-center gap-2 text-base font-bold text-heading" htmlFor="pub-msg">
            <StepNumber n={2} />
            {t('contact.note')}
          </label>
          <Textarea
            id="pub-msg"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder={t('contact.notePlaceholder')}
            maxLength={500}
            rows={3}
          />
          <p className="mt-1.5 text-end text-xs text-muted-fg tabular-nums">{message.length}/500</p>
        </div>

        {/* Delivery: a choice only when the backend offers several channels */}
        {channels.length > 1 ? (
          <div className="mt-5">
            <h2 className="text-sm font-bold text-heading mb-2">{t('contact.method')}</h2>
            <div className="grid grid-cols-2 gap-3">
              {channels.map((c) => (
                <ChannelButton
                  key={c}
                  active={activeChannel === c}
                  onClick={() => setChannel(c)}
                  label={t(CHANNEL_UI[c].labelKey)}
                  icon={CHANNEL_UI[c].icon}
                  className={CHANNEL_UI[c].className}
                  activeClass={CHANNEL_UI[c].activeClass}
                />
              ))}
            </div>
          </div>
        ) : (
          activeChannel && (
            <p className="mt-4 flex items-center gap-2 rounded-xl bg-primary-soft/60 px-4 py-3 text-sm text-foreground">
              <Smartphone className="h-4 w-4 shrink-0 text-primary" aria-hidden />
              {t('contact.deliveredBy', { method: t(CHANNEL_UI[activeChannel].labelKey) })}
            </p>
          )
        )}

        {captcha.widget}
        <Button
          size="lg"
          className="w-full mt-5"
          loading={contactMutation.isPending}
          disabled={!activeChannel || (captcha.required && !captcha.token)}
          onClick={handleSubmit}
        >
          {t('contact.sendContact')}
        </Button>

        <p className="flex items-start justify-center gap-1.5 text-center text-xs leading-relaxed text-muted-fg mt-4">
          <Lock className="h-3.5 w-3.5 mt-0.5 shrink-0" aria-hidden />
          {t('contact.privacyNote')}
        </p>
      </Card>

      <div className="mt-4 text-center">
        <button
          type="button"
          onClick={() => {
            setShowReport(true)
            setReportDone(false)
          }}
          className="inline-flex min-h-11 items-center gap-1.5 px-3 text-sm text-muted-fg underline underline-offset-2 hover:text-foreground"
        >
          <Flag className="h-4 w-4" aria-hidden />
          {t('contact.reportTitle')}
        </button>
      </div>

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
      captchaWidget: reportCaptcha.widget,
      submitBlocked: reportCaptcha.required && !reportCaptcha.token,
      onClose: () => setShowReport(false),
      error: fieldError,
    }
  }
}

/* ---------------------------------- bits ---------------------------------- */

function StepNumber({ n }: { n: number }) {
  return (
    <span className="grid h-6 w-6 shrink-0 place-items-center rounded-full bg-primary text-xs font-bold text-white" aria-hidden>
      {n}
    </span>
  )
}

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
  captchaWidget,
  submitBlocked = false,
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
  /** Bot-protection widget (S-11), rendered above the submit button. */
  captchaWidget?: ReactNode
  submitBlocked?: boolean
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

          {captchaWidget}
          <div className="flex justify-end gap-2 mt-4">
            <Button variant="ghost" onClick={onClose} disabled={loading}>
              {t('common.cancel')}
            </Button>
            <Button variant="danger" onClick={onSubmit} loading={loading} disabled={submitBlocked}>
              {t('contact.reportSubmit')}
            </Button>
          </div>
        </>
      )}
    </div>
  )
}

function vehicleLabel(vehicle: { nickname: string | null; brand: string | null; model: string | null; color: string | null } | null | undefined) {
  if (!vehicle) return ''
  const brandModel = [vehicle.brand, vehicle.model].filter(Boolean).join(' ')
  return [brandModel, vehicle.color].filter(Boolean).join(' · ')
}
