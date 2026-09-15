import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  ArrowLeft,
  ArrowRight,
  Check,
  ChevronDown,
  Copy,
  ScanLine,
  ShieldCheck,
  Truck,
  X,
} from 'lucide-react'
import { Button, buttonClasses } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Textarea } from '@/components/ui/Textarea'
import { Alert } from '@/components/ui/Alert'
import { orderApi } from '@/services/orderApi'
import { http } from '@/services/apiClient'
import {
  orderDetailsSchema,
  PACKS,
  PACK_MAX_QTY,
  packIsPreselect,
  toFullMobile,
  TN_DIAL_CODE,
  type OrderDetailsValues,
} from '@/features/order/orderSchemas'
import { cn } from '@/lib/cn'
import { GOVERNORATES } from '@/types'
import type { Governorate, OrderResponse, StickerPackage } from '@/types'

/** Hard ceiling shared with OrderService (100 stickers per order). */
const MAX_TOTAL_STICKERS = 100

const DRAFT_KEY = 'carlink.order.draft'
const LAST_REF_KEY = 'carlink.order.lastReference'

interface OrderDraft {
  step: 1 | 2 | 3
  pkg: StickerPackage
  qty: number
  details: OrderDetailsValues | null
}

function loadDraft(): OrderDraft | null {
  try {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return null
    const d = JSON.parse(raw) as Partial<OrderDraft>
    if (d && packIsPreselect(d.pkg ?? null) && typeof d.qty === 'number' && d.qty > 0) {
      return { step: d.step ?? 1, pkg: d.pkg!, qty: d.qty, details: d.details ?? null }
    }
  } catch {
    /* corrupt draft — start fresh */
  }
  return null
}

function packPrice(pkg: StickerPackage) {
  return PACKS.find((p) => p.key === pkg)!
}

function packNum(key: StickerPackage) {
  return key === 'SINGLE' ? 1 : key === 'DOUBLE' ? 2 : 3
}

/**
 * "Get your sticker" funnel — 3-step guest checkout (package → details →
 * cash-on-delivery confirm) ending on a success screen with the order
 * reference and each sticker's QR. Pricing is mirrored locally for UX but
 * always re-computed server-side.
 */
export default function OrderPage() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const planParam = searchParams.get('plan')
  const preselect: StickerPackage | null = packIsPreselect(planParam) ? planParam : null

  const initial = loadDraft()
  const [step, setStep] = useState<1 | 2 | 3>(preselect ? 1 : (initial?.step ?? 1))
  const [pkg, setPkg] = useState<StickerPackage>(preselect ?? initial?.pkg ?? 'SINGLE')
  const [qty, setQty] = useState(preselect ? 1 : (initial?.qty ?? 1))
  const [details, setDetails] = useState<OrderDetailsValues | null>(initial?.details ?? null)

  // The full order response lives only in memory — never persisted (raw tokens).
  const [placed, setPlaced] = useState<OrderResponse | null>(null)
  const [lastReference, setLastReference] = useState<string | null>(() =>
    localStorage.getItem(LAST_REF_KEY),
  )
  const [submissionError, setSubmissionError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  // Draft persistence — cleared once the order is placed.
  useEffect(() => {
    if (placed) return
    const draft: OrderDraft = { step, pkg, qty, details }
    localStorage.setItem(DRAFT_KEY, JSON.stringify(draft))
  }, [step, pkg, qty, details, placed])

  // Recovery after a page refresh: token-free / PII-free summary only.
  const lastSummary = useQuery({
    queryKey: ['order', 'last', lastReference],
    queryFn: () => orderApi.getOrder(lastReference!),
    enabled: Boolean(lastReference && !placed),
    retry: false,
  })

  const pack = packPrice(pkg)
  const maxQty = Math.min(PACK_MAX_QTY, Math.floor(MAX_TOTAL_STICKERS / pack.stickers))
  const totalStickers = pack.stickers * qty
  const total = pack.price * qty

  const placeMutation = useMutation({
    mutationFn: () =>
      orderApi.createOrder({
        items: [{ stickerPackage: pkg, quantity: qty }],
        customerName: details!.customerName,
        mobile: toFullMobile(details!.mobile),
        email: details!.email,
        governorate: details!.governorate as Governorate,
        deliveryAddress: details!.deliveryAddress,
        deliveryNotes: details?.deliveryNotes?.trim() || undefined,
      }),
    onSuccess: (data) => {
      setPlaced(data)
      localStorage.removeItem(DRAFT_KEY)
      setLastReference(data.reference)
    },
    onError: (err) => setSubmissionError(http.humanizeError(err)),
  })

  async function copyReference() {
    try {
      await navigator.clipboard.writeText((placed?.reference ?? lastReference) || '')
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      /* clipboard unavailable */
    }
  }

  function resetOrder() {
    setPlaced(null)
    setStep(1)
    setPkg('SINGLE')
    setQty(1)
    setDetails(null)
    setSubmissionError(null)
    setCopied(false)
    localStorage.removeItem(DRAFT_KEY)
    localStorage.removeItem(LAST_REF_KEY)
    setLastReference(null)
  }

  // ---------- Success screen (in-memory order, raw tokens shown once) ----------
  if (placed) {
    return (
      <div className="max-w-2xl mx-auto w-full py-10 px-4 sm:px-6">
        <SuccessCard
          title={t('order.success')}
          hint={t('order.successHint')}
          reference={placed.reference}
          status={t('order.statusPlaced')}
          onCopy={copyReference}
          copied={copied}
        >
          <div className="mt-6">
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-bold text-heading">{t('order.yourStickers')}</h2>
              <span className="text-sm text-muted-fg tabular-nums">{placed.totalAmount} TND</span>
            </div>
            <ul className="grid gap-4 sm:grid-cols-2">
              {placed.stickers.map((st, i) => (
                <li
                  key={i}
                  className="rounded-xl border border-border bg-surface p-4 flex flex-col items-center text-center"
                >
                  <img
                    src={st.imageDataUri}
                    alt={`${t('order.label')} ${i + 1}`}
                    className="h-36 w-36 rounded-lg bg-white p-1.5 shadow-card"
                  />
                  <p className="mt-3 text-sm font-semibold text-heading">
                    {t('order.label')} {i + 1}
                  </p>
                  <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-fg">
                    <ScanLine className="h-3.5 w-3.5" aria-hidden />
                    {t('order.scanHint')}
                  </p>
                </li>
              ))}
            </ul>
            <p className="mt-4 flex items-start gap-2 text-xs text-muted-fg">
              <ShieldCheck className="h-4 w-4 text-primary shrink-0 mt-0.5" aria-hidden />
              {t('order.keepReference')}
            </p>
          </div>
        </SuccessCard>

        <div className="flex justify-center gap-3 mt-6">
          <Link to="/" className={buttonClasses({ variant: 'outline' })}>
            {t('order.goHome')}
          </Link>
          <Button onClick={resetOrder}>{t('order.orderAnother')}</Button>
        </div>
      </div>
    )
  }

  // ---------- Recovery banner (after refresh: no QR codes retrievable) ----------
  const showRecovery = Boolean(lastReference && !placed && lastSummary.data)

  return (
    <div className="max-w-2xl mx-auto w-full py-10 px-4 sm:px-6">
      {/* Main card */}
      <div className="rounded-2xl bg-surface shadow-card p-5 sm:p-8">
        {showRecovery && lastSummary.data && (
          <div className="rounded-xl border border-primary/30 bg-primary-soft/40 p-4 mb-8">
            <div className="flex items-start gap-3">
              <div className="h-10 w-10 shrink-0 rounded-lg bg-primary text-white flex items-center justify-center">
                <Check className="h-5 w-5" aria-hidden />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-bold text-heading">{t('order.lastOrder')}</p>
                <p className="text-sm text-muted-fg mt-0.5">
                  <span className="font-mono font-semibold text-foreground">{lastSummary.data.reference}</span>
                  {' · '}
                  {t('order.statusPlaced')}
                  {' · '}
                  {lastSummary.data.stickerCount} {t('order.unit')}
                  {' · '}
                  {lastSummary.data.totalAmount} TND
                </p>
              </div>
              <button
                type="button"
                onClick={() => {
                  localStorage.removeItem(LAST_REF_KEY)
                  setLastReference(null)
                }}
                className="text-muted-fg hover:text-foreground p-1"
                aria-label={t('common.close')}
              >
                <X className="h-4 w-4" aria-hidden />
              </button>
            </div>
          </div>
        )}

        <Stepper step={step} />

        {submissionError && (
          <div className="mb-4">
            <Alert tone="error">{submissionError}</Alert>
          </div>
        )}

        {/* ---------------------- Step 1: package ---------------------- */}
        {step === 1 && (
          <section>
            <header className="mb-6">
              <h1 className="text-2xl sm:text-3xl font-extrabold text-heading">{t('order.title')}</h1>
              <p className="text-muted-fg mt-1 text-sm">{t('order.subtitle')}</p>
            </header>

            <div className="space-y-3">
              {PACKS.map((p) => {
                const selected = pkg === p.key
                return (
                  <button
                    key={p.key}
                    type="button"
                    aria-pressed={selected}
                    onClick={() => {
                      setPkg(p.key)
                      setQty(1)
                    }}
                    className={cn(
                      'relative flex items-center gap-3 sm:gap-4 w-full rounded-xl border-2 p-4 text-start transition-all',
                      selected
                        ? 'border-primary bg-primary-soft shadow-card'
                        : 'border-border bg-background hover:border-primary/30',
                    )}
                  >
                    {/* Radio indicator */}
                    <span
                      className={cn(
                        'flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2 transition-colors',
                        selected ? 'border-primary bg-primary' : 'border-input-border',
                      )}
                      aria-hidden="true"
                    >
                      {selected && <Check className="h-3 w-3 text-white" />}
                    </span>

                    {/* Name + subtitle */}
                    <div className="flex-1 min-w-0">
                      <span className="flex items-center gap-2">
                        <h2 className="font-bold text-heading">{t(`landing.pricingPack${packNum(p.key)}Name`)}</h2>
                        {p.badge && (
                          <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">
                            {t(p.badge)}
                          </span>
                        )}
                      </span>
                      <p className="text-sm text-muted-fg mt-0.5">
                        {t(`landing.pricingPack${packNum(p.key)}Subtitle`)}
                      </p>
                    </div>

                    {/* Price */}
                    <div className="shrink-0 text-end">
                      <span className="text-xl sm:text-2xl font-extrabold text-heading tabular-nums leading-none">
                        {p.price}
                      </span>
                      <span className="block text-xs text-muted-fg mt-0.5">TND</span>
                    </div>
                  </button>
                )
              })}
            </div>

            {/* Quantity + total */}
            <div className="mt-6 rounded-xl border border-border bg-background p-4">
              <div className="flex items-center justify-between gap-4">
                <span className="text-sm font-semibold text-heading">{t('order.quantity')}</span>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="icon"
                    disabled={qty <= 1}
                    onClick={() => setQty((q) => Math.max(1, q - 1))}
                    aria-label={t('order.decrease')}
                  >
                    −
                  </Button>
                  <span className="w-10 sm:w-12 text-center text-lg font-bold text-heading tabular-nums">{qty}</span>
                  <Button
                    variant="outline"
                    size="icon"
                    disabled={qty >= maxQty}
                    onClick={() => setQty((q) => Math.min(maxQty, q + 1))}
                    aria-label={t('order.increase')}
                  >
                    +
                  </Button>
                </div>
              </div>
              <div className="mt-3 pt-3 border-t border-border">
                <p className="text-xs text-muted-fg mb-1">{totalStickers} {t('order.stickerCount')}</p>
                <div className="flex items-end justify-between">
                  <span className="text-sm text-muted-fg">{t('order.total')}</span>
                  <span className="text-xl font-bold text-heading tabular-nums">{total.toFixed(2)} TND</span>
                </div>
              </div>
            </div>

            {/* CTA */}
            <Button
              fullWidth
              size="lg"
              className="mt-6"
              onClick={() => setStep(2)}
            >
              {t('order.continue')} <ArrowRight className="h-4 w-4" aria-hidden />
            </Button>
          </section>
        )}

        {/* ---------------------- Step 2: details ---------------------- */}
        {step === 2 && (
          <DetailsForm
            initial={details}
            onBack={() => setStep(1)}
            onSubmit={(values) => {
              setDetails(values)
              setStep(3)
            }}
          />
        )}

        {/* ---------------------- Step 3: confirm ---------------------- */}
        {step === 3 && details && (
          <section>
            <header className="mb-6">
              <h1 className="text-2xl sm:text-3xl font-extrabold text-heading">{t('order.summary')}</h1>
              <p className="text-muted-fg mt-1 text-sm">{t('order.summaryHint')}</p>
            </header>

            {/* Delivery summary */}
            <div className="rounded-xl border border-border bg-background p-4">
              <div className="flex items-center justify-between mb-3">
                <span className="flex items-center gap-2 text-sm font-semibold text-heading">
                  <Truck className="h-4 w-4 text-primary" aria-hidden />
                  {t('order.cashOnDelivery')}
                </span>
                <button
                  type="button"
                  onClick={() => setStep(2)}
                  className="text-sm font-semibold text-primary hover:text-primary-hover"
                >
                  {t('order.editDelivery')}
                </button>
              </div>
              <p className="text-sm font-medium text-heading">{details.customerName}</p>
              <p className="text-sm text-muted-fg tabular-nums" dir="ltr">{toFullMobile(details.mobile)}</p>
              <p className="text-sm text-muted-fg">{details.email}</p>
              <p className="text-sm text-muted-fg">
                {details.deliveryAddress}
                {details.deliveryNotes?.trim() ? `, ${details.deliveryNotes.trim()}` : ''}
              </p>
              <p className="text-sm text-muted-fg">{t(`order.governorates.${details.governorate}`)}</p>
            </div>

            {/* Payment method */}
            <div className="mt-6">
              <h2 className="text-base font-bold text-heading mb-3">{t('order.paymentMethod')}</h2>
              <div className="space-y-3">
                {/* Cash on delivery — selected */}
                <div className="rounded-xl border-2 border-primary bg-primary-soft p-4 flex items-start gap-3">
                  <span className="mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-primary text-white">
                    <Check className="h-3 w-3" />
                  </span>
                  <div>
                    <p className="text-sm font-bold text-heading">{t('order.cashOnDelivery')}</p>
                    <p className="text-xs text-muted-fg mt-0.5">{t('order.codHint')}</p>
                  </div>
                </div>
                
              </div>
            </div>

            {/* Order total */}
            <div className="mt-6 pt-4 border-t border-border flex items-end justify-between">
              <div>
                <span className="text-sm font-semibold text-heading">{t('order.total')}</span>
                <p className="text-xs text-muted-fg">{t('order.deliveryIncluded')}</p>
              </div>
              <span className="text-xl font-bold text-heading tabular-nums">{total.toFixed(2)} TND</span>
            </div>

            {/* CTA */}
            <Button
              fullWidth
              size="lg"
              className="mt-6"
              loading={placeMutation.isPending}
              onClick={() => placeMutation.mutate()}
            >
              {t('order.confirmOrder')} — {total.toFixed(2)} TND
            </Button>

            {/* Trust */}
            <p className="mt-3 flex items-center justify-center gap-1.5 text-xs text-muted-fg">
              <ShieldCheck className="h-3.5 w-3.5 text-success" aria-hidden />
              {t('order.trustHint')}
            </p>
          </section>
        )}
      </div>
    </div>
  )
}

/* ------------------------------- subcomponents ------------------------------- */

function Stepper({ step }: { step: 1 | 2 | 3 }) {
  const { t } = useTranslation()
  const labels = [t('order.stepPackage'), t('order.stepDetails'), t('order.stepConfirm')]
  const steps = [1, 2, 3] as const
  return (
    <div className="mb-8" aria-label={t('order.stepsAria')}>
      <div className="flex items-center">
        {steps.map((n) => {
          const done = n < step
          const active = n === step
          return (
            <div key={n} className="flex items-center flex-1 last:flex-none">
              {/* Circle */}
              <div className="flex flex-col items-center shrink-0">
                <span
                  className={cn(
                    'flex h-9 w-9 items-center justify-center rounded-full text-sm font-bold transition-all',
                    done && 'bg-success text-white',
                    active && 'bg-primary text-white ring-4 ring-primary/20',
                    !done && !active && 'bg-muted text-muted-fg',
                  )}
                >
                  {done ? <Check className="h-4 w-4" /> : n}
                </span>
                <span
                  className={cn(
                    'mt-1.5 text-xs font-semibold text-center whitespace-nowrap',
                    active ? 'text-primary' : 'text-muted-fg',
                  )}
                >
                  {labels[n - 1]}
                </span>
              </div>
              {/* Connecting line */}
              {n < 3 && (
                <div
                  className={cn(
                    'flex-1 h-0.5 mx-2 rounded-full transition-colors mb-6',
                    n < step ? 'bg-success' : 'bg-border',
                  )}
                />
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}

function DetailsForm({
  initial,
  onBack,
  onSubmit,
}: {
  initial: OrderDetailsValues | null
  onBack: () => void
  onSubmit: (values: OrderDetailsValues) => void
}) {
  const { t } = useTranslation()
  const [notesOpen, setNotesOpen] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<OrderDetailsValues>({
    resolver: zodResolver(orderDetailsSchema),
    defaultValues: {
      customerName: initial?.customerName ?? '',
      mobile: initial?.mobile ? toFullMobile(initial.mobile).slice(TN_DIAL_CODE.length) : '',
      email: initial?.email ?? '',
      governorate: initial?.governorate ?? '',
      deliveryAddress: initial?.deliveryAddress ?? '',
      deliveryNotes: initial?.deliveryNotes ?? '',
    },
  })

  const labelCls = 'text-[11px] sm:text-xs font-semibold text-muted-fg uppercase tracking-wider'

  return (
    <section>
      <header className="mb-6">
        <h1 className="text-2xl sm:text-3xl font-extrabold text-heading">{t('order.details')}</h1>
        <p className="text-muted-fg mt-1 text-sm">{t('order.detailsHint')}</p>
      </header>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-6" noValidate>
        {/* Contact details */}
        <div>
          <h2 className="text-base font-bold text-heading mb-4">{t('order.contactDetails')}</h2>
          <div className="space-y-4">
            <div>
              <label className={labelCls}>{t('order.fullName')}</label>
              <Input
                autoComplete="name"
                placeholder={t('order.fullNamePlaceholder')}
                maxLength={100}
                error={Boolean(errors.customerName)}
                className="mt-1.5"
                {...register('customerName')}
              />
              {errors.customerName && (
                <p className="text-sm text-destructive mt-1" role="alert">{errors.customerName.message}</p>
              )}
            </div>

            <div className="grid sm:grid-cols-2 gap-4">
              <div>
                <label className={labelCls}>{t('order.mobile')}</label>
                <div
                  className={cn(
                    'flex items-center h-11 mt-1.5 rounded-lg border bg-surface transition-colors overflow-hidden',
                    errors.mobile ? 'border-destructive' : 'border-input-border hover:border-primary/50 focus-within:border-primary',
                  )}
                >
                  <span className="flex items-center h-full px-3.5 text-sm font-semibold select-none border-r bg-muted/40 text-foreground">
                    {TN_DIAL_CODE}
                  </span>
                  <input
                    type="tel"
                    inputMode="numeric"
                    autoComplete="tel-national"
                    placeholder="22 001 122"
                    maxLength={8}
                    aria-invalid={Boolean(errors.mobile)}
                    className="flex-1 h-full min-w-0 px-3.5 bg-transparent text-foreground placeholder:text-muted-fg focus:outline-none"
                    {...register('mobile', {
                      onChange: (e) => {
                        e.target.value = e.target.value.replace(/\D/g, '').slice(0, 8)
                      },
                    })}
                  />
                </div>
                {errors.mobile ? (
                  <p className="text-sm text-destructive mt-1" role="alert">{errors.mobile.message}</p>
                ) : (
                  <p className="text-xs text-muted-fg mt-1">{t('order.mobileHint')}</p>
                )}
              </div>
              <div>
                <label className={labelCls}>{t('order.email')}</label>
                <Input
                  type="email"
                  autoComplete="email"
                  placeholder={t('order.emailPlaceholder')}
                  error={Boolean(errors.email)}
                  className="mt-1.5"
                  {...register('email')}
                />
                {errors.email && (
                  <p className="text-sm text-destructive mt-1" role="alert">{errors.email.message}</p>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Delivery address */}
        <div>
          <h2 className="text-base font-bold text-heading mb-4">{t('order.deliverySection')}</h2>
          <div className="space-y-4">
            <div>
              <label className={labelCls}>{t('order.deliveryAddress')}</label>
              <Input
                autoComplete="street-address"
                placeholder={t('order.deliveryAddressPlaceholder')}
                maxLength={300}
                error={Boolean(errors.deliveryAddress)}
                className="mt-1.5"
                {...register('deliveryAddress')}
              />
              {errors.deliveryAddress && (
                <p className="text-sm text-destructive mt-1" role="alert">{errors.deliveryAddress.message}</p>
              )}
            </div>

            <div>
              <label className={labelCls}>{t('order.governorate')}</label>
              <select
                className={cn(
                  'w-full h-11 mt-1.5 px-3.5 rounded-lg bg-surface border text-foreground transition-colors focus-visible:outline-none appearance-none',
                  errors.governorate
                    ? 'border-destructive focus-visible:border-destructive'
                    : 'border-input-border hover:border-primary/50 focus-visible:border-primary',
                )}
                {...register('governorate')}
              >
                <option value="" disabled>
                  {t('order.governoratePlaceholder')}
                </option>
                {GOVERNORATES.map((g) => (
                  <option key={g} value={g}>
                    {t(`order.governorates.${g}`)}
                  </option>
                ))}
              </select>
              {errors.governorate && (
                <p className="text-sm text-destructive mt-1" role="alert">{errors.governorate.message}</p>
              )}
            </div>

            {/* Collapsible delivery notes */}
            <div>
              <button
                type="button"
                onClick={() => setNotesOpen(!notesOpen)}
                className="flex items-center gap-1.5 text-sm font-semibold text-primary hover:text-primary-hover transition-colors"
              >
                <ChevronDown
                  className={cn('h-4 w-4 transition-transform', notesOpen && 'rotate-180')}
                  aria-hidden
                />
                {t('order.deliveryNotes')} ({t('common.optional')})
              </button>
              {notesOpen && (
                <div className="mt-3">
                  <Textarea
                    maxLength={500}
                    placeholder={t('order.deliveryNotesPlaceholder')}
                    rows={3}
                    error={Boolean(errors.deliveryNotes)}
                    {...register('deliveryNotes')}
                  />
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between gap-2 pt-2">
          <button
            type="button"
            onClick={onBack}
            className="inline-flex items-center gap-1.5 text-sm font-semibold text-muted-fg hover:text-primary shrink-0"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden />
            {t('order.back')}
          </button>
          <Button type="submit" size="lg" className="shrink-0">
            {t('order.reviewPayment')} <ArrowRight className="h-4 w-4" aria-hidden />
          </Button>
        </div>
      </form>
    </section>
  )
}

function SuccessCard({
  title,
  hint,
  reference,
  status,
  onCopy,
  copied,
  children,
}: {
  title: string
  hint: string
  reference: string
  status: string
  onCopy: () => void
  copied: boolean
  children: React.ReactNode
}) {
  const { t } = useTranslation()
  return (
    <div className="rounded-2xl border border-border bg-surface p-6 sm:p-8 text-center">
      <div className="mx-auto h-16 w-16 rounded-full bg-success-soft text-success flex items-center justify-center">
        <Check className="h-8 w-8" aria-hidden />
      </div>
      <h1 className="text-2xl font-extrabold text-heading mt-5">{title}</h1>
      <p className="text-sm text-muted-fg mt-2">{hint}</p>

      <div className="mt-6 rounded-xl border border-border bg-background p-5">
        <p className="text-xs font-semibold text-muted-fg uppercase tracking-wide">{t('order.reference')}</p>
        <div className="flex items-center justify-center gap-2 mt-2">
          <p className="font-mono text-lg font-bold text-heading tracking-wide">{reference}</p>
          <button
            type="button"
            onClick={onCopy}
            className="text-muted-fg hover:text-primary p-1"
            aria-label={t('order.copyReference')}
          >
            {copied ? <Check className="h-4 w-4 text-success" aria-hidden /> : <Copy className="h-4 w-4" aria-hidden />}
          </button>
        </div>
        <p className="mt-1 inline-flex items-center gap-1.5 text-xs text-muted-fg">
          <Truck className="h-3.5 w-3.5" aria-hidden />
          {status}
        </p>
      </div>

      <div className="text-left">{children}</div>
    </div>
  )
}
