import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Banknote, Check, Sticker, Truck } from 'lucide-react'
import { PageIntro, PublicContainer } from '@/components/shared/PublicPage'
import { CountUp } from '@/components/shared/CountUp'
import { cn } from '@/lib/cn'

const CURRENCY = 'TND'
/** Flat delivery fee added at checkout (mirrors OrderService.DELIVERY_FEE). */
const DELIVERY_FEE = 7

const plans = [
  {
    key: 'SINGLE',
    n: 1,
    stickers: 1,
    price: 20,
    compareAt: null,
    badge: null,
    save: null,
    featured: false,
    features: ['landing.pricingPlan1Feature1', 'landing.pricingPlan1Feature2', 'landing.pricingPlan1Feature3'],
  },
  {
    key: 'DOUBLE',
    n: 2,
    stickers: 2,
    price: 35,
    compareAt: 40,
    badge: 'landing.pricingPack2Badge',
    save: 'landing.pricingSave2',
    featured: true,
    features: ['landing.pricingPlan2Feature1', 'landing.pricingPlan2Feature2', 'landing.pricingPlan2Feature3'],
  },
  {
    key: 'BUSINESS',
    n: 3,
    stickers: 20,
    price: 350,
    compareAt: 400,
    badge: 'landing.pricingPack3Badge',
    save: 'landing.pricingSave3',
    featured: false,
    features: [
      'landing.pricingPlan3Feature1',
      'landing.pricingPlan3Feature2',
      'landing.pricingPlan3Feature3',
      'landing.pricingPlan3Feature4',
    ],
  },
] as const

export default function PricingPage() {
  const { t, i18n } = useTranslation()
  const num = new Intl.NumberFormat(i18n.language, { maximumFractionDigits: 1 })
  const perYear = (price: number) => `${price} ${CURRENCY} / ${t('landing.pricingPerYear')}`

  const compareRows = [
    { label: t('landing.pricingStickers'), values: plans.map((p) => t('landing.pricingStickerCount', { count: p.stickers })) },
    { label: t('landing.pricingComparePrice'), values: plans.map((p) => perYear(p.price)) },
    {
      label: t('landing.pricingPerSticker'),
      values: plans.map((p) => `${num.format(p.price / p.stickers)} ${CURRENCY}`),
    },
    { label: t('landing.pricingCompareRenewal'), values: plans.map((p) => perYear(p.price)) },
    {
      label: t('landing.pricingCompareDelivery'),
      values: [t('landing.pricingCompareDeliveryBasic'), t('landing.pricingCompareDeliveryBoth'), t('landing.pricingCompareDeliveryBoth')],
    },
    { label: t('landing.pricingCompareRelay'), values: plans.map(() => t('landing.pricingCompareIncluded')) },
    { label: t('landing.pricingCompareNewSticker'), values: plans.map(() => t('landing.pricingCompareIncluded')) },
  ]

  return (
    <PublicContainer>
      <PageIntro center eyebrow={t('landing.pricingBadge')} title={t('landing.pricingTitle')} lead={t('landing.pricingSubtitle')} />

      {/* Plans */}
      <div className="motion-stagger grid gap-5 lg:grid-cols-3 lg:items-stretch">
        {plans.map((plan) => (
          <article
            key={plan.key}
            className={cn(
              'hover-lift relative flex h-full flex-col rounded-3xl border bg-surface p-6 sm:p-7',
              plan.featured ? 'border-2 border-primary shadow-[0_18px_50px_rgba(37,99,235,0.14)]' : 'border-border shadow-card',
            )}
          >
            {plan.badge && (
              <span
                className={cn(
                  'absolute -top-3 start-6 rounded-full px-3 py-1 text-xs font-bold',
                  plan.featured ? 'bg-primary text-white' : 'bg-accent text-white',
                )}
              >
                {t(plan.badge)}
              </span>
            )}

            <div className="flex items-start justify-between gap-3">
              <div>
                <h2 className="text-xl font-bold text-heading">{t(`landing.pricingPack${plan.n}Name`)}</h2>
                <p className="mt-1 text-sm text-muted-fg">{t(`landing.pricingPack${plan.n}Subtitle`)}</p>
              </div>
              <span className="inline-flex shrink-0 items-center gap-1.5 rounded-full bg-primary-soft px-3 py-1.5 text-sm font-bold text-primary">
                <Sticker className="h-4 w-4" aria-hidden />
                {t('landing.pricingStickerCount', { count: plan.stickers })}
              </span>
            </div>

            <div className="mt-6 flex items-end gap-2">
              <span className="font-display text-5xl font-black leading-none tracking-tight text-heading">
                <CountUp value={plan.price} />
              </span>
              <span className="pb-1 text-base font-medium text-muted-fg">
                {CURRENCY} / {t('landing.pricingPerYear')}
              </span>
            </div>
            <div className="mt-2 flex min-h-6 flex-wrap items-center gap-2 text-sm">
              {plan.compareAt && <span className="text-muted-fg line-through">{plan.compareAt} {CURRENCY}</span>}
              {plan.save && <span className="rounded-full bg-success-soft px-2 py-0.5 font-semibold text-success">{t(plan.save)}</span>}
            </div>

            <ul className="mt-6 flex-1 space-y-3 border-t border-border pt-6 text-base text-heading">
              {plan.features.map((feature) => (
                <li key={feature} className="flex items-start gap-3">
                  <span className="mt-0.5 grid h-5 w-5 shrink-0 place-items-center rounded-full bg-primary-soft text-primary">
                    <Check className="h-3.5 w-3.5" aria-hidden />
                  </span>
                  <span className="leading-snug">{t(feature)}</span>
                </li>
              ))}
            </ul>

            <Link
              to={`/order?plan=${plan.key}`}
              className={cn(
                'group mt-8 inline-flex h-12 w-full items-center justify-center gap-2 rounded-full text-base font-semibold transition-all active:scale-[0.98]',
                plan.featured
                  ? 'bg-primary text-white shadow-[0_10px_30px_rgba(37,99,235,0.22)] hover:bg-primary-hover'
                  : 'border border-primary/30 bg-surface text-primary hover:bg-primary-soft',
              )}
            >
              {t('landing.pricingCtaPack')}
              <ArrowRight className="nudge h-4 w-4 rtl:rotate-180" aria-hidden />
            </Link>
          </article>
        ))}
      </div>

      {/* What every order includes */}
      <div className="motion-stagger mt-8 grid gap-3 sm:grid-cols-2">
        <div className="flex items-start gap-3 rounded-2xl border border-border bg-surface p-5">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-primary-soft text-primary" aria-hidden>
            <Truck className="h-5 w-5" />
          </span>
          <div>
            <p className="font-semibold text-heading">{t('landing.pricingDeliveryTitle', { fee: DELIVERY_FEE })}</p>
            <p className="mt-1 text-sm leading-relaxed text-muted-fg">{t('landing.pricingDeliveryText')}</p>
          </div>
        </div>
        <div className="flex items-start gap-3 rounded-2xl border border-border bg-surface p-5">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-success-soft text-success" aria-hidden>
            <Banknote className="h-5 w-5" />
          </span>
          <div>
            <p className="font-semibold text-heading">{t('order.cashOnDelivery')}</p>
            <p className="mt-1 text-sm leading-relaxed text-muted-fg">{t('order.codHint')}</p>
          </div>
        </div>
      </div>

      {/* Comparison */}
      <section className="mt-14" aria-labelledby="compare-title">
        <h2 id="compare-title" className="mb-5 text-2xl font-bold text-heading sm:text-3xl">
          {t('landing.pricingCompareTitle')}
        </h2>

        <div className="hidden overflow-hidden rounded-2xl border border-border md:block">
          <table className="w-full text-base">
            <thead className="bg-muted/50">
              <tr>
                <th scope="col" className="px-5 py-4 text-start text-sm font-semibold text-muted-fg">
                  {t('landing.pricingCompareFeature')}
                </th>
                {plans.map((plan) => (
                  <th key={plan.key} scope="col" className={cn('px-5 py-4 text-start font-bold text-heading', plan.featured && 'bg-primary-soft text-primary')}>
                    {t(`landing.pricingPack${plan.n}Name`)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-border bg-surface">
              {compareRows.map((row) => (
                <tr key={row.label}>
                  <th scope="row" className="px-5 py-4 text-start text-sm font-medium text-muted-fg">
                    {row.label}
                  </th>
                  {row.values.map((value, i) => (
                    <td key={i} className={cn('px-5 py-4 text-heading', plans[i].featured && 'bg-primary-soft/40')}>
                      {value}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="grid gap-4 md:hidden">
          {plans.map((plan, i) => (
            <article key={plan.key} className="overflow-hidden rounded-2xl border border-border bg-surface">
              <h3 className={cn('px-5 py-4 text-lg font-bold', plan.featured ? 'bg-primary-soft text-primary' : 'bg-muted/50 text-heading')}>
                {t(`landing.pricingPack${plan.n}Name`)}
              </h3>
              <dl className="divide-y divide-border px-5">
                {compareRows.map((row) => (
                  <div key={row.label} className="flex items-center justify-between gap-4 py-3.5">
                    <dt className="text-sm text-muted-fg">{row.label}</dt>
                    <dd className="text-end text-sm font-semibold text-heading">{row.values[i]}</dd>
                  </div>
                ))}
              </dl>
            </article>
          ))}
        </div>

        <p className="mt-5 text-sm leading-relaxed text-muted-fg">
          {t('landing.pricingNoteLead')}{' '}
          <Link to="/terms" className="font-semibold text-primary underline underline-offset-4">
            {t('footer.termsLink')}
          </Link>
          .
        </p>
      </section>
    </PublicContainer>
  )
}
