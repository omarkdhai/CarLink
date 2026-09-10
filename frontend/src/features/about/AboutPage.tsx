import { useTranslation } from 'react-i18next'
import {
  Heart,
  ShieldCheck,
  Users,
  Sparkles,
  Lightbulb,
  Target,
  Handshake,
  Eye,
} from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'
import { Link } from 'react-router-dom'

const stats = [
  { key: 'stats1Value', label: 'stats1Label', icon: ShieldCheck },
  { key: 'stats2Value', label: 'stats2Label', icon: Users },
  { key: 'stats3Value', label: 'stats3Label', icon: Eye },
  { key: 'stats4Value', label: 'stats4Label', icon: Sparkles },
]

const values = [
  { icon: Heart, title: 'value1Title', text: 'value1Text' },
  { icon: Lightbulb, title: 'value2Title', text: 'value2Text' },
  { icon: Target, title: 'value3Title', text: 'value3Text' },
  { icon: Handshake, title: 'value4Title', text: 'value4Text' },
]

export default function AboutPage() {
  const { t } = useTranslation()

  return (
    <div>
      {/* Hero */}
      <section className="mt-[-40px] py-20 sm:py-28">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 text-center">
          <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-6">
            <ShieldCheck className="h-4 w-4" aria-hidden />
            {t('about.badge')}
          </span>
          <h1 className="text-3xl sm:text-5xl font-extrabold text-heading leading-tight tracking-tight">
            {t('about.heroTitle')}
          </h1>
          <p className="text-muted-fg mt-5 max-w-2xl mx-auto text-lg">
            {t('about.heroSubtitle')}
          </p>
          <Link
            to="/register"
            className={buttonClasses({ size: 'lg' }) + ' mt-8 inline-flex'}
          >
            {t('about.heroCta')}
          </Link>
        </div>
      </section>

      {/* Statistics */}
      <section className="py-16 sm:py-20">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          <div className="text-center max-w-2xl mx-auto mb-12">
            <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
              {t('about.statsTitle')}
            </h2>
          </div>
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-5">
            {stats.map(({ key, label, icon: Icon }) => (
              <div
                key={key}
                className="group bg-white rounded-2xl border border-border p-6 text-center hover:border-primary/40 hover:shadow-card transition-all"
              >
                <div className="h-12 w-12 rounded-xl bg-primary-soft text-primary flex items-center justify-center mx-auto mb-4 group-hover:bg-primary group-hover:text-white transition-colors">
                  <Icon className="h-6 w-6" aria-hidden />
                </div>
                <p className="text-3xl font-extrabold text-heading">
                  {t(`about.${key}`)}
                </p>
                <p className="text-sm text-muted-fg mt-1">{t(`about.${label}`)}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Origin story */}
      <section className="py-16 sm:py-24">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 grid lg:grid-cols-2 gap-10 lg:gap-16 items-center">
          <div>
            <span className="inline-flex items-center gap-2 rounded-full bg-accent/10 text-accent text-sm font-semibold px-3 py-1 mb-4">
              <Lightbulb className="h-4 w-4" aria-hidden />
              {t('about.originBadge')}
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
              {t('about.originTitle')}
            </h2>
          </div>
          <div className="space-y-4">
            <p className="text-muted-fg leading-relaxed">{t('about.originText1')}</p>
            <p className="text-muted-fg leading-relaxed">{t('about.originText2')}</p>
          </div>
        </div>
      </section>

      {/* Values */}
      <section className="py-16 sm:py-24">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          <div className="text-center max-w-2xl mx-auto mb-14">
            <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
              <Heart className="h-4 w-4" aria-hidden />
              {t('about.valuesBadge')}
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
              {t('about.valuesTitle')}
            </h2>
            <p className="text-muted-fg mt-3">{t('about.valuesSubtitle')}</p>
          </div>
          <div className="grid gap-5 sm:grid-cols-2">
            {values.map(({ icon: Icon, title, text }) => (
              <div
                key={title}
                className="group bg-white rounded-2xl border border-border p-7 hover:border-primary/40 hover:shadow-card transition-all"
              >
                <div className="h-12 w-12 rounded-xl bg-primary-soft text-primary flex items-center justify-center mb-4 group-hover:bg-primary group-hover:text-white transition-colors">
                  <Icon className="h-6 w-6" aria-hidden />
                </div>
                <h3 className="font-bold text-heading text-lg mb-1.5">{t(`about.${title}`)}</h3>
                <p className="text-sm text-muted-fg leading-relaxed">{t(`about.${text}`)}</p>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  )
}