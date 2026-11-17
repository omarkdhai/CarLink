import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ArrowRight, HeartHandshake, Lightbulb, ShieldCheck, Sparkles, Users } from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'
import { PageIntro, PublicContainer } from '@/components/shared/PublicPage'
import { CountUpText } from '@/components/shared/CountUp'

const stats = [
  { key: 'stats1Value', label: 'stats1Label' },
  { key: 'stats2Value', label: 'stats2Label' },
  { key: 'stats3Value', label: 'stats3Label' },
  { key: 'stats4Value', label: 'stats4Label' },
]

const values = [
  { title: 'value1Title', text: 'value1Text', icon: ShieldCheck },
  { title: 'value2Title', text: 'value2Text', icon: Sparkles },
  { title: 'value3Title', text: 'value3Text', icon: HeartHandshake },
  { title: 'value4Title', text: 'value4Text', icon: Users },
]

export default function AboutPage() {
  const { t } = useTranslation()

  return (
    <PublicContainer>
      <div className="grid gap-10 lg:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)] lg:items-end lg:gap-16">
        <PageIntro eyebrow={t('about.badge')} title={t('about.heroTitle')} lead={t('about.heroSubtitle')}>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/order?plan=SINGLE" className={buttonClasses({ size: 'lg' }) + ' group'}>
              {t('about.heroCta')}
              <ArrowRight className="nudge h-4 w-4 rtl:rotate-180" aria-hidden />
            </Link>
            <Link to="/contact" className={buttonClasses({ size: 'lg', variant: 'outline' })}>
              {t('navbar.contact')}
            </Link>
          </div>
        </PageIntro>

        {/* Figures */}
        <section aria-labelledby="stats-title" className="mb-10 sm:mb-12">
          <h2 id="stats-title" className="sr-only">
            {t('about.statsTitle')}
          </h2>
          <dl className="reveal-stagger grid grid-cols-2 gap-3">
            {stats.map(({ key, label }) => (
              <div key={key} className="hover-lift rounded-2xl border border-border bg-surface p-5 shadow-card">
                <dt className="sr-only">{t(`about.${label}`)}</dt>
                <dd>
                  <span className="block font-display text-3xl font-black tracking-tight text-heading"><CountUpText text={t(`about.${key}`)} />
                  </span>
                  <span className="mt-1.5 block text-sm leading-snug text-muted-fg" aria-hidden>
                    {t(`about.${label}`)}
                  </span>
                </dd>
              </div>
            ))}
          </dl>
        </section>
      </div>

      {/* Origin story */}
      <section aria-labelledby="origin-title" className="rounded-3xl bg-primary-soft/60 p-6 sm:p-10">
        <div className="grid gap-6 lg:grid-cols-[auto_minmax(0,1fr)] lg:gap-10">
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-surface text-primary shadow-card" aria-hidden>
            <Lightbulb className="h-6 w-6" />
          </span>
          <div className="max-w-[65ch]">
            <p className="text-xs font-semibold uppercase tracking-[0.12em] text-primary rtl:tracking-normal">{t('about.originBadge')}</p>
            <h2 id="origin-title" className="mt-2 text-2xl font-bold text-heading sm:text-3xl">
              {t('about.originTitle')}
            </h2>
            <p className="mt-4 text-base leading-relaxed text-foreground/80 sm:text-lg">{t('about.originText1')}</p>
            <p className="mt-4 text-base leading-relaxed text-foreground/80 sm:text-lg">{t('about.originText2')}</p>
          </div>
        </div>
      </section>

      {/* Values */}
      <section aria-labelledby="values-title" className="mt-14 sm:mt-16">
        <p className="text-xs font-semibold uppercase tracking-[0.12em] text-primary rtl:tracking-normal">{t('about.valuesBadge')}</p>
        <h2 id="values-title" className="mt-2 text-2xl font-bold text-heading sm:text-3xl">
          {t('about.valuesTitle')}
        </h2>
        <p className="mt-3 max-w-[60ch] text-base leading-relaxed text-muted-fg">{t('about.valuesSubtitle')}</p>
        <ul className="reveal-stagger mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {values.map(({ title, text, icon: Icon }) => (
            <li key={title} className="hover-lift rounded-2xl border border-border bg-surface p-6 shadow-card">
              <span className="grid h-10 w-10 place-items-center rounded-xl bg-primary-soft text-primary" aria-hidden>
                <Icon className="h-5 w-5" />
              </span>
              <h3 className="mt-4 text-lg font-bold text-heading">{t(`about.${title}`)}</h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-fg">{t(`about.${text}`)}</p>
            </li>
          ))}
        </ul>
      </section>
    </PublicContainer>
  )
}
