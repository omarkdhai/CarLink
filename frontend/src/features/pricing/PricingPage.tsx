import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Check } from 'lucide-react'

const CURRENCY = 'TND'

const plans = [
  {
    key: 'SINGLE',
    n: 1,
    price: '20',
    compareAt: null,
    badge: null,
    featured: false,
    features: [
      'landing.pricingPlan1Feature1',
      'landing.pricingPlan1Feature2',
      'landing.pricingPlan1Feature3',
    ],
  },
  {
    key: 'DOUBLE',
    n: 2,
    price: '35',
    compareAt: '40',
    badge: 'landing.pricingPack2Badge',
    featured: true,
    features: [
      'landing.pricingPlan2Feature1',
      'landing.pricingPlan2Feature2',
      'landing.pricingPlan2Feature3',
    ],
  },
  {
    key: 'BUSINESS',
    n: 3,
    price: '350',
    compareAt: '400',
    badge: null,
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
  const { t } = useTranslation()

  const compareRows = [
    {
      label: t('landing.pricingComparePrice'),
      values: plans.map(
        (plan) => `${plan.price} ${CURRENCY} / ${t('landing.pricingPerYear')}`,
      ),
    },
    {
      label: t('landing.pricingCompareRenewal'),
      values: plans.map(
        (plan) => `${plan.price} ${CURRENCY} / ${t('landing.pricingPerYear')}`,
      ),
    },
    {
      label: t('landing.pricingCompareDelivery'),
      values: [
        t('landing.pricingCompareDeliveryBasic'),
        t('landing.pricingCompareDeliveryBoth'),
        t('landing.pricingCompareDeliveryBoth'),
      ],
    },
    {
      label: t('landing.pricingCompareRelay'),
      values: plans.map(() => t('landing.pricingCompareIncluded')),
    },
    {
      label: t('landing.pricingCompareNewSticker'),
      values: plans.map(() => t('landing.pricingCompareIncluded')),
    },
  ]

  return (
    <div className="pb-16 pt-8 sm:pb-20 sm:pt-12">
      <div className="mx-auto max-w-6xl px-4 sm:px-9">
        <div className="overflow-hidden rounded-[1.5rem] border border-border bg-white shadow-[0_8px_24px_rgba(15,23,42,0.06)] md:rounded-[2rem]">
          <div className="flex flex-col gap-6 px-5 py-6 sm:px-6 sm:py-7 md:gap-8 md:px-8 md:py-10 lg:gap-10 lg:px-12 lg:py-14 xl:px-14 xl:py-16">
            {/* Header */}
            <header className="flex flex-col items-center text-center">
              <div className="max-w-3xl space-y-3.5 md:space-y-4 lg:space-y-5">
                <p className="text-[0.72rem] font-semibold uppercase tracking-[0.14em] text-primary/70 rtl:tracking-[0.04em]">
                  {t('landing.pricingBadge')}
                </p>
                <h1 className="text-[clamp(1.875rem,7vw,3.75rem)] font-black leading-[1.05] tracking-tight text-heading">
                  {t('landing.pricingTitle')}
                </h1>
                <p className="mx-auto max-w-2xl text-sm leading-7 text-muted-fg sm:text-base md:text-lg">
                  {t('landing.pricingSubtitle')}
                </p>
              </div>
            </header>

            <div className="space-y-8">
              {/* Plans */}
              <section className="space-y-4">
                <div className="grid gap-4 lg:grid-cols-3">
                  {plans.map((plan) => (
                    <article
                      key={plan.key}
                      className={`relative flex h-full flex-col rounded-[1.75rem] border bg-white p-5 shadow-[0_12px_40px_rgba(15,23,42,0.07)] sm:p-6 ${
                        plan.featured
                          ? 'border-primary shadow-[0_18px_50px_rgba(37,99,235,0.14)]'
                          : 'border-border'
                      }`}
                    >
                      {plan.badge && (
                        <span className="absolute -top-3 start-5 rounded-full bg-primary px-3 py-1 text-xs font-black text-white">
                          {t(plan.badge)}
                        </span>
                      )}

                      <h2 className="font-sans text-sm font-bold text-primary">
                        {t(`landing.pricingPlan${plan.n}Name`)}
                      </h2>

                      <div className="mt-3">
                        {plan.compareAt && (
                          <span className="mb-1.5 block text-sm font-medium text-muted-fg line-through">
                            {plan.compareAt} {CURRENCY}
                          </span>
                        )}
                        <div className="flex items-baseline gap-2">
                          <span className="text-4xl font-black tracking-tight text-heading">
                            {plan.price}
                          </span>
                          <span className="pb-1 text-sm font-medium text-muted-fg">
                            {CURRENCY} / {t('landing.pricingPerYear')}
                          </span>
                        </div>
                      </div>

                      <p className="mt-5 min-h-12 text-sm leading-6 text-muted-fg">
                        {t(`landing.pricingPack${plan.n}Subtitle`)}
                      </p>

                      <ul className="mt-5 flex-1 space-y-3 text-sm text-heading">
                        {plan.features.map((feature) => (
                          <li key={feature} className="flex items-start gap-2.5">
                            <Check className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden />
                            <span>{t(feature)}</span>
                          </li>
                        ))}
                      </ul>

                      <div className="pt-7">
                        <Link
                          to={`/order?plan=${plan.key}`}
                          className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-full bg-primary text-sm font-semibold text-white shadow-[0_10px_30px_rgba(37,99,235,0.18)] transition-all hover:bg-primary-hover hover:shadow-[0_14px_36px_rgba(37,99,235,0.26)] active:scale-[0.98]"
                        >
                          {t('landing.pricingCta')}
                        </Link>
                      </div>
                    </article>
                  ))}
                </div>

                <p className="rounded-xl bg-muted px-4 py-3 text-center text-xs leading-5 text-muted-fg sm:text-sm">
                  {t('landing.pricingNoteLead')}{' '}
                  <Link
                    to="/terms"
                    className="font-semibold text-primary underline underline-offset-4"
                  >
                    {t('footer.termsLink')}
                  </Link>
                  .
                </p>
              </section>

              {/* Comparison */}
              <section className="overflow-hidden rounded-[1.75rem] border border-border bg-primary-soft/50">
                <div className="border-b border-border px-5 py-4 sm:px-6">
                  <h2 className="font-sans text-lg font-semibold text-heading">
                    {t('landing.pricingCompareTitle')}
                  </h2>
                </div>

                <div className="hidden gap-px bg-border md:grid md:grid-cols-4">
                  <div className="bg-white px-4 py-4 text-sm font-semibold text-heading">
                    {t('landing.pricingCompareFeature')}
                  </div>
                  {plans.map((plan) => (
                    <div
                      key={plan.key}
                      className="bg-white px-4 py-4 text-sm font-semibold text-heading"
                    >
                      {t(`landing.pricingPlan${plan.n}Name`)}
                    </div>
                  ))}
                  {compareRows.map((row) => (
                    <div key={row.label} className="contents">
                      <div className="bg-white px-4 py-4 text-sm text-muted-fg">{row.label}</div>
                      {row.values.map((value, i) => (
                        <div key={i} className="bg-white px-4 py-4 text-sm text-heading">
                          {value}
                        </div>
                      ))}
                    </div>
                  ))}
                </div>

                <div className="grid gap-3 p-3 sm:p-4 md:hidden">
                  {plans.map((plan, i) => (
                    <article
                      key={plan.key}
                      className="overflow-hidden rounded-2xl border border-border bg-white"
                    >
                      <div className="flex items-center justify-between gap-3 border-b border-border bg-primary-soft px-4 py-3.5">
                        <h3 className="text-lg font-semibold text-heading">
                          {t(`landing.pricingPlan${plan.n}Name`)}
                        </h3>
                        {plan.badge && (
                          <span className="rounded-full bg-primary px-3 py-1 text-xs font-black text-white">
                            {t(plan.badge)}
                          </span>
                        )}
                      </div>
                      <dl className="divide-y divide-border px-4">
                        {compareRows.map((row) => (
                          <div
                            key={row.label}
                            className="flex items-center justify-between gap-4 py-3.5"
                          >
                            <dt className="text-sm text-muted-fg">{row.label}</dt>
                            <dd className="text-end text-sm font-semibold text-heading">
                              {row.values[i]}
                            </dd>
                          </div>
                        ))}
                      </dl>
                    </article>
                  ))}
                </div>
              </section>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
