import { useEffect, useState, type ReactNode } from 'react'
import { useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery } from '@tanstack/react-query'
import { AlertTriangle, AppWindow, BatteryCharging, CarFront, Check, Clock, DoorOpen, Flag, Lightbulb, Loader2, ShieldAlert, Smartphone, Lock } from 'lucide-react'
import { Logo } from '@/components/layout/Logo'
import { Button } from '@/components/ui/Button'
import { Textarea } from '@/components/ui/Textarea'
import { Alert } from '@/components/ui/Alert'
import { useCarColor } from '@/hooks/useCarColor'
import { publicQrApi, publicQrQueryKeys } from '@/services/publicQrApi'
import { toApiError } from '@/services/errors'
import { http } from '@/services/apiClient'
import StickerActivation from '@/features/stickers/StickerActivation'
import type { AlertDeliveryStatus, ReportReason } from '@/types'
import { SuccessMark } from '@/components/shared/SuccessMark'
import { useCaptcha } from '@/components/shared/useCaptcha'
import { fitNote } from '@/lib/sms'

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

/**
 * Core product: the mobile-first public page behind a scanned QR (/c/:token).
 * Visitor picks a reason + note; the owner gets it by SMS (and WhatsApp when
 * enabled). Nothing personal is
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

  const [message, setMessage] = useState('')
  const [selectedReason, setSelectedReason] = useState<ReasonKey | null>(null)
  const [fieldError, setFieldError] = useState<string | null>(null)

  const [done, setDone] = useState<{ conversationId: string; method: string; status: AlertDeliveryStatus } | null>(null)
  // Stop waiting for the operator's confirmation after this long.
  const [confirmTimedOut, setConfirmTimedOut] = useState(false)
  // ...and stop checking quietly in the background after this long.
  const [confirmGaveUp, setConfirmGaveUp] = useState(false)

  // Every alert goes out on all the channels the backend lists (SMS, plus
  // WhatsApp when configured): the visitor does not choose.
  const channels = view.data?.channels ?? []
  const methodLabel = channels.includes('WHATSAPP')
    ? t('contact.smsAndWhatsapp')
    : t('contact.sms')

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
        channel: 'SMS',
        message: message.trim(),
        reason: selectedReason!,
      }, captcha.headers()),
    onSettled: () => captcha.reset(),
    onSuccess: (data) => {
      setConfirmTimedOut(false)
      setConfirmGaveUp(false)
      setDone({ conversationId: data.conversationId, method: methodLabel, status: data.status ?? 'SENT' })
    },
    onError: (err) => setFieldError(http.humanizeError(err)),
  })

  // Accepted by the SMS provider is not "received": ask for the operator's
  // delivery report for a little while before telling the visitor it worked.
  const waitingForConfirmation = done?.status === 'SENT' && !confirmTimedOut
  const delivery = useQuery({
    queryKey: publicQrQueryKeys.delivery(done?.conversationId ?? ''),
    queryFn: () => publicQrApi.deliveryStatus(token, done!.conversationId),
    // Operator reports take from ~1 s (rejections) to a few minutes (delivered):
    // ask every 2 s while the spinner shows, then every 5 s behind the
    // "not confirmed yet" screen, which switches to delivered if it arrives.
    enabled: done?.status === 'SENT' && !confirmGaveUp,
    refetchInterval: (query) =>
      query.state.data?.status === 'SENT' || !query.state.data ? (confirmTimedOut ? 5000 : 2000) : false,
    retry: false,
  })
  useEffect(() => {
    if (!waitingForConfirmation) return
    const timer = window.setTimeout(() => setConfirmTimedOut(true), CONFIRM_TIMEOUT_MS)
    return () => window.clearTimeout(timer)
  }, [waitingForConfirmation])
  useEffect(() => {
    if (done?.status !== 'SENT') return
    const timer = window.setTimeout(() => setConfirmGaveUp(true), CONFIRM_BACKGROUND_MS)
    return () => window.clearTimeout(timer)
  }, [done])
  const deliveryStatus: AlertDeliveryStatus | undefined =
    done?.status === 'SENT' ? (delivery.data?.status ?? 'SENT') : done?.status

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

  // How much of the note fits in the owner's SMS (always Derja, 70 characters
  // in all); the full note is always stored and shown in the owner's dashboard.
  const noteLimit = selectedReason ? view.data?.smsNoteLimits?.[selectedReason] : undefined
  const noteFit = channels.includes('SMS') && noteLimit !== undefined ? fitNote(message, noteLimit) : null

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
    if (channels.length === 0) {
      setFieldError(t('errors.generic'))
      return
    }
    contactMutation.mutate()
  }

  /** Back to the form with the reason and note kept, so the visitor can change the note. */
  function editNote() {
    setDone(null)
    setConfirmTimedOut(false)
    setConfirmGaveUp(false)
    setFieldError(null)
    setShowReport(false)
    setReportDone(false)
  }

  function resetForm() {
    setDone(null)
    setConfirmTimedOut(false)
    setConfirmGaveUp(false)
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
            <DeliveryOutcome
              status={deliveryStatus ?? 'SENT'}
              waiting={waitingForConfirmation && deliveryStatus === 'SENT'}
              method={done.method}
            />
            {deliveryStatus === 'REFUSED' ? (
              <Button className="mt-6 w-full" onClick={editNote}>
                {t('contact.delivery.changeNote')}
              </Button>
            ) : !(waitingForConfirmation && deliveryStatus === 'SENT') && (
              <Button className="mt-6 w-full" onClick={resetForm}>
                {deliveryStatus === 'FAILED' ? t('contact.delivery.tryAgain') : t('contact.sendAnother')}
              </Button>
            )}
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
            aria-describedby="pub-msg-sms"
          />
          <div id="pub-msg-sms" aria-live="polite">
            {noteFit ? (
              <div className="mt-1.5 flex items-start justify-between gap-3 text-xs">
                <span className={noteFit.over ? 'font-semibold text-destructive' : 'text-muted-fg'}>
                  {noteFit.over
                    ? t('contact.smsLimit.over')
                    : t('contact.smsLimit.left', { count: noteFit.max - noteFit.used })}
                </span>
                <span className={'shrink-0 tabular-nums ' + (noteFit.over ? 'font-semibold text-destructive' : 'text-muted-fg')}>
                  {noteFit.used}/{noteFit.max}
                </span>
              </div>
            ) : (
              <p className="mt-1.5 text-end text-xs text-muted-fg tabular-nums">{message.length}/500</p>
            )}
            {channels.includes('SMS') && (
              <p className="mt-2 rounded-lg bg-muted/60 px-3 py-2 text-xs leading-relaxed text-muted-fg">
                {selectedReason ? t('contact.smsLimit.hint') : t('contact.smsLimit.pickReason')}
              </p>
            )}
          </div>
        </div>

        {/* How the owner is reached: every listed channel, no choice to make */}
        {channels.length > 0 && (
          <p className="mt-4 flex items-center gap-2 rounded-xl bg-primary-soft/60 px-4 py-3 text-sm text-foreground">
            <Smartphone className="h-4 w-4 shrink-0 text-primary" aria-hidden />
            {t('contact.deliveredBy', { method: methodLabel })}
          </p>
        )}

        {captcha.widget}
        <Button
          size="lg"
          className="w-full mt-5"
          loading={contactMutation.isPending}
          disabled={channels.length === 0 || (captcha.required && !captcha.token)}
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

/** How long the visitor waits for the operator's delivery report (rejections come back in ~1 s). */
const CONFIRM_TIMEOUT_MS = 20_000
/** Keep checking quietly behind the "not confirmed yet" screen for this long. */
const CONFIRM_BACKGROUND_MS = 180_000

/**
 * The outcome shown after sending: delivered (confirmed by the operator), still
 * being confirmed, accepted but not confirmed yet (phone off?), or failed.
 */
function DeliveryOutcome({ status, waiting, method }: { status: AlertDeliveryStatus; waiting: boolean; method: string }) {
  const { t } = useTranslation()
  if (waiting) {
    return (
      <div role="status" aria-live="polite">
        <span className="mx-auto mb-4 grid h-16 w-16 place-items-center rounded-full bg-primary-soft text-primary">
          <Loader2 className="h-8 w-8 animate-spin" aria-hidden />
        </span>
        <h1 className="text-xl font-bold text-heading">{t('contact.delivery.checkingTitle')}</h1>
        <p className="text-sm text-muted-fg mt-2">{t('contact.delivery.checkingHint')}</p>
      </div>
    )
  }
  if (status === 'REFUSED') {
    return (
      <div role="alert">
        <span className="mx-auto mb-4 grid h-16 w-16 place-items-center rounded-full bg-warning-soft text-warning">
          <ShieldAlert className="h-8 w-8" aria-hidden />
        </span>
        <h1 className="text-xl font-bold text-heading">{t('contact.delivery.refusedTitle')}</h1>
        <p className="text-sm text-muted-fg mt-2 leading-relaxed">{t('contact.delivery.refusedHint')}</p>
      </div>
    )
  }
  if (status === 'FAILED') {
    return (
      <div role="alert">
        <span className="mx-auto mb-4 grid h-16 w-16 place-items-center rounded-full bg-destructive-soft text-destructive">
          <AlertTriangle className="h-8 w-8" aria-hidden />
        </span>
        <h1 className="text-xl font-bold text-heading">{t('contact.delivery.failedTitle')}</h1>
        <p className="text-sm text-muted-fg mt-2 leading-relaxed">{t('contact.delivery.failedHint')}</p>
      </div>
    )
  }
  if (status === 'SENT') {
    return (
      <div role="status">
        <span className="mx-auto mb-4 grid h-16 w-16 place-items-center rounded-full bg-warning-soft text-warning">
          <Clock className="h-8 w-8" aria-hidden />
        </span>
        <h1 className="text-xl font-bold text-heading">{t('contact.delivery.unconfirmedTitle')}</h1>
        <p className="text-sm text-muted-fg mt-2 leading-relaxed">{t('contact.delivery.unconfirmedHint')}</p>
      </div>
    )
  }
  return (
    <div role="status">
      <SuccessMark className="mb-4" />
      <h1 className="text-xl font-bold text-heading">{t('contact.delivery.deliveredTitle')}</h1>
      <p className="text-sm text-muted-fg mt-2">{t('contact.delivery.deliveredHint', { method })}</p>
    </div>
  )
}
