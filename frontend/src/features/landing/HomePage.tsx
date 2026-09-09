import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  CarFront,
  ShieldCheck,
  MessageSquare,
  QrCode,
  ArrowRight,
  ScanLine,
  BadgeEuro,
  Gem,
  Building2,
  Mail,
  Clock,
  Sparkles,
} from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'
import { cn } from '@/lib/cn'

/** Pricing plan shape returned by `t('landing.plans', { returnObjects: true })`. */
interface Plan {
  name: string
  price: string
  period: string
  description: string
  cta: string
  features: string[]
}

/** About stat shape returned by `t('landing.aboutStats', { returnObjects: true })`. */
interface AboutStat {
  value: string
  label: string
}

const planIcons = [BadgeEuro, Gem, Building2] as const

/**
 * Public landing page — the marketing face of CarLink.
 * Flat/minimal per design direction; no owner data exposed.
 * Sections are anchored by the public navbar (/#home #pricing #about #contact).
 */
export default function HomePage() {
  const { t } = useTranslation()

  const steps = [
    {
      icon: QrCode,
      title: t('landing.how1Title'),
      text: t('landing.how1Text'),
    },
    {
      icon: MessageSquare,
      title: t('landing.how2Title'),
      text: t('landing.how2Text'),
    },
    {
      icon: ScanLine,
      title: t('landing.how3Title'),
      text: t('landing.how3Text'),
    },
  ]

  const plans = t('landing.plans', { returnObjects: true }) as unknown as Plan[]
  const stats = t('landing.aboutStats', { returnObjects: true }) as unknown as AboutStat[]

  return (
    <div>
      {/* Hero */}
      <section id="home" className="scroll-mt-20 max-w-4xl mx-auto text-center pt-16 pb-20 px-4">
        <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-6">
          <ShieldCheck className="h-4 w-4" aria-hidden />
          {t('landing.badge')}
        </span>
        <h1 className="text-4xl sm:text-5xl font-bold text-heading tracking-tight leading-tight max-w-3xl mx-auto">
          {t('landing.heroTitle')}
        </h1>
        <p className="text-lg text-muted-fg mt-5 max-w-2xl mx-auto">{t('landing.heroSubtitle')}</p>
        <div className="flex flex-col sm:flex-row items-center justify-center gap-3 mt-8">
          <Link to="/register" className={buttonClasses({ size: 'lg' })}>
            {t('landing.heroCta')}
            <ArrowRight className="h-5 w-5" aria-hidden />
          </Link>
          <Link to="/login" className={buttonClasses({ variant: 'outline', size: 'lg' })}>
            {t('landing.heroCtaSecondary')}
          </Link>
        </div>

        {/* Hero mock — a simple QR card */}
        <div className="mt-14 mx-auto max-w-xs">
          <div className="bg-surface border border-border rounded-2xl shadow-pop p-6 text-center">
            <div className="h-40 w-40 bg-white mx-auto rounded-xl border border-border p-3 flex items-center justify-center">
              <div className="grid grid-cols-4 gap-1">
                {Array.from({ length: 16 }).map((_, i) => (
                  <div
                    key={i}
                    className={`h-4 w-4 ${i % 3 === 0 ? 'bg-heading' : i % 5 === 0 ? 'bg-primary' : 'bg-heading/80'}`}
                  />
                ))}
              </div>
            </div>
            <p className="mt-4 font-semibold text-heading">CarLink</p>
            <p className="text-xs text-muted-fg mt-1">{t('app.tagline')}</p>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="bg-surface border-y border-border py-16">
        <div className="max-w-5xl mx-auto px-4">
          <h2 className="text-2xl sm:text-3xl font-bold text-heading text-center mb-12">{t('landing.howTitle')}</h2>
          <div className="grid gap-6 sm:grid-cols-3">
            {steps.map(({ icon: Icon, title, text }) => (
              <div key={title} className="bg-background rounded-xl border border-border p-6">
                <div className="h-12 w-12 rounded-lg bg-primary-soft text-primary flex items-center justify-center mb-4">
                  <Icon className="h-6 w-6" aria-hidden />
                </div>
                <h3 className="font-bold text-heading mb-1.5">{title}</h3>
                <p className="text-sm text-muted-fg leading-relaxed">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Pricing */}
      <section id="pricing" className="scroll-mt-20 py-20">
        <div className="max-w-5xl mx-auto px-4">
          <h2 className="text-2xl sm:text-3xl font-bold text-heading text-center">{t('landing.pricingTitle')}</h2>
          <p className="text-muted-fg mt-3 text-center max-w-xl mx-auto">{t('landing.pricingSubtitle')}</p>

          <div className="grid gap-6 md:grid-cols-3 mt-12 items-stretch">
            {plans.map((plan, i) => {
              const Icon = planIcons[i] ?? BadgeEuro
              const isPopular = i === 1
              return (
                <div
                  key={plan.name}
                  className={cn(
                    'relative flex flex-col bg-surface border rounded-2xl p-6 shadow-card',
                    isPopular ? 'border-accent ring-1 ring-accent' : 'border-border',
                  )}
                >
                  {isPopular && (
                    <span className="absolute -top-3 start-1/2 -translate-x-1/2 inline-flex items-center gap-1 rounded-full bg-accent text-white text-xs font-semibold px-3 py-1">
                      <Sparkles className="h-3.5 w-3.5" aria-hidden />
                      {t('landing.planPopular')}
                    </span>
                  )}
                  <div className="flex items-center gap-3 mb-2">
                    <div className="h-10 w-10 rounded-lg bg-primary-soft text-primary flex items-center justify-center">
                      <Icon className="h-5 w-5" aria-hidden />
                    </div>
                    <h3 className="font-bold text-heading">{plan.name}</h3>
                  </div>
                  <div className="flex items-baseline gap-1 mb-1">
                    <span className="text-3xl font-bold text-heading">{plan.price}</span>
                    {plan.period && <span className="text-sm text-muted-fg">{plan.period}</span>}
                  </div>
                  <p className="text-sm text-muted-fg mb-5">{plan.description}</p>
                  <ul className="space-y-2.5 mb-6 text-sm">
                    {plan.features.map((feature) => (
                      <li key={feature} className="flex items-center gap-2 text-foreground">
                        <span className="h-1.5 w-1.5 rounded-full bg-primary shrink-0" aria-hidden />
                        {feature}
                      </li>
                    ))}
                  </ul>
                  <div className="mt-auto">
                    <Link
                      to={i === 0 ? '/register' : i === 2 ? '#contact' : '/register'}
                      className={buttonClasses({ variant: isPopular ? 'primary' : 'outline', fullWidth: true })}
                    >
                      {plan.cta}
                    </Link>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </section>

      {/* About */}
      <section id="about" className="scroll-mt-20 bg-surface border-y border-border py-20">
        <div className="max-w-4xl mx-auto px-4">
          <h2 className="text-2xl sm:text-3xl font-bold text-heading text-center">{t('landing.aboutTitle')}</h2>
          <p className="text-muted-fg mt-5 leading-relaxed text-center max-w-2xl mx-auto">{t('landing.aboutText1')}</p>
          <p className="text-muted-fg mt-3 leading-relaxed text-center max-w-2xl mx-auto">{t('landing.aboutText2')}</p>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 mt-12">
            {stats.map((stat) => (
              <div key={stat.label} className="bg-background rounded-xl border border-border p-6 text-center">
                <p className="text-3xl font-bold text-primary">{stat.value}</p>
                <p className="text-sm text-muted-fg mt-1">{stat.label}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Contact */}
      <section id="contact" className="scroll-mt-20 py-20">
        <div className="max-w-2xl mx-auto px-4">
          <h2 className="text-2xl sm:text-3xl font-bold text-heading text-center">{t('landing.contactTitle')}</h2>
          <p className="text-muted-fg mt-3 text-center">{t('landing.contactSubtitle')}</p>

          <div className="mt-10 bg-surface border border-border rounded-2xl shadow-card p-8 text-center">
            <div className="h-14 w-14 rounded-2xl bg-primary-soft text-primary flex items-center justify-center mx-auto mb-4">
              <Mail className="h-7 w-7" aria-hidden />
            </div>
            <p className="text-sm text-muted-fg">{t('landing.contactEmailLabel')}</p>
            <a
              href="mailto:contact@carlink.app"
              className="block text-lg font-bold text-primary hover:text-primary-hover mt-1"
            >
              contact@carlink.app
            </a>
            <p className="inline-flex items-center gap-1.5 text-sm text-muted-fg mt-4">
              <Clock className="h-4 w-4" aria-hidden />
              {t('landing.contactResponse')}
            </p>
          </div>
        </div>
      </section>

      {/* Privacy */}
      <section className="max-w-4xl mx-auto px-4 py-16 text-center">
        <div className="bg-primary-soft/60 border border-primary/20 rounded-2xl p-8">
          <ShieldCheck className="h-10 w-10 text-primary mx-auto mb-4" aria-hidden />
          <h2 className="text-2xl font-bold text-heading">{t('landing.privacyTitle')}</h2>
          <p className="text-muted-fg mt-3 max-w-xl mx-auto">{t('landing.privacyText')}</p>
        </div>
      </section>

      {/* Final CTA */}
      <section className="px-4 pb-20">
        <div className="max-w-3xl mx-auto bg-heading rounded-2xl text-white text-center p-10">
          <CarFront className="h-10 w-10 mx-auto mb-4 text-primary-muted" aria-hidden />
          <h2 className="text-2xl sm:text-3xl font-bold">{t('landing.ctaTitle')}</h2>
          <p className="text-white/80 mt-3">{t('landing.ctaSubtitle')}</p>
          <Link to="/register" className={buttonClasses({ variant: 'primary', size: 'lg' }) + ' mt-6'}>
            {t('landing.heroCta')}
          </Link>
        </div>
      </section>
    </div>
  )
}