import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Check, DollarSign, Gauge, Lock, Bell, Smartphone } from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'

const packs = [
  {
    key: 'single',
    price: '20',
    badge: null,
  },
  {
    key: 'double',
    price: '35',
    badge: 'landing.pricingPack2Badge',
  },
  {
    key: 'business',
    price: '350',
    badge: 'landing.pricingPack3Badge',
  },
]

const features = [
  'landing.pricingFeature1',
  'landing.pricingFeature2',
  'landing.pricingFeature3',
  'landing.pricingFeature4',
  'landing.pricingFeature5',
]

export default function PricingPage() {
  const { t } = useTranslation()

  return (
    <div className="min-h-screen mt-[-40px]">
      {/* Header */}
      <section className="py-16 sm:py-20">
        <div className="max-w-4xl mx-auto px-4 sm:px-6 text-center">
          <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
            <DollarSign className="h-4 w-4" aria-hidden />
            {t('landing.pricingBadge')}
          </span>
          <h1 className="text-3xl sm:text-5xl font-extrabold text-heading leading-tight tracking-tight">
            {t('landing.pricingTitle')}
          </h1>
          <p className="text-muted-fg mt-5 max-w-2xl mx-auto text-lg">
            {t('landing.pricingSubtitle')}
          </p>
        </div>
      </section>

      {/* Pricing Cards */}
      <section className="py-16 sm:py-20">
        <div className="max-w-5xl mx-auto px-4 sm:px-6">
          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {packs.map((pack) => (
              <div
                key={pack.key}
                className={`relative bg-white rounded-2xl border-2 p-6 sm:p-8 flex flex-col ${
                  pack.key === 'double'
                    ? 'border-primary shadow-pop'
                    : 'border-border'
                }`}
              >
                {pack.badge && (
                  <div className="absolute -top-3 left-1/2 -translate-x-1/2">
                    <span className="inline-flex items-center gap-1 rounded-full bg-primary text-white text-xs font-semibold px-3 py-1">
                      {t(pack.badge)}
                    </span>
                  </div>
                )}

                <h2 className="text-lg font-semibold text-heading">
                  {t(`landing.pricingPack${pack.key === 'single' ? '1' : pack.key === 'double' ? '2' : '3'}Name`)}
                </h2>
                <p className="text-sm text-muted-fg mt-1">
                  {t(`landing.pricingPack${pack.key === 'single' ? '1' : pack.key === 'double' ? '2' : '3'}Subtitle`)}
                </p>

                <div className="mt-6 mb-6">
                  <span className="text-4xl font-bold text-heading">{pack.price}</span>
                  <span className="text-lg font-medium text-muted-fg ml-1">TND</span>
                </div>

                <ul className="space-y-3 mb-8 flex-1">
                  {features.map((f) => (
                    <li key={f} className="flex items-start gap-2 text-sm text-heading">
                      <Check className="h-5 w-5 text-primary shrink-0 mt-0.5" aria-hidden />
                      {t(f)}
                    </li>
                  ))}
                </ul>

                <Link
                  to="/register"
                  className={buttonClasses({
                    variant: pack.key === 'double' ? 'primary' : 'outline',
                    size: 'lg',
                  }) + ' w-full justify-center'}
                >
                  {t('landing.pricingCta')}
                </Link>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* Advantages Section — same as homepage */}
      <section className="py-16 sm:py-24">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          {/* Header */}
          <div className="text-center max-w-2xl mx-auto mb-14">
            <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
              <Gauge className="h-4 w-4" aria-hidden />
              {t('landing.advBadge')}
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
              {t('landing.advTitle')}
            </h2>
            <p className="text-muted-fg mt-3">
              {t('landing.advSubtitle')}
            </p>
          </div>

          {/* Advantages grid */}
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {/* Advantage 1: Privacy */}
            <div className="group bg-background rounded-2xl border border-border p-6 hover:border-primary/40 hover:shadow-card transition-all">
              <div className="h-12 w-12 rounded-xl bg-primary-soft text-primary flex items-center justify-center mb-4 group-hover:bg-primary group-hover:text-white transition-colors">
                <Lock className="h-6 w-6" aria-hidden />
              </div>
              <h3 className="font-bold text-heading text-lg mb-1.5">{t('landing.adv1Title')}</h3>
              <p className="text-sm text-muted-fg leading-relaxed">{t('landing.adv1Text')}</p>
            </div>

            {/* Advantage 2: Instant alert */}
            <div className="group bg-background rounded-2xl border border-border p-6 hover:border-primary/40 hover:shadow-card transition-all">
              <div className="h-12 w-12 rounded-xl bg-primary-soft text-primary flex items-center justify-center mb-4 group-hover:bg-primary group-hover:text-white transition-colors">
                <Bell className="h-6 w-6" aria-hidden />
              </div>
              <h3 className="font-bold text-heading text-lg mb-1.5">{t('landing.adv2Title')}</h3>
              <p className="text-sm text-muted-fg leading-relaxed">{t('landing.adv2Text')}</p>
            </div>

            {/* Advantage 3: No app for visitors */}
            <div className="group bg-background rounded-2xl border border-border p-6 hover:border-primary/40 hover:shadow-card transition-all">
              <div className="h-12 w-12 rounded-xl bg-primary-soft text-primary flex items-center justify-center mb-4 group-hover:bg-primary group-hover:text-white transition-colors">
                <Smartphone className="h-6 w-6" aria-hidden />
              </div>
              <h3 className="font-bold text-heading text-lg mb-1.5">{t('landing.adv3Title')}</h3>
              <p className="text-sm text-muted-fg leading-relaxed">{t('landing.adv3Text')}</p>
            </div>
          </div>
        </div>
      </section>
    </div>
  )
}
