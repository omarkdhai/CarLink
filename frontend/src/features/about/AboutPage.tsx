import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'

const KICKER =
  'text-[0.72rem] font-semibold uppercase leading-4 tracking-[0.14em] text-primary/70 rtl:tracking-[0.04em]'

const stats = [
  { key: 'stats1Value', label: 'stats1Label' },
  { key: 'stats2Value', label: 'stats2Label' },
  { key: 'stats3Value', label: 'stats3Label' },
  { key: 'stats4Value', label: 'stats4Label' },
]

const values = [
  { title: 'value1Title', text: 'value1Text' },
  { title: 'value2Title', text: 'value2Text' },
  { title: 'value3Title', text: 'value3Text' },
  { title: 'value4Title', text: 'value4Text' },
]

export default function AboutPage() {
  const { t } = useTranslation()

  return (
    <div className="pt-12 pb-16 sm:pt-16 sm:pb-20 lg:pb-24">
      <div className="mx-auto w-[min(1120px,calc(100%_-_1.5rem))]">
        <div className="overflow-hidden rounded-[1.5rem] border border-[#f1f5f9] bg-white shadow-[0_8px_24px_rgba(26,26,26,0.06)] md:rounded-[2rem]">
          <div className="flex flex-col gap-6 px-5 py-6 sm:px-6 sm:py-7 md:gap-8 md:px-8 md:py-10 lg:gap-10 lg:px-12 lg:py-14 xl:px-14 xl:py-16">
            {/* Header */}
            <div className="max-w-3xl space-y-3 lg:space-y-4">
              <p className={KICKER}>{t('about.badge')}</p>
              <h1 className="text-3xl font-bold tracking-tight text-heading sm:text-4xl md:text-6xl">
                {t('about.heroTitle')}
              </h1>
              <p className="max-w-2xl text-sm leading-7 text-muted-fg sm:text-base md:text-lg">
                {t('about.heroSubtitle')}
              </p>
              <div className="pt-2">
                <Link
                  to="/order?plan=single"
                  className="inline-flex min-h-12 items-center justify-center gap-2 rounded-full bg-primary px-6 text-sm font-semibold text-white shadow-[0_8px_24px_rgba(37,99,235,0.24)] transition-colors hover:bg-primary-hover focus-visible:outline-none"
                >
                  {t('about.heroCta')}
                  <ArrowRight className="h-4 w-4 rtl:rotate-180" aria-hidden />
                </Link>
              </div>
            </div>

            <article className="mx-auto w-full max-w-3xl space-y-10 text-start">
              {/* Statistics */}
              <section className="space-y-5">
                <h2 className="text-lg font-semibold tracking-tight text-heading sm:text-xl">
                  {t('about.statsTitle')}
                </h2>
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                  {stats.map(({ key, label }) => (
                    <div key={key} className="rounded-2xl bg-primary-soft/60 p-5">
                      <p className="text-2xl font-semibold tracking-[-0.03em] text-heading sm:text-3xl">
                        {t(`about.${key}`)}
                      </p>
                      <p className="mt-1 text-xs leading-5 text-muted-fg">{t(`about.${label}`)}</p>
                    </div>
                  ))}
                </div>
              </section>

              {/* Origin story */}
              <section className="space-y-3">
                <p className={KICKER}>{t('about.originBadge')}</p>
                <h2 className="text-lg font-semibold tracking-tight text-heading sm:text-xl">
                  {t('about.originTitle')}
                </h2>
                <p className="text-sm leading-7 text-muted-fg sm:text-base">
                  {t('about.originText1')}
                </p>
                <p className="text-sm leading-7 text-muted-fg sm:text-base">
                  {t('about.originText2')}
                </p>
              </section>

              {/* Values */}
              <section className="space-y-3">
                <p className={KICKER}>{t('about.valuesBadge')}</p>
                <h2 className="text-lg font-semibold tracking-tight text-heading sm:text-xl">
                  {t('about.valuesTitle')}
                </h2>
                <p className="text-sm leading-7 text-muted-fg sm:text-base">
                  {t('about.valuesSubtitle')}
                </p>
                <div className="grid gap-3 pt-2 sm:grid-cols-2">
                  {values.map(({ title, text }) => (
                    <div key={title} className="rounded-2xl border border-border bg-background p-5">
                      <h3 className="text-sm font-semibold text-heading">{t(`about.${title}`)}</h3>
                      <p className="mt-1.5 text-sm leading-7 text-muted-fg">{t(`about.${text}`)}</p>
                    </div>
                  ))}
                </div>
              </section>
            </article>
          </div>
        </div>
      </div>
    </div>
  )
}
