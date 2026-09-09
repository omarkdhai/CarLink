import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { CarFront, ShieldCheck, MessageSquare, QrCode, ArrowRight, ScanLine } from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'

/**
 * Public landing page — the marketing face of CarLink.
 * Flat/minimal per design direction; no owner data exposed.
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

  return (
    <div>
      {/* Hero */}
      <section className="max-w-4xl mx-auto text-center pt-16 pb-20 px-4">
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