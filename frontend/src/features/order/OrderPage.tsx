import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useTranslation } from 'react-i18next'
import { useMutation, useQuery } from '@tanstack/react-query'
import {
  ArrowLeft,
  ArrowRight,
  Banknote,
  Check,
  ChevronDown,
  CircleHelp,
  Copy,
  Download,
  KeyRound,
  Loader2,
  Minus,
  PackageCheck,
  Plus,
  ScanLine,
  ShieldCheck,
  Smartphone,
  Truck,
  X,
} from 'lucide-react'
import { Input } from '@/components/ui/Input'
import { Textarea } from '@/components/ui/Textarea'
import { Alert } from '@/components/ui/Alert'
import { orderApi } from '@/services/orderApi'
import { http } from '@/services/apiClient'
import {
  orderDetailsSchema,
  DELIVERY_FEE,
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

const SUBMIT_BTN =
  'inline-flex h-[50px] items-center justify-center gap-3 rounded-full bg-primary px-7 text-sm font-bold text-white transition-colors hover:bg-primary-hover active:translate-y-0 disabled:pointer-events-none disabled:opacity-50'

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

  // Bring the confirmation screen into view on small screens.
  useEffect(() => {
    if (placed) window.scrollTo({ top: 0, behavior: 'smooth' })
  }, [placed])

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
  const subtotal = pack.price * qty
  const total = subtotal + DELIVERY_FEE
  const minPrice = Math.min(...PACKS.map((p) => p.price))

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

  // ---------- Confirmation screen (in-memory order, raw tokens shown once) ----------
  if (placed) {
    return (
      <OrderShell>
        <div className="px-5 py-8 sm:px-8 sm:py-10">
          <div className="mx-auto w-full max-w-3xl">
            {/* Hero */}
            <div className="text-center">
              <div className="mx-auto grid h-16 w-16 place-items-center rounded-full bg-success-soft text-success ring-8 ring-success-soft/60">
                <Check className="h-8 w-8" aria-hidden />
              </div>
              <h1 className="mt-5 text-[28px] font-black leading-tight tracking-[-0.03em] text-heading sm:text-4xl">
                {t('order.success')}
              </h1>
              <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-muted-fg">
                {t('order.successHint')}
              </p>
            </div>

            {/* Reference + status */}
            <div className="mt-7 flex flex-col gap-4 rounded-2xl border border-border bg-white p-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
              <div className="flex min-w-0 items-center gap-3">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-primary-soft text-primary">
                  <PackageCheck className="h-5 w-5" aria-hidden />
                </span>
                <div className="min-w-0">
                  <p className="text-[10px] font-semibold uppercase tracking-[0.12em] text-muted-fg">
                    {t('order.reference')}
                  </p>
                  <p className="truncate font-mono text-base font-bold tracking-wide text-heading">
                    {placed.reference}
                  </p>
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="inline-flex items-center gap-1.5 rounded-full bg-success-soft px-3 py-1.5 text-[11px] font-semibold text-success">
                  <Truck className="h-3.5 w-3.5" aria-hidden />
                  {t('order.statusPlaced')}
                </span>
                <button
                  type="button"
                  onClick={copyReference}
                  className="inline-flex h-9 items-center gap-1.5 rounded-full border border-border bg-white px-3 text-xs font-semibold text-muted-fg transition-colors hover:border-primary/40 hover:text-primary"
                >
                  {copied ? (
                    <Check className="h-3.5 w-3.5 text-success" aria-hidden />
                  ) : (
                    <Copy className="h-3.5 w-3.5" aria-hidden />
                  )}
                  {t('order.copyReference')}
                </button>
              </div>
            </div>

            {/* Summary + delivery */}
            <div className="mt-4 grid gap-4 sm:grid-cols-2">
              <div className="rounded-2xl border border-border bg-white p-5">
                <h2 className="text-sm font-semibold text-heading">{t('order.summary')}</h2>
                <dl className="mt-4 grid gap-3 text-sm">
                  {placed.items.map((item) => (
                    <div
                      key={item.stickerPackage}
                      className="flex items-baseline justify-between gap-4"
                    >
                      <dt className="text-muted-fg">
                        {item.quantity} × {t(`landing.pricingPack${packNum(item.stickerPackage)}Name`)}
                      </dt>
                      <dd className="font-semibold tabular-nums text-heading">
                        {item.subtotal} TND
                      </dd>
                    </div>
                  ))}
                  <div className="flex items-baseline justify-between gap-4">
                    <dt className="text-muted-fg">{t('order.deliveryRow')}</dt>
                    <dd className="font-semibold tabular-nums text-heading">
                      {DELIVERY_FEE.toFixed(2)} TND
                    </dd>
                  </div>
                  <div className="flex items-baseline justify-between gap-4 border-t border-border pt-3">
                    <dt className="font-semibold text-heading">{t('order.total')}</dt>
                    <dd className="text-lg font-bold tabular-nums text-heading">
                      {placed.totalAmount} TND
                    </dd>
                  </div>
                </dl>
              </div>

              <div className="rounded-2xl border border-border bg-white p-5">
                <h2 className="text-sm font-semibold text-heading">{t('order.deliverySection')}</h2>
                {details && (
                  <div className="mt-3 space-y-1 text-sm leading-6 text-muted-fg">
                    <p className="font-semibold text-heading">{details.customerName}</p>
                    <p dir="ltr" className="tabular-nums">
                      {toFullMobile(details.mobile)}
                    </p>
                    <p className="break-words">{details.email}</p>
                    <p className="break-words">{details.deliveryAddress}</p>
                    <p>{t(`order.governorates.${details.governorate}`)}</p>
                  </div>
                )}
              </div>
            </div>

            {/* QR stickers — raw tokens are shown exactly once */}
            <div className="mt-4 rounded-2xl border border-border bg-white p-5 sm:p-6">
              <div className="flex items-start gap-3">
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-primary-soft text-primary">
                  <ScanLine className="h-4 w-4" aria-hidden />
                </span>
                <div>
                  <h2 className="text-sm font-semibold text-heading">{t('order.yourStickers')}</h2>
                  <p className="mt-1 text-xs leading-5 text-muted-fg">{t('order.qrOnce')}</p>
                </div>
              </div>
              <ul className="mt-4 grid gap-3 sm:grid-cols-2">
                {placed.stickers.map((st, i) => (
                  <li
                    key={i}
                    className="flex items-center gap-4 rounded-xl border border-border bg-background p-3"
                  >
                    <img
                      src={st.imageDataUri}
                      alt={`${t('order.label')} ${i + 1}`}
                      className="h-24 w-24 shrink-0 rounded-lg bg-white p-1.5 shadow-card"
                    />
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-heading">
                        {t('order.label')} {i + 1}
                      </p>
                      <p className="mt-0.5 flex items-center gap-1 text-xs text-muted-fg">
                        <ScanLine className="h-3.5 w-3.5" aria-hidden />
                        {t('order.scanHint')}
                      </p>
                      <a
                        href={st.imageDataUri}
                        download={`carlink-sticker-${i + 1}.png`}
                        className="mt-2 inline-flex items-center gap-1.5 text-xs font-semibold text-primary transition-colors hover:text-primary-hover"
                      >
                        <Download className="h-3.5 w-3.5" aria-hidden />
                        {t('order.download')}
                      </a>
                      {st.activationCode && (
                        <div className="mt-2 inline-flex items-center gap-1 text-xs rounded-lg bg-primary-soft text-primary px-2 py-1 font-mono font-semibold border border-primary/30">
                          <KeyRound className="h-3 w-3" aria-hidden />
                          {st.activationCode}
                        </div>
                      )}
                    </div>
                  </li>
                ))}
              </ul>
              <p className="mt-4 flex items-start gap-2 text-xs leading-5 text-muted-fg">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden />
                {t('order.keepReference')}
              </p>
            </div>

            <div className="mt-6 flex flex-col justify-center gap-3 sm:flex-row">
              <Link
                to="/"
                className="inline-flex h-[46px] items-center justify-center gap-2 rounded-full border border-border bg-white px-6 text-sm font-semibold text-foreground transition-colors hover:bg-muted"
              >
                {t('order.goHome')}
              </Link>
              <button type="button" onClick={resetOrder} className={SUBMIT_BTN}>
                {t('order.orderAnother')}
              </button>
            </div>
          </div>
        </div>
      </OrderShell>
    )
  }

  // ---------- Recovery banner (after refresh: no QR codes retrievable) ----------
  const showRecovery = Boolean(lastReference && !placed && lastSummary.data)

  return (
    <OrderShell>
      {/* Intro */}
      <div className="px-5 pb-[22px] pt-5 sm:px-8 sm:pb-[26px] sm:pt-8">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
          <div className="min-w-0">
            <h1 className="max-w-[25ch] text-3xl font-black leading-tight text-heading sm:text-4xl">
              {t('order.title')}
            </h1>
            <p className="mt-3 max-w-[620px] text-[13px] leading-7 text-muted-fg">
              {t('order.subtitle')}
            </p>
            <ul className="mt-4 grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
              <BenefitChip icon={<Smartphone className="h-3.5 w-3.5" aria-hidden />}>
                {t('order.benefit1')}
              </BenefitChip>
              <BenefitChip icon={<ScanLine className="h-3.5 w-3.5" aria-hidden />}>
                {t('order.benefit2')}
              </BenefitChip>
              <BenefitChip icon={<Banknote className="h-3.5 w-3.5" aria-hidden />}>
                {t('order.benefit3')}
              </BenefitChip>
              <BenefitChip icon={<ShieldCheck className="h-3.5 w-3.5" aria-hidden />}>
                {t('order.benefit4')}
              </BenefitChip>
            </ul>
          </div>
          <p className="flex shrink-0 items-baseline gap-1.5 sm:mt-2">
            <span className="text-[9px] text-muted-fg">{t('order.fromPrice')}</span>
            <strong className="text-lg font-semibold tabular-nums text-primary">
              {minPrice} TND
            </strong>
            <span className="text-[9px] text-muted-fg">/ {t('landing.pricingPerYear')}</span>
          </p>
        </div>
      </div>

      {/* Checkout: summary aside + order card */}
      <div className="grid gap-7 px-3.5 pb-0 pt-3.5 sm:px-8 sm:pt-[30px] lg:grid-cols-[minmax(260px,0.75fr)_minmax(0,1.25fr)] lg:gap-9">
        <OrderSummaryAside
          className="hidden lg:block lg:sticky lg:top-24 lg:mb-10"
          pkg={pkg}
          qty={qty}
          subtotal={subtotal}
          total={total}
        />

        <div
          className="relative mb-8 rounded-[24px] border border-border bg-white p-[22px] shadow-[0_12px_40px_rgba(37,99,235,0.05)] sm:p-8 lg:mb-10"
          aria-busy={placeMutation.isPending || undefined}
        >
          {placeMutation.isPending && <PlacingOrderOverlay />}
          {showRecovery && lastSummary.data && (
            <div className="mb-7 rounded-2xl border border-primary/30 bg-primary-soft/50 p-4">
              <div className="flex items-start gap-3">
                <div className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-primary text-white">
                  <Check className="h-5 w-5" aria-hidden />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-bold text-heading">{t('order.lastOrder')}</p>
                  <p className="mt-0.5 text-sm text-muted-fg">
                    <span className="font-mono font-semibold text-foreground">
                      {lastSummary.data.reference}
                    </span>
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
                  className="p-1 text-muted-fg transition-colors hover:text-foreground"
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
            <section className="page-enter">
              <header className="mb-5">
                <h2 className="text-[27px] font-semibold leading-snug tracking-[-0.03em] text-heading">
                  {t('order.choosePack')}
                </h2>
                <p className="mt-2 text-xs leading-5 text-muted-fg">{t('order.choosePackHint')}</p>
              </header>

              <div className="grid gap-2.5">
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
                        'grid w-full grid-cols-[18px_minmax(0,1fr)_auto] items-center gap-3 rounded-[14px] border bg-white p-4 text-start transition-all',
                        selected
                          ? 'border-primary bg-primary/5 ring-1 ring-primary'
                          : 'border-border hover:border-primary/60',
                      )}
                    >
                      <span
                        className={cn(
                          'grid h-[18px] w-[18px] place-items-center rounded-full border transition-colors',
                          selected ? 'border-primary bg-primary text-white' : 'border-input-border',
                        )}
                        aria-hidden="true"
                      >
                        {selected && <Check className="h-3 w-3" />}
                      </span>

                      <span className="min-w-0">
                        <span className="flex flex-wrap items-center gap-2">
                          <span className="text-[15px] font-semibold text-heading">
                            {t(`landing.pricingPack${packNum(p.key)}Name`)}
                          </span>
                          {p.badge && (
                            <span className="rounded-full bg-primary/10 px-2 py-[3px] text-[9px] font-semibold text-primary">
                              {t(p.badge)}
                            </span>
                          )}
                        </span>
                        <span className="mt-1 block text-[11px] leading-5 text-muted-fg">
                          {t(`landing.pricingPack${packNum(p.key)}Subtitle`)}
                        </span>
                      </span>

                      <span className="text-end">
                        <span className="block text-[23px] font-semibold leading-none tabular-nums tracking-[-0.04em] text-heading">
                          {p.price}
                        </span>
                        <span className="mt-1 block text-[10px] font-medium text-muted-fg">
                          TND / {t('landing.pricingPerYear')}
                        </span>
                      </span>
                    </button>
                  )
                })}
              </div>

              <Link
                to="/pricing"
                className="mt-3 inline-flex w-fit items-center gap-1.5 text-xs font-semibold text-primary transition-colors hover:text-primary-hover"
              >
                <CircleHelp className="h-4 w-4" aria-hidden />
                {t('order.comparePlans')}
              </Link>

              {/* Quantity */}
              <div className="mt-4 border-t border-border pt-[18px]">
                <div className="flex items-center justify-between gap-4">
                  <div className="flex-1">
                    <span className="block text-sm font-semibold text-heading">
                      {t('order.quantity')}
                    </span>
                    <p className="mt-1.5 text-[10px] leading-5 text-muted-fg">
                      {t('order.qtyHint', {
                        plan: t(`landing.pricingPack${packNum(pkg)}Name`),
                        price: pack.price,
                      })}
                      {' · '}
                      {totalStickers} {t('order.stickerCount')}
                    </p>
                  </div>
                  <div className="flex h-11 w-[138px] shrink-0 items-center rounded-2xl border border-input-border bg-white transition-colors hover:border-primary/40 focus-within:ring-2 focus-within:ring-primary/25">
                    <button
                      type="button"
                      disabled={qty <= 1}
                      onClick={() => setQty((q) => Math.max(1, q - 1))}
                      aria-label={t('order.decrease')}
                      className="grid h-full w-12 shrink-0 place-items-center rounded-s-2xl transition-colors hover:bg-muted disabled:opacity-40"
                    >
                      <Minus className="h-4 w-4" aria-hidden />
                    </button>
                    <span
                      className="min-w-0 flex-1 border-x border-input-border text-center text-base font-semibold tabular-nums text-heading"
                      role="spinbutton"
                      aria-valuenow={qty}
                      aria-valuemin={1}
                      aria-valuemax={maxQty}
                      aria-label={t('order.quantity')}
                    >
                      {qty}
                    </span>
                    <button
                      type="button"
                      disabled={qty >= maxQty}
                      onClick={() => setQty((q) => Math.min(maxQty, q + 1))}
                      aria-label={t('order.increase')}
                      className="grid h-full w-12 shrink-0 place-items-center rounded-e-2xl transition-colors hover:bg-muted disabled:opacity-40"
                    >
                      <Plus className="h-4 w-4" aria-hidden />
                    </button>
                  </div>
                </div>
              </div>

              {/* Mobile totals (desktop uses the summary aside) */}
              <MobileTotal pkg={pkg} qty={qty} subtotal={subtotal} total={total} />

              <nav className="mt-5 flex justify-end">
                <button type="button" className={SUBMIT_BTN} onClick={() => setStep(2)}>
                  {t('order.continue')}
                  <ArrowRight className="h-4 w-4 rtl:rotate-180" aria-hidden />
                </button>
              </nav>
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
            <section className="page-enter">
              <header className="mb-5">
                <h2 className="text-[27px] font-semibold leading-snug tracking-[-0.03em] text-heading">
                  {t('order.summary')}
                </h2>
                <p className="mt-2 text-xs leading-5 text-muted-fg">{t('order.summaryHint')}</p>
              </header>

              {/* Delivery summary */}
              <div className="rounded-2xl border border-border bg-primary-soft/50 p-4">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <span className="flex items-center gap-2 text-sm font-semibold text-heading">
                    <Truck className="h-4 w-4 text-primary" aria-hidden />
                    {t('order.cashOnDelivery')}
                  </span>
                  <button
                    type="button"
                    onClick={() => setStep(2)}
                    className="text-xs font-semibold text-primary transition-colors hover:text-primary-hover"
                  >
                    {t('order.editDelivery')}
                  </button>
                </div>
                <p className="text-sm font-medium text-heading">{details.customerName}</p>
                <p className="text-sm tabular-nums text-muted-fg" dir="ltr">
                  {toFullMobile(details.mobile)}
                </p>
                <p className="text-sm text-muted-fg">{details.email}</p>
                <p className="text-sm text-muted-fg">
                  {details.deliveryAddress}
                  {details.deliveryNotes?.trim() ? `, ${details.deliveryNotes.trim()}` : ''}
                </p>
                <p className="text-sm text-muted-fg">{t(`order.governorates.${details.governorate}`)}</p>
              </div>

              {/* Payment method */}
              <div className="mt-5">
                <h3 className="mb-3 text-sm font-semibold text-heading">
                  {t('order.paymentMethod')}
                </h3>
                <div className="rounded-2xl border border-primary bg-primary/5 p-4 ring-1 ring-primary">
                  <div className="flex items-start gap-3">
                    <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full bg-primary text-white">
                      <Check className="h-3 w-3" aria-hidden />
                    </span>
                    <div>
                      <p className="text-sm font-semibold text-heading">{t('order.cashOnDelivery')}</p>
                      <p className="mt-0.5 text-xs text-muted-fg">{t('order.codHint')}</p>
                    </div>
                  </div>
                </div>
              </div>

              {/* Order total */}
              <div className="mt-5 flex items-end justify-between border-t border-border pt-4">
                <div>
                  <span className="text-sm font-semibold text-heading">{t('order.total')}</span>
                  <p className="text-xs text-muted-fg">
                    {t('order.deliveryFeeNote', { fee: DELIVERY_FEE.toFixed(2) })}
                  </p>
                </div>
                <span className="text-2xl font-semibold tabular-nums tracking-[-0.03em] text-heading">
                  {total.toFixed(2)} TND
                </span>
              </div>

              {/* CTA */}
              <nav className="mt-5 flex items-center justify-between gap-3">
                <button
                  type="button"
                  onClick={() => setStep(2)}
                  className="inline-flex items-center gap-1.5 text-xs font-semibold text-muted-fg transition-colors hover:text-primary"
                >
                  <ArrowLeft className="h-4 w-4 rtl:rotate-180" aria-hidden />
                  {t('order.back')}
                </button>
                <button
                  type="button"
                  disabled={placeMutation.isPending}
                  onClick={() => placeMutation.mutate()}
                  className={SUBMIT_BTN}
                >
                  {placeMutation.isPending ? (
                    <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
                  ) : null}
                  {t('order.confirmOrder')} — {total.toFixed(2)} TND
                </button>
              </nav>

              {/* Trust */}
              <p className="mt-3 flex items-center justify-center gap-1.5 text-[11px] text-muted-fg">
                <ShieldCheck className="h-3.5 w-3.5 text-success" aria-hidden />
                {t('order.trustHint')}
              </p>
            </section>
          )}
        </div>
      </div>
    </OrderShell>
  )
}

/* ------------------------------- subcomponents ------------------------------- */

/** Photo band + shade + slide dots, mirroring ewsali's order backdrop. */
function OrderShell({ children }: { children: ReactNode }) {
  return (
    <div className="px-3 py-8 sm:px-6 sm:py-12">
      <section className="mx-auto w-full max-w-[1120px] overflow-hidden rounded-[24px] border border-border bg-background shadow-[0_18px_60px_rgba(15,23,42,0.08)] sm:rounded-[32px]">
        {children}
      </section>
    </div>
  )
}

function BenefitChip({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <li className="flex h-full min-h-[36px] w-full items-center justify-center gap-1.5 rounded-full bg-white/85 px-2.5 py-2 text-center text-[10px] font-semibold leading-tight text-muted-fg ring-1 ring-white/70 sm:inline-flex sm:min-h-0 sm:w-auto sm:justify-start">
      <span className="shrink-0 text-primary">{icon}</span>
      {children}
    </li>
  )
}

/** Full-screen progress state while the order is being created server-side. */
function PlacingOrderOverlay() {
  const { t } = useTranslation()
  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed inset-0 z-[100] grid place-items-center bg-white/80 p-6 backdrop-blur-sm"
    >
      <div className="flex w-full max-w-sm flex-col items-center rounded-[28px] border border-border bg-white p-8 text-center shadow-[0_24px_70px_rgba(15,23,42,0.16)]">
        <span className="grid h-16 w-16 place-items-center rounded-full bg-primary-soft text-primary">
          <Loader2 className="h-8 w-8 animate-spin" aria-hidden />
        </span>
        <p className="mt-5 text-base font-bold text-heading">{t('order.placingOrder')}</p>
        <p className="mt-1.5 text-xs leading-5 text-muted-fg">{t('order.placingHint')}</p>
      </div>
    </div>
  )
}

/** Rounded sticker artwork used in the summary aside + mobile total. */
function StickerArt({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'block aspect-[1/2] w-[90px] rounded-[13%] bg-white p-2 shadow-[0_10px_24px_rgba(15,23,42,0.14)] ring-1 ring-black/5',
        className,
      )}
      aria-hidden="true"
    >
      <span className="grid h-full w-full place-items-center rounded-[9%] bg-primary-soft">
        <svg viewBox="0 0 33 33" className="h-[74%] w-[74%] text-primary" fill="currentColor">
          <rect x="2" y="2" width="9" height="9" rx="1.5" fill="none" stroke="currentColor" strokeWidth="2.2" />
          <rect x="5.5" y="5.5" width="2" height="2" />
          <rect x="22" y="2" width="9" height="9" rx="1.5" fill="none" stroke="currentColor" strokeWidth="2.2" />
          <rect x="25.5" y="5.5" width="2" height="2" />
          <rect x="2" y="22" width="9" height="9" rx="1.5" fill="none" stroke="currentColor" strokeWidth="2.2" />
          <rect x="5.5" y="25.5" width="2" height="2" />
          <rect x="14" y="3" width="2.4" height="2.4" />
          <rect x="18" y="3" width="2.4" height="2.4" />
          <rect x="14" y="7" width="2.4" height="2.4" />
          <rect x="14" y="13" width="2.4" height="2.4" />
          <rect x="18" y="13" width="2.4" height="2.4" />
          <rect x="24" y="13" width="2.4" height="2.4" />
          <rect x="28" y="13" width="2.4" height="2.4" />
          <rect x="3" y="14" width="2.4" height="2.4" />
          <rect x="7" y="14" width="2.4" height="2.4" />
          <rect x="14" y="17" width="2.4" height="2.4" />
          <rect x="20" y="17" width="2.4" height="2.4" />
          <rect x="26" y="17" width="2.4" height="2.4" />
          <rect x="14" y="22" width="2.4" height="2.4" />
          <rect x="18" y="22" width="2.4" height="2.4" />
          <rect x="24" y="23" width="2.4" height="2.4" />
          <rect x="28" y="23" width="2.4" height="2.4" />
          <rect x="14" y="27" width="2.4" height="2.4" />
          <rect x="21" y="27" width="2.4" height="2.4" />
          <rect x="27" y="27" width="2.4" height="2.4" />
        </svg>
      </span>
    </span>
  )
}

function OrderSummaryAside({
  pkg,
  qty,
  subtotal,
  total,
  className,
}: {
  pkg: StickerPackage
  qty: number
  subtotal: number
  total: number
  className?: string
}) {
  const { t } = useTranslation()
  const planName = t(`landing.pricingPack${packNum(pkg)}Name`)

  return (
    <aside
      className={cn(
        'rounded-3xl border border-border bg-primary-soft p-7',
        className,
      )}
      aria-label={t('order.summary')}
    >
      <div className="text-center">
        <div className="mb-5 grid min-h-[236px] place-items-center">
          <StickerArt className="-rotate-[8deg]" />
        </div>
        <p className="text-[10px] font-bold uppercase tracking-[0.12em] text-primary">
          {t('order.summaryEyebrow')}
        </p>
        <h3 className="mt-2 text-[22px] font-semibold leading-snug tracking-[-0.02em] text-heading">
          {t('order.summaryProduct')}
        </h3>
        <p className="text-xs leading-5 text-muted-fg">{t('order.summaryIncludes')}</p>
      </div>

      <dl className="mt-7 grid gap-3.5 border-t border-border pt-6 text-[13px]">
        <div className="flex items-baseline justify-between gap-4">
          <dt className="text-muted-fg">
            {qty} × {planName}
          </dt>
          <dd className="font-semibold tabular-nums text-heading">{subtotal.toFixed(2)} TND</dd>
        </div>
        <div className="flex items-baseline justify-between gap-4">
          <dt className="text-muted-fg">{t('order.deliveryRow')}</dt>
          <dd className="font-semibold tabular-nums text-heading">{DELIVERY_FEE.toFixed(2)} TND</dd>
        </div>
        <div className="mt-1.5 flex items-baseline justify-between gap-4 border-t border-border pt-[18px]">
          <dt className="font-semibold text-heading">{t('order.total')}</dt>
          <dd className="text-[27px] font-semibold tabular-nums tracking-[-0.04em] text-heading">
            {total.toFixed(2)}
          </dd>
        </div>
      </dl>

      <ul className="mt-6 grid gap-3 text-[11px] text-muted-fg">
        <li className="flex items-center gap-2.5">
          <Truck className="h-4 w-4 shrink-0 text-primary" aria-hidden />
          {t('order.summaryBenefit1')}
        </li>
        <li className="flex items-center gap-2.5">
          <PackageCheck className="h-4 w-4 shrink-0 text-primary" aria-hidden />
          {t('order.summaryBenefit2')}
        </li>
        <li className="flex items-center gap-2.5">
          <Banknote className="h-4 w-4 shrink-0 text-primary" aria-hidden />
          {t('order.summaryBenefit3')}
        </li>
      </ul>
    </aside>
  )
}

/** Compact invoice card shown on small screens (the aside is desktop-only). */
function MobileTotal({
  pkg,
  qty,
  subtotal,
  total,
}: {
  pkg: StickerPackage
  qty: number
  subtotal: number
  total: number
}) {
  const { t } = useTranslation()
  const planName = t(`landing.pricingPack${packNum(pkg)}Name`)

  return (
    <div className="mt-4 rounded-2xl border border-border bg-primary-soft p-3.5 lg:hidden">
      <div className="flex items-start gap-3">
        <StickerArt className="w-6 shrink-0 -rotate-[7deg]" />
        <div className="min-w-0 flex-1 space-y-2 text-xs">
          <div className="flex items-baseline justify-between gap-3">
            <span className="truncate font-bold text-heading">
              {planName} × {qty}
            </span>
            <strong className="shrink-0 font-bold text-heading">{subtotal.toFixed(2)} TND</strong>
          </div>
          <div className="flex items-baseline justify-between gap-3 text-muted-fg">
            <span>{t('order.deliveryRow')}</span>
            <strong className="shrink-0 text-[11px] font-semibold text-muted-fg">
              {DELIVERY_FEE.toFixed(2)} TND
            </strong>
          </div>
          <div className="flex items-baseline justify-between gap-3 border-t border-border pt-2.5">
            <span className="text-[11px] font-bold text-heading">{t('order.total')}</span>
            <strong className="shrink-0 text-lg font-bold tabular-nums tracking-[-0.03em] text-heading">
              {total.toFixed(2)} TND
            </strong>
          </div>
        </div>
      </div>
    </div>
  )
}

function Stepper({ step }: { step: 1 | 2 | 3 }) {
  const { t } = useTranslation()
  const labels = [t('order.stepPackage'), t('order.stepDetails'), t('order.stepConfirm')]
  const steps = [1, 2, 3] as const
  return (
    <nav
      className="mx-auto mb-7 grid w-full max-w-[520px] grid-cols-3 border-b border-border pb-6"
      aria-label={t('order.stepsAria')}
    >
      {steps.map((n) => {
        const done = n < step
        const active = n === step
        return (
          <div key={n} className="relative grid justify-items-center gap-2">
            {n < 3 && (
              <span
                aria-hidden="true"
                className={cn('absolute top-[14px] h-px', done || n + 1 <= step ? 'bg-primary' : 'bg-border')}
                style={{ insetInlineStart: 'calc(50% + 1.05rem)', width: 'calc(100% - 2.1rem)' }}
              />
            )}
            <span
              className={cn(
                'grid h-7 w-7 place-items-center rounded-full text-xs font-bold transition-colors',
                active && 'bg-primary text-white',
                done && 'bg-primary/10 text-primary',
                !done && !active && 'bg-muted text-muted-fg',
              )}
            >
              {n}
            </span>
            <span
              className={cn(
                'text-center text-[11px] font-medium leading-tight',
                active || done ? 'text-primary' : 'text-muted-fg',
              )}
            >
              {labels[n - 1]}
            </span>
          </div>
        )
      })}
    </nav>
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
    watch,
    setValue,
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

  const labelCls = 'text-xs font-medium text-foreground'
  const governorate = watch('governorate')

  return (
    <section className="page-enter">
      <header className="mb-5">
        <h2 className="text-[27px] font-semibold leading-snug tracking-[-0.03em] text-heading">
          {t('order.details')}
        </h2>
        <p className="mt-2 text-xs leading-5 text-muted-fg">{t('order.detailsHint')}</p>
      </header>

      <form onSubmit={handleSubmit(onSubmit)} className="grid gap-6" noValidate>
        {/* Contact details */}
        <div className="grid gap-4 border-t border-border pt-5">
          <h3 className="text-sm font-semibold text-heading">{t('order.contactDetails')}</h3>
          <div className="grid gap-4">
            <div>
              <label className={labelCls}>{t('order.fullName')}</label>
              <Input
                autoComplete="name"
                placeholder={t('order.fullNamePlaceholder')}
                maxLength={100}
                error={Boolean(errors.customerName)}
                className="mt-1.5 h-12 rounded-xl"
                {...register('customerName')}
              />
              {errors.customerName && (
                <p className="mt-1 text-sm text-destructive" role="alert">
                  {errors.customerName.message}
                </p>
              )}
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className={labelCls}>{t('order.mobile')}</label>
                <div
                  className={cn(
                    'mt-1.5 flex h-12 overflow-hidden rounded-xl border bg-white transition-colors',
                    errors.mobile
                      ? 'border-destructive'
                      : 'border-border hover:border-primary/50 focus-within:border-primary',
                  )}
                >
                  <span className="flex h-full select-none items-center border-e border-border bg-muted/50 px-3.5 text-sm font-medium text-foreground">
                    {TN_DIAL_CODE}
                  </span>
                  <input
                    type="tel"
                    inputMode="numeric"
                    autoComplete="tel-national"
                    placeholder="22 001 122"
                    maxLength={8}
                    aria-invalid={Boolean(errors.mobile)}
                    className="min-w-0 flex-1 bg-transparent px-3.5 text-foreground placeholder:text-muted-fg focus:outline-none"
                    {...register('mobile', {
                      onChange: (e) => {
                        e.target.value = e.target.value.replace(/\D/g, '').slice(0, 8)
                      },
                    })}
                  />
                </div>
                {errors.mobile ? (
                  <p className="mt-1 text-sm text-destructive" role="alert">
                    {errors.mobile.message}
                  </p>
                ) : (
                  <p className="mt-1 text-xs text-muted-fg">{t('order.mobileHint')}</p>
                )}
              </div>
              <div>
                <label className={labelCls}>{t('order.email')}</label>
                <Input
                  type="email"
                  autoComplete="email"
                  placeholder={t('order.emailPlaceholder')}
                  error={Boolean(errors.email)}
                  className="mt-1.5 h-12 rounded-xl"
                  {...register('email')}
                />
                {errors.email && (
                  <p className="mt-1 text-sm text-destructive" role="alert">
                    {errors.email.message}
                  </p>
                )}
              </div>
            </div>
          </div>
        </div>

        {/* Delivery address */}
        <div className="grid gap-4 border-t border-border pt-5">
          <h3 className="text-sm font-semibold text-heading">{t('order.deliverySection')}</h3>
          <div className="grid gap-4">
            <div>
              <label className={labelCls}>{t('order.deliveryAddress')}</label>
              <Input
                autoComplete="street-address"
                placeholder={t('order.deliveryAddressPlaceholder')}
                maxLength={300}
                error={Boolean(errors.deliveryAddress)}
                className="mt-1.5 h-12 rounded-xl"
                {...register('deliveryAddress')}
              />
              {errors.deliveryAddress && (
                <p className="mt-1 text-sm text-destructive" role="alert">
                  {errors.deliveryAddress.message}
                </p>
              )}
            </div>

            <div>
              <label className={labelCls}>{t('order.governorate')}</label>
              <GovernorateSelect
                value={governorate}
                error={Boolean(errors.governorate)}
                placeholder={t('order.governoratePlaceholder')}
                onChange={(g) =>
                  setValue('governorate', g, { shouldValidate: true, shouldDirty: true })
                }
              />
              {errors.governorate && (
                <p className="mt-1 text-sm text-destructive" role="alert">
                  {errors.governorate.message}
                </p>
              )}
            </div>

            {/* Collapsible delivery notes */}
            <div>
              <button
                type="button"
                onClick={() => setNotesOpen(!notesOpen)}
                className="inline-flex items-center gap-1.5 text-sm font-semibold text-primary transition-colors hover:text-primary-hover"
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

        {/* Footer nav */}
        <div className="flex items-center justify-between gap-3 border-t border-border pt-5">
          <button
            type="button"
            onClick={onBack}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-muted-fg transition-colors hover:text-primary"
          >
            <ArrowLeft className="h-4 w-4 rtl:rotate-180" aria-hidden />
            {t('order.back')}
          </button>
          <button type="submit" className={SUBMIT_BTN}>
            {t('order.reviewPayment')}
            <ArrowRight className="h-4 w-4 rtl:rotate-180" aria-hidden />
          </button>
        </div>
      </form>
    </section>
  )
}

/** Elegant governorate picker — mirrors the language selector's dropdown. */
function GovernorateSelect({
  value,
  onChange,
  placeholder,
  error,
}: {
  value: string
  onChange: (value: Governorate) => void
  placeholder: string
  error?: boolean
}) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onDocClick)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return (
    <div className="relative mt-1.5" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-invalid={error || undefined}
        className={cn(
          'flex h-12 w-full items-center justify-between gap-3 rounded-xl border bg-white px-3.5 text-start text-sm transition-colors focus-visible:outline-none',
          error
            ? 'border-destructive'
            : 'border-border hover:border-primary/50 focus-visible:border-primary',
          open && !error && 'border-primary',
        )}
      >
        <span className={cn('truncate', value ? 'font-medium text-foreground' : 'text-muted-fg')}>
          {value ? t(`order.governorates.${value}`) : placeholder}
        </span>
        <ChevronDown
          className={cn(
            'h-4 w-4 shrink-0 text-muted-fg transition-transform',
            open && 'rotate-180',
          )}
          aria-hidden
        />
      </button>

      {open && (
        <div
          role="listbox"
          className="absolute inset-x-0 z-50 mt-2 max-h-64 overflow-y-auto rounded-xl border border-border bg-surface py-1 shadow-pop"
        >
          {GOVERNORATES.map((g) => {
            const selected = g === value
            return (
              <button
                key={g}
                type="button"
                role="option"
                aria-selected={selected}
                onClick={() => {
                  onChange(g)
                  setOpen(false)
                }}
                className={cn(
                  'flex w-full items-center justify-between gap-3 px-3.5 py-2.5 text-start text-sm transition-colors hover:bg-muted',
                  selected ? 'font-semibold text-primary' : 'text-foreground',
                )}
              >
                <span className="truncate">{t(`order.governorates.${g}`)}</span>
                {selected && <Check className="h-4 w-4 shrink-0" aria-hidden />}
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
