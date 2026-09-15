import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  CarFront,
  ShieldCheck,
  MessageSquare,
  QrCode,
  ArrowRight,
  ScanLine,
  Truck,
  Lock,
  AlertTriangle,
  Lightbulb,
  Zap,
  ShoppingCart,
  MessageCircleWarning,
  Smartphone,
  Check,
  Users,
  Bell,
  Gauge,
  Clock,
  Star,
  Signal,
  Wifi,
  ChevronDown,
  HelpCircle,
} from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'

const problems = [
  { icon: CarFront, title: 'landing.problem1Title', text: 'landing.problem1Text' },
  { icon: MessageCircleWarning, title: 'landing.problem2Title', text: 'landing.problem2Text' },
  { icon: Lightbulb, title: 'landing.problem3Title', text: 'landing.problem3Text' },
  { icon: AlertTriangle, title: 'landing.problem4Title', text: 'landing.problem4Text' },
  { icon: ShoppingCart, title: 'landing.problem5Title', text: 'landing.problem5Text' },
  { icon: Zap, title: 'landing.problem6Title', text: 'landing.problem6Text' },
] as const

const faqs = ['faq1', 'faq2', 'faq3', 'faq4', 'faq5'] as const

/**
 * Public landing page — the marketing face of CarLink.
 * Flat/minimal per design direction; no owner data exposed.
 */
export default function HomePage() {
  const { t } = useTranslation()
  // Only used on touch devices: a tap toggles a card's details (tap = hover).
  const [activeProblem, setActiveProblem] = useState<number | null>(null)
  // FAQ accordion: which question is open (one at a time).
  const [openFaq, setOpenFaq] = useState<number | null>(null)

  return (
    <div>
      {/* Hero - White split layout */}
      <section id="home" className="scroll-mt-20 mt-[-40px]">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 py-16 sm:py-24">
          <div className="grid lg:grid-cols-2 gap-12 lg:gap-16 items-center">
            {/* Left column - Content */}
            <div className="text-center lg:text-left">
              <h1 className="text-3xl sm:text-4xl lg:text-5xl font-extrabold text-heading leading-tight tracking-tight">
                {t('landing.heroTitle')}
              </h1>
              <p className="text-lg text-muted-fg mt-5 max-w-xl">
                {t('landing.heroSubtitle')}
              </p>
              <div className="flex flex-col sm:flex-row items-center justify-center lg:justify-start gap-3 mt-8">
                <Link to="/order?plan=single" className={buttonClasses({ size: 'lg' })}>
                  {t('landing.heroCta')}
                  <ArrowRight className="h-5 w-5" aria-hidden />
                </Link>
                <Link to="/#how" className={buttonClasses({ variant: 'outline', size: 'lg' })}>
                  {t('landing.heroCtaSecondary')}
                </Link>
              </div>

              {/* Trust indicators */}
              <div className="grid grid-cols-3 gap-2 sm:gap-4 mt-10 pt-8 border-t border-border">
                <div className="flex flex-col sm:flex-row items-center gap-1 sm:gap-2 text-center sm:text-left lg:justify-start">
                  <Lock className="h-4 w-4 text-primary shrink-0" aria-hidden />
                  <span className="text-xs sm:text-sm font-medium leading-tight">{t('landing.trust1')}</span>
                </div>
                <div className="flex flex-col sm:flex-row items-center gap-1 sm:gap-2 text-center sm:text-left lg:justify-start">
                  <ShieldCheck className="h-4 w-4 text-primary shrink-0" aria-hidden />
                  <span className="text-xs sm:text-sm font-medium leading-tight">{t('landing.trust2')}</span>
                </div>
                <div className="flex flex-col sm:flex-row items-center gap-1 sm:gap-2 text-center sm:text-left lg:justify-start">
                  <Clock className="h-4 w-4 text-primary shrink-0" aria-hidden />
                  <span className="text-xs sm:text-sm font-medium leading-tight">{t('landing.trust3')}</span>
                </div>
              </div>
            </div>

            {/* Right column - Car with QR visual */}
            <div className="relative">
              {/* Car silhouette with windshield */}
              <div className="relative mx-auto max-w-sm">
                {/* Car body */}
                <div className="bg-gradient-to-b from-primary to-primary/80 rounded-3xl p-8 pb-16 relative">
                  {/* Windshield area */}
                  <div className="bg-white/20 backdrop-blur-sm rounded-2xl p-6 border border-white/30">
                    <div className="flex items-center justify-center">
                      {/* QR Code mockup */}
                      <div className="bg-white rounded-xl p-4 shadow-lg">
                        <div className="grid grid-cols-5 gap-1.5">
                          {Array.from({ length: 25 }).map((_, i) => (
                            <div
                              key={i}
                              className={`h-3 w-3 ${[0,4,6,8,12,14,18,20,22,24].includes(i) ? 'bg-heading' : [1,2,3,5,9,10,15,16,17,19,21,23].includes(i) ? 'bg-primary' : 'bg-heading/60'}`}
                            />
                          ))}
                        </div>
                      </div>
                    </div>
                    <p className="text-center text-white text-sm mt-4 font-medium">
                      {t('landing.qrScanText')}
                    </p>
                  </div>
                </div>

                {/* Floating card overlay */}
                <div className="absolute -bottom-6 right-2 sm:right-8 bg-white rounded-xl shadow-xl p-4 border border-border">
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-lg bg-primary-soft text-primary flex items-center justify-center">
                      <QrCode className="h-5 w-5" aria-hidden />
                    </div>
                    <div>
                      <p className="font-semibold text-heading text-sm">CarLink</p>
                      <p className="text-xs text-muted-fg">{t('app.tagline')}</p>
                    </div>
                  </div>
                </div>

                {/* Car icon accent */}
                <div className="absolute -top-4 start-0 sm:-start-8">
                  <div className="h-12 w-12 rounded-full bg-primary/10 flex items-center justify-center">
                    <Truck className="h-6 w-6 text-primary" aria-hidden />
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Problems Section */}
      <section className="py-16 sm:py-20">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          {/* Header */}
          <div className="text-center max-w-2xl mx-auto mb-12">
            <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
              <AlertTriangle className="h-4 w-4" aria-hidden />
              {t('landing.problemsBadge')}
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
              {t('landing.problemsTitle')}
            </h2>
            <p className="text-muted-fg mt-3">
              {t('landing.problemsSubtitle')}
            </p>
          </div>

          {/* Problem cards */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {problems.map(({ icon: Icon, title, text }, i) => (
              <div
                key={title}
                onClick={() => {
                  // On touch there is no hover, so a tap toggles the details.
                  if (window.matchMedia('(hover: none)').matches) {
                    setActiveProblem(activeProblem === i ? null : i)
                  }
                }}
                className={`group cursor-pointer select-none bg-background rounded-xl border border-border p-5 hover:shadow-card transition-all duration-900 ${
                  activeProblem === i ? 'shadow-card border-primary/40' : ''
                }`}
              >
                <div className="flex items-start gap-4">
                  <div className="h-10 w-10 rounded-lg bg-primary-soft text-primary flex items-center justify-center shrink-0">
                    <Icon className="h-5 w-5" aria-hidden />
                  </div>
                  <div>
                    <h3 className="font-semibold text-heading">{t(title)}</h3>
                    <p
                      className={`text-sm text-muted-fg mt-1 leading-relaxed max-h-0 overflow-hidden opacity-0 transition-all duration-700 ease-in-out ${
                        activeProblem === i ? 'max-h-40 opacity-100' : ''
                      } group-hover:max-h-40 group-hover:opacity-100`}
                    >
                      {t(text)}
                    </p>
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Bottom CTA */}
          <div className="text-center mt-10">
            <p className="text-muted-fg text-sm mb-4">{t('landing.problemsCtaText')}</p>
            <Link to="/order?plan=single" className={buttonClasses({ size: 'lg' })}>
              {t('landing.problemsCta')}
              <ArrowRight className="h-5 w-5" aria-hidden />
            </Link>
          </div>
        </div>
      </section>

      {/* How it works */}
      <section id="how" className="scroll-mt-20 py-16 sm:py-24">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          {/* Header */}
          <div className="text-center max-w-2xl mx-auto mb-16">
            <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
              <Smartphone className="h-4 w-4" aria-hidden />
              {t('landing.howBadge')}
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
              {t('landing.howTitle')}
            </h2>
            <p className="text-muted-fg mt-3">
              {t('landing.howSubtitle')}
            </p>
          </div>

          {/* Steps with visual flow */}
          <div className="relative">
            <div className="grid gap-8 lg:gap-12 lg:grid-cols-3">
              {/* Step 1 */}
              <div className="relative text-center">
                {/* Step number */}
                <div className="relative inline-flex mb-6">
                  <div className="h-16 w-16 rounded-2xl bg-primary text-white flex items-center justify-center text-2xl font-bold shadow-lg shadow-primary/25">
                    1
                  </div>
                  <div className="absolute -top-2 -right-2 h-6 w-6 rounded-full bg-background border-2 border-primary flex items-center justify-center">
                    <QrCode className="h-3 w-3 text-primary" aria-hidden />
                  </div>
                </div>

                {/* Content card */}
                <div className="bg-surface rounded-2xl border border-border p-6 shadow-card">
                  <div className="h-14 w-14 rounded-xl bg-primary-soft text-primary flex items-center justify-center mx-auto mb-4">
                    <QrCode className="h-7 w-7" aria-hidden />
                  </div>
                  <h3 className="font-bold text-heading text-lg mb-2">{t('landing.how1Title')}</h3>
                  <p className="text-sm text-muted-fg leading-relaxed">{t('landing.how1Text')}</p>
                </div>
              </div>

              {/* Step 2 */}
              <div className="relative text-center">
                {/* Step number */}
                <div className="relative inline-flex mb-6">
                  <div className="h-16 w-16 rounded-2xl bg-primary text-white flex items-center justify-center text-2xl font-bold shadow-lg shadow-primary/25">
                    2
                  </div>
                  <div className="absolute -top-2 -right-2 h-6 w-6 rounded-full bg-background border-2 border-primary flex items-center justify-center">
                    <Smartphone className="h-3 w-3 text-primary" aria-hidden />
                  </div>
                </div>

                {/* Content card */}
                <div className="bg-surface rounded-2xl border border-border p-6 shadow-card">
                  <div className="h-14 w-14 rounded-xl bg-primary-soft text-primary flex items-center justify-center mx-auto mb-4">
                    <ScanLine className="h-7 w-7" aria-hidden />
                  </div>
                  <h3 className="font-bold text-heading text-lg mb-2">{t('landing.how2Title')}</h3>
                  <p className="text-sm text-muted-fg leading-relaxed">{t('landing.how2Text')}</p>
                </div> 
              </div>

              {/* Step 3 */}
              <div className="relative text-center">
                {/* Step number */}
                <div className="relative inline-flex mb-6">
                  <div className="h-16 w-16 rounded-2xl bg-primary text-white flex items-center justify-center text-2xl font-bold shadow-lg shadow-primary/25">
                    3
                  </div>
                  <div className="absolute -top-2 -right-2 h-6 w-6 rounded-full bg-background border-2 border-primary flex items-center justify-center">
                    <MessageSquare className="h-3 w-3 text-primary" aria-hidden />
                  </div>
                </div>

                {/* Content card */}
                <div className="bg-surface rounded-2xl border border-border p-6 shadow-card">
                  <div className="h-14 w-14 rounded-xl bg-primary-soft text-primary flex items-center justify-center mx-auto mb-4">
                    <MessageSquare className="h-7 w-7" aria-hidden />
                  </div>
                  <h3 className="font-bold text-heading text-lg mb-2">{t('landing.how3Title')}</h3>
                  <p className="text-sm text-muted-fg leading-relaxed">{t('landing.how3Text')}</p>
                </div>
              </div>
            </div>
          </div>

          {/* Trust bar */}
          <div className="mt-16 bg-primary-soft/50 rounded-2xl p-6 sm:p-8">
            <div className="flex flex-col sm:flex-row items-center justify-between gap-6">
              <div className="flex items-center gap-4">
                <div className="h-12 w-12 rounded-xl bg-primary text-white flex items-center justify-center shrink-0">
                  <Check className="h-6 w-6" aria-hidden />
                </div>
                <div className="text-center sm:text-left">
                  <p className="font-semibold text-heading">{t('landing.howTrustTitle')}</p>
                  <p className="text-sm text-muted-fg">{t('landing.howTrustText')}</p>
                </div>
              </div>
              <div className="flex items-center gap-6 text-sm text-muted-fg">
                <div className="flex items-center gap-2">
                  <Users className="h-4 w-4 text-primary" aria-hidden />
                  <span>{t('landing.howEasyText')}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Clock className="h-4 w-4 text-primary" aria-hidden />
                  <span>{t('landing.howTimeText')}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Advantages Section */}
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

      {/* Ways to reach the owner */}
      <section className="py-16 sm:py-24">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          <div className="text-center max-w-2xl mx-auto mb-14">
            <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
              <Bell className="h-4 w-4" aria-hidden />
              {t('landing.reachBadge')}
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
              {t('landing.reachTitle')}
            </h2>
            <p className="text-muted-fg mt-3">{t('landing.reachSubtitle')}</p>
          </div>

          <div className="grid gap-5 sm:grid-cols-2 max-w-4xl mx-auto">
            {/* SMS message */}
            <div className="group relative bg-white rounded-2xl border border-border p-7 hover:border-primary/40 hover:shadow-card transition-all">
              <span className="inline-block rounded-full bg-primary text-white text-xs font-semibold px-3 py-1 uppercase tracking-wide">
                {t('landing.reachSmsName')}
              </span>
              
              <p className="flex items-center mt-4 gap-2 text-sm font-medium text-heading">
                <Signal className="h-4 w-4 text-primary" aria-hidden />
                {t('landing.reachSmsSignal')}
              </p>
              <p className="text-sm text-muted-fg leading-relaxed mt-1">{t('landing.reachSmsText')}</p>
            </div>

            {/* Anonymous alert call */}
            <div className="group relative bg-white rounded-2xl border border-border p-7 hover:border-primary/40 hover:shadow-card transition-all">
              <span className="inline-block rounded-full bg-primary text-white text-xs font-semibold px-3 py-1 uppercase tracking-wide">
                {t('landing.reachCallName')}
              </span>
              
              <p className="flex items-center mt-4 gap-2 text-sm font-medium text-heading">
                <Wifi className="h-4 w-4 text-primary" aria-hidden />
                {t('landing.reachCallSignal')}
              </p>
              <p className="text-sm text-muted-fg leading-relaxed mt-1">{t('landing.reachCallText')}</p>
            </div>
          </div>
        </div>
      </section>

      {/* Reviews Carousel */}
      <section className="py-16 sm:py-20 overflow-hidden">
        <div className="max-w-6xl mx-auto px-4 sm:px-6">
          <div className="text-center mb-10">
            <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
              <Star className="h-4 w-4" aria-hidden />
              {t('landing.reviewsBadge')}
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
              {t('landing.reviewsTitle')}
            </h2>
          </div>

          {/* Auto-scrolling carousel */}
          <div className="relative">
            <div className="flex gap-6 animate-scroll" style={{ width: 'max-content' }}>
              {/* First set + duplicate for seamless loop */}
              {[1,2,3,4,5,6,1,2,3,4,5,6].map((i, idx) => (
                <div
                  key={idx}
                  className="flex-none w-80 bg-background rounded-xl border border-border p-6"
                >
                  <div className="flex gap-1 mb-3">
                    {[1,2,3,4,5].map((s) => (
                      <Star
                        key={s}
                        className="h-4 w-4 fill-accent text-accent"
                        aria-hidden
                      />
                    ))}
                  </div>
                  <p className="text-sm text-heading leading-relaxed mb-4">
                    {t(`landing.review${i}Text`)}
                  </p>
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-full bg-primary-soft text-primary flex items-center justify-center font-semibold">
                      {t(`landing.review${i}Author`)}
                    </div>
                    <div>
                      <p className="font-medium text-heading text-sm">
                        {t(`landing.review${i}Name`)}
                      </p>
                      <p className="text-xs text-muted-fg">
                        {t(`landing.review${i}Role`)}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* CTA — yellow banner */}
      <section className="px-4 pt-25 pb-20">
        <div className="max-w-5xl mx-auto bg-primary-soft rounded-3xl p-8 sm:p-10 md:p-12">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-8 items-center">
            {/* Left: heading + subtitle + CTA */}
            <div className="text-center md:text-left">
              <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
                {t('landing.finalSectionTitle')}
              </h2>
              <p className="text-sm sm:text-base text-heading/70 mt-3 max-w-md mx-auto md:mx-0">
                {t('landing.finalSectionSubtitle')}
              </p>
              <Link
                to="/order?plan=business"
                className={buttonClasses({ variant: 'primary', size: 'lg' }) + ' mt-6 inline-flex items-center gap-2 bg-heading text-white hover:bg-heading/90'}
              >
                {t('landing.finalSectionCta')}
                <ArrowRight className="h-4 w-4" aria-hidden />
              </Link>
            </div>

            {/* Right: pricing info card */}
            <div className="bg-white rounded-2xl border-2 border-gray-900 p-6 sm:p-7 text-center">
              <p className="text-sm text-muted-fg">
                {t('landing.finalSectionPriceLabel')}
              </p>
              <p className="text-2xl sm:text-3xl font-bold text-heading mt-1">
                {t('landing.finalSectionPriceValue')}
                <span className="text-base font-normal">{t('landing.finalSectionPricePer')}</span>
              </p>
              <p className="text-xs font-medium text-heading mt-4">
                {t('landing.finalSectionNote')}
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* FAQ */}
      <section className="py-16 sm:py-24">
        <div className="max-w-3xl mx-auto px-4 sm:px-6">
          <div className="text-center mb-12">
            <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
              <HelpCircle className="h-4 w-4" aria-hidden />
              {t('landing.faqBadge')}
            </span>
            <h2 className="text-2xl sm:text-3xl font-extrabold text-heading leading-tight">
              {t('landing.faqTitle')}
            </h2>
            <p className="text-muted-fg mt-3">
              {t('landing.faqSubtitle')}
            </p>
          </div>

          <div className="space-y-3">
            {faqs.map((key, i) => {
              const open = openFaq === i
              return (
                <div
                  key={key}
                  className={`bg-white rounded-2xl border transition-colors duration-300 ${
                    open ? 'border-primary/40' : 'border-border'
                  }`}
                >
                  <button
                    type="button"
                    onClick={() => setOpenFaq(open ? null : i)}
                    aria-expanded={open}
                    className="w-full flex items-center justify-between gap-4 px-5 sm:px-6 py-4 text-left cursor-pointer"
                  >
                    <span className="font-semibold text-heading text-sm sm:text-base">
                      {t(`landing.${key}Q`)}
                    </span>
                    <ChevronDown
                      className={`h-5 w-5 text-muted-fg shrink-0 transition-transform duration-300 ${
                        open ? 'rotate-180' : ''
                      }`}
                      aria-hidden
                    />
                  </button>
                  <div
                    className={`grid transition-all duration-500 ease-in-out ${
                      open ? 'grid-rows-[1fr] opacity-100' : 'grid-rows-[0fr] opacity-0'
                    }`}
                  >
                    <div className="overflow-hidden min-h-0">
                      <p className="px-5 sm:px-6 pb-5 text-sm text-muted-fg leading-relaxed">
                        {t(`landing.${key}A`)}
                      </p>
                    </div>
                  </div>
                </div>
              )
            })}
          </div>
        </div>
      </section>
    </div>
  )
}