import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  CarFront,
  ShieldCheck,
  QrCode,
  ArrowRight,
  DoorOpen,
  BatteryFull,
  Lock,
  LockKeyhole,
  AlertTriangle,
  Lightbulb,
  Zap,
  MessageCircleWarning,
  Smartphone,
  Check,
  Users,
  Bell,
  Clock,
  Signal,
  Wifi,
  MessageSquare,
  PhoneCall,
  ChevronDown,
} from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'
import heroImage from '@/assets/hero.png'

/** Icon-only trust pills shown above the hero headline. */
const HERO_TRAITS = [
  { icon: ShieldCheck, key: 'landing.trust2' },
  { icon: Lock, key: 'landing.trust1' },
  { icon: Clock, key: 'landing.trust3' },
] as const

const problems = [
  { icon: CarFront, title: 'landing.problem1Title', text: 'landing.problem1Text' },
  { icon: MessageCircleWarning, title: 'landing.problem2Title', text: 'landing.problem2Text' },
  { icon: Lightbulb, title: 'landing.problem3Title', text: 'landing.problem3Text' },
  { icon: AlertTriangle, title: 'landing.problem4Title', text: 'landing.problem4Text' },
  { icon: Zap, title: 'landing.problem6Title', text: 'landing.problem6Text' },
] as const

const faqs = ['faq1', 'faq2', 'faq3', 'faq4', 'faq5'] as const

/** "How it works" steps — each one drives a state of the phone mockup. */
const howSteps = [
  { title: 'landing.how1Title', text: 'landing.how1Text' },
  { title: 'landing.how2Title', text: 'landing.how2Text' },
  { title: 'landing.how3Title', text: 'landing.how3Text' },
] as const

/** Reasons shown inside the phone mockup's contact form. */
const phoneReasons = [
  { key: 'contact.reasons.BLOCKING', icon: CarFront },
  { key: 'contact.reasons.LIGHTS', icon: Lightbulb },
  { key: 'contact.reasons.WINDOWS_OPEN', icon: DoorOpen },
  { key: 'contact.reasons.HIT_CAR', icon: AlertTriangle },
] as const

/** The three advantages shown in the panel under "how it works". */
const advantages = [
  { icon: Lock, title: 'landing.adv1Title', text: 'landing.adv1Text' },
  { icon: Bell, title: 'landing.adv2Title', text: 'landing.adv2Text' },
  { icon: Smartphone, title: 'landing.adv3Title', text: 'landing.adv3Text' },
] as const

/** Delivery channels — one card each in the "we reach you" section. */
const reachChannels = [
  { icon: MessageSquare, name: 'landing.reachSmsName', signal: 'landing.reachSmsSignal', text: 'landing.reachSmsText' },
  { icon: PhoneCall, name: 'landing.reachCallName', signal: 'landing.reachCallSignal', text: 'landing.reachCallText' },
] as const

/**
 * Public landing page — the marketing face of CarLink.
 * Flat/minimal per design direction; no owner data exposed.
 */
export default function HomePage() {
  const { t } = useTranslation()
  // Situations accordion: which row is expanded (first one open by default).
  const [activeProblem, setActiveProblem] = useState<number | null>(0)
  // Phone mockup: which "how it works" state is on screen. It auto-advances
  // until the visitor picks a step themselves.
  const [howStep, setHowStep] = useState(0)
  const [howAuto, setHowAuto] = useState(true)
  useEffect(() => {
    if (!howAuto) return
    const id = window.setInterval(() => setHowStep((s) => (s + 1) % howSteps.length), 3200)
    return () => window.clearInterval(id)
  }, [howAuto])
  const pickHowStep = (i: number) => {
    setHowAuto(false)
    setHowStep(i)
  }

  return (
    <div>
      {/* Hero — mirrors the reference site's layout: icon trust pills, a
          two-line headline (question + answer), a highlighted subtitle, a pill
          CTA with a circular arrow, a demo link, a trust line and a visual. */}
      <section id="home" className="scroll-mt-20 mt-[-10px]">
        <div className="max-w-6xl mx-auto px-4 sm:px-6 pt-12 sm:pt-14 lg:pt-16 pb-16 sm:pb-20 lg:pb-24">
          <div className="grid items-center gap-14 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)] lg:gap-14 xl:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)]">
            {/* Left column — content */}
            <div className="mx-auto flex w-full max-w-[34rem] flex-col items-center gap-7 text-center lg:mx-0 lg:max-w-[38rem] lg:items-start lg:gap-6 lg:text-start">
              {/* Icon-only trust pills */}
              <div className="flex items-center justify-center gap-2.5 lg:justify-start" role="list">
                {HERO_TRAITS.map(({ icon: Icon, key }) => (
                  <span
                    key={key}
                    role="listitem"
                    title={t(key)}
                    aria-label={t(key)}
                    className="inline-flex h-[2.35rem] w-[2.35rem] items-center justify-center rounded-full border border-primary bg-white text-primary shadow-[0_4px_14px_rgba(37,99,235,0.08)]"
                  >
                    <Icon className="h-4 w-4" aria-hidden />
                  </span>
                ))}
              </div>

              {/* Headline — both contact directions, each a question + answer */}
              <h1 className="flex w-full flex-col gap-5 [text-wrap:balance] lg:gap-7">
                <span className="flex flex-col gap-1.5 lg:gap-2">
                  <span className="text-[clamp(2.25rem,8.5vw,2.75rem)] font-black leading-[1.06] tracking-[-0.028em] text-heading lg:text-[3.25rem] xl:text-[3.75rem]">
                    {t('landing.heroQ1')}
                  </span>
                  <span className="text-lg font-normal leading-[1.5] text-muted-fg lg:text-[1.375rem]">
                    {t('landing.heroA1')}
                  </span>
                </span>
                <span className="flex flex-col gap-1.5 lg:gap-2">
                  <span className="text-[clamp(2.25rem,8.5vw,2.75rem)] font-black leading-[1.06] tracking-[-0.028em] text-primary-active lg:text-[3.25rem] xl:text-[3.75rem]">
                    {t('landing.heroQ2')}
                  </span>
                  <span className="text-lg font-normal leading-[1.5] text-primary lg:text-[1.375rem]">
                    {t('landing.heroA2')}
                  </span>
                </span>
              </h1>

              {/* Divider + highlighted subtitle */}
              <div className="mt-0.5 flex w-full max-w-[32rem] flex-col items-center gap-4 lg:items-start">
                <span
                  className="h-px w-12 rounded-full bg-gradient-to-r from-primary-active to-accent shadow-[0_0_18px_rgba(37,99,235,0.15)]"
                  aria-hidden
                />
                <p className="m-0 text-[0.9375rem] leading-[1.65] text-muted-fg lg:text-[1.0625rem] lg:leading-[1.75]">
                  <mark className="box-decoration-clone rounded-sm bg-primary-soft px-1 text-inherit">
                    {t('landing.heroSubtitle')}
                  </mark>
                </p>
              </div>

              {/* CTAs — order pill + how-it-works pill on one line */}
              <div className="flex w-full flex-wrap items-center justify-center gap-3 sm:flex-nowrap lg:justify-start">
                <Link
                  to="/order?plan=single"
                  className="inline-flex min-h-[54px] items-center justify-center gap-2.5 rounded-full bg-primary py-2 pe-2 ps-[1.35rem] font-semibold text-white shadow-[0_8px_24px_rgba(37,99,235,0.28)] transition-all hover:-translate-y-0.5 hover:bg-primary-hover hover:shadow-[0_14px_36px_rgba(37,99,235,0.34)] active:translate-y-0 active:scale-[0.98] focus-visible:outline-none"
                >
                  <span className="whitespace-nowrap text-[clamp(1rem,2vw,1.125rem)]">
                    {t('landing.heroCta')}
                  </span>
                  <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-white/25">
                    <ArrowRight className="h-5 w-5 rtl:rotate-180" aria-hidden />
                  </span>
                </Link>

                <a
                  href="#how"
                  className="inline-flex min-h-[54px] items-center justify-center gap-2 rounded-full border border-primary/25 bg-white/90 px-6 font-semibold text-primary shadow-[0_4px_14px_rgba(37,99,235,0.10)] transition-all hover:-translate-y-0.5 hover:border-primary/45 hover:bg-primary-soft active:translate-y-0 active:scale-[0.98] focus-visible:outline-none"
                >
                  <span className="whitespace-nowrap text-[clamp(0.9375rem,1.6vw,1rem)]">
                    {t('landing.heroCtaSecondary')}
                  </span>
                  <ChevronDown className="h-4 w-4" aria-hidden />
                </a>
              </div>

              {/* Trust line — one line on mobile */}
              <div className="mt-0.5 flex w-full flex-nowrap items-center justify-between gap-x-2 border-t border-muted pt-3.5 text-[11px] text-muted-fg sm:gap-x-4 lg:justify-start lg:gap-x-5 lg:text-sm">
                <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
                  <Lock className="h-3.5 w-3.5 shrink-0 text-primary" aria-hidden />
                  {t('landing.trust1')}
                </span>
                <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
                  <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-primary" aria-hidden />
                  {t('landing.trust2')}
                </span>
                <span className="inline-flex items-center gap-1.5 whitespace-nowrap">
                  <Clock className="h-3.5 w-3.5 shrink-0 text-primary" aria-hidden />
                  {t('landing.trust3')}
                </span>
              </div>
            </div>

            {/* Right column — hero visual */}
            <div className="relative mx-auto w-full max-w-2xl">
              <div className="relative aspect-[4/4.35] overflow-hidden rounded-[2rem] border-2 border-white bg-gradient-to-b from-primary-soft to-white shadow-[0_30px_80px_rgba(15,23,42,0.20)] sm:aspect-[4/3.65] sm:rounded-se-[7rem] lg:aspect-[4/4.05]">
                <img
                  src={heroImage}
                  alt={t('landing.heroAlt')}
                  className="absolute inset-0 h-full w-full object-cover"
                  loading="eager"
                />
                <div
                  className="absolute inset-x-0 bottom-0 h-1/3 bg-gradient-to-t from-black/40 to-transparent"
                  aria-hidden
                />
              </div>

              {/* Floating card — scan / contact */}
              <div className="absolute -top-4 end-3 z-10 flex max-w-[13.5rem] items-start gap-3 rounded-2xl border border-white/90 bg-white/95 p-3.5 shadow-[0_14px_36px_rgba(15,23,42,0.16)] backdrop-blur sm:-top-5 sm:end-6 sm:max-w-[14.5rem] sm:p-4">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary ring-1 ring-primary/15">
                  <QrCode className="h-5 w-5" aria-hidden />
                </span>
                <div className="min-w-0 pt-0.5 text-start">
                  <p className="text-[0.8125rem] font-bold leading-snug text-heading">
                    {t('landing.heroCardScanTitle')}
                  </p>
                  <p className="mt-1 text-xs leading-relaxed text-muted-fg">
                    {t('landing.heroCardScanText')}
                  </p>
                </div>
              </div>

              {/* Floating card — alert */}
              <div className="absolute -bottom-5 start-3 z-10 flex max-w-[13.5rem] items-start gap-3 rounded-2xl border border-white/90 bg-white/95 p-3.5 shadow-[0_14px_36px_rgba(15,23,42,0.16)] backdrop-blur sm:-bottom-6 sm:start-7 sm:max-w-[14.5rem] sm:p-4">
                <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent ring-1 ring-accent/15">
                  <Bell className="h-5 w-5" aria-hidden />
                </span>
                <div className="min-w-0 pt-0.5 text-start">
                  <p className="text-[0.8125rem] font-bold leading-snug text-heading">
                    {t('landing.heroCardAlertTitle')}
                  </p>
                  <p className="mt-1 text-xs leading-relaxed text-muted-fg">
                    {t('landing.heroCardAlertText')}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Problems / situations — modelled on the reference site: a full-bleed
          band, a two-line intro (second line accented) and a numbered accordion
          whose active row reveals a phone-notification preview. */}
      <section className="border-y border-muted bg-gradient-to-b from-primary-soft to-background py-16 sm:py-20 lg:py-24">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          {/* Intro — title left, copy right on desktop */}
          <div className="grid gap-5 lg:grid-cols-[minmax(0,1.02fr)_minmax(0,0.98fr)] lg:items-end lg:gap-x-16">
            <h2 className="m-0 text-[clamp(2.2rem,6.5vw,3.35rem)] font-black leading-[1.08] tracking-[-0.034em] text-heading">
              <span>{t('landing.problemsTitleLead')}</span>{' '}
              <span className="block text-primary-active">{t('landing.problemsTitleAccent')}</span>
            </h2>
            <p className="m-0 max-w-[31rem] text-[0.98rem] font-normal leading-[1.72] text-muted-fg lg:justify-self-end lg:pb-1">
              {t('landing.problemsSubtitle')}
            </p>
          </div>

          {/* Numbered accordion */}
          <ul className="mt-11 flex flex-col border-t border-muted sm:mt-12 lg:mt-14">
            {problems.map(({ icon: Icon, title, text }, i) => {
              const active = activeProblem === i
              return (
                <li key={title}>
                  <button
                    type="button"
                    aria-pressed={active}
                    aria-current={active ? 'true' : undefined}
                    onClick={() => setActiveProblem(active ? null : i)}
                    className={`group grid w-full grid-cols-[2.35rem_minmax(0,1fr)] items-center gap-x-4 border-b border-muted px-3 py-5 text-start transition-colors duration-200 sm:px-10 lg:grid-cols-[2.75rem_minmax(0,1fr)_minmax(16.5rem,19rem)] lg:px-12 ${
                      active ? 'bg-white/60' : 'hover:bg-muted/40'
                    }`}
                  >
                    <span
                      className={`font-mono text-[0.7rem] font-medium leading-none tracking-[0.06em] transition-colors ${
                        active ? 'font-semibold text-primary-active' : 'text-muted-fg/50'
                      }`}
                    >
                      {String(i + 1).padStart(2, '0')}
                    </span>

                    <span
                      className={`min-w-0 text-[clamp(1.05rem,2.4vw,1.32rem)] leading-[1.34] tracking-[-0.022em] transition-colors ${
                        active
                          ? 'font-bold text-heading'
                          : 'font-medium text-heading/60 group-hover:text-heading'
                      }`}
                    >
                      {t(title)}
                    </span>

                    {/* Notification preview — inline on mobile, third column on desktop */}
                    <span
                      className={`min-w-0 items-start ${
                        active
                          ? 'col-start-2 mt-2.5 flex lg:col-start-3 lg:row-start-1 lg:mt-0 lg:justify-self-end'
                          : 'hidden'
                      }`}
                    >
                      <span className="flex w-full max-w-full items-start gap-[0.72rem] rounded-2xl border border-black/5 bg-white p-3 shadow-[0_1px_2px_rgba(0,0,0,0.04),0_6px_20px_rgba(15,10,8,0.08)] lg:w-[18.5rem]">
                        <span className="grid h-[2.35rem] w-[2.35rem] shrink-0 place-items-center rounded-[0.58rem] bg-gradient-to-b from-primary to-primary-active text-white shadow-sm">
                          <Icon className="h-[1.15rem] w-[1.15rem]" aria-hidden />
                        </span>
                        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                          <span className="flex w-full min-w-0 items-center gap-2">
                            <span className="flex min-w-0 items-center gap-1.5">
                              <span className="text-[0.62rem] font-semibold leading-none tracking-[0.01em] text-slate-500">
                                CarLink
                              </span>
                              <span className="text-[0.62rem] leading-none text-slate-400" aria-hidden>
                                •
                              </span>
                              <span className="truncate text-[0.62rem] font-medium leading-none tracking-[0.01em] text-slate-500">
                                {t('landing.problemsAlertSender')}
                              </span>
                            </span>
                            <span className="ms-auto shrink-0 text-[0.62rem] font-medium leading-none text-slate-400">
                              {t('landing.problemsAlertTime')}
                            </span>
                          </span>
                          <span className="text-[0.84rem] leading-[1.35] text-slate-700">
                            {t(text)}
                          </span>
                        </span>
                      </span>
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>

          {/* Bottom CTA */}
          <div className="mt-10 text-center">
            <p className="mb-4 text-sm text-muted-fg">{t('landing.problemsCtaText')}</p>
            <Link to="/order?plan=single" className={buttonClasses({ size: 'lg' })}>
              {t('landing.problemsCta')}
              <ArrowRight className="h-5 w-5" aria-hidden />
            </Link>
          </div>
        </div>
      </section>

      {/* How it works — modelled on the reference site's phone demo: copy and
          numbered step controls on the left, an interactive phone mockup of the
          contact flow on the right. */}
      <section id="how" className="scroll-mt-20 bg-muted/30 py-16 sm:py-20 lg:py-24">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          <div className="grid items-center gap-12 lg:grid-cols-[1fr_1.05fr] lg:gap-14">
            {/* Left — copy + step controls */}
            <div className="w-full max-w-[450px]">
              <h2 className="m-0 text-[clamp(29px,7vw,39px)] font-black leading-[1.2] tracking-[-0.045em] text-heading sm:text-[clamp(36px,4.4vw,62px)]">
                {t('landing.howTitle')}
              </h2>
              <p className="mt-[26px] max-w-[410px] text-[15px] leading-[1.95] text-muted-fg">
                {t('landing.howSubtitle')}
              </p>

              <div className="mt-8 flex flex-col gap-1 border-t border-border pt-4">
                {howSteps.map((step, i) => (
                  <button
                    key={step.title}
                    type="button"
                    onClick={() => pickHowStep(i)}
                    aria-current={howStep === i ? 'true' : undefined}
                    className={`flex min-h-[52px] items-center gap-4 rounded-lg px-2.5 py-2.5 text-start transition-colors duration-200 ${
                      howStep === i
                        ? 'bg-white/70 text-primary-active'
                        : 'text-muted-fg hover:bg-white/50 hover:text-heading'
                    }`}
                  >
                    <span
                      className={`grid h-7 w-7 shrink-0 place-items-center rounded-full border font-mono text-[11px] font-medium leading-none transition-colors ${
                        howStep === i
                          ? 'border-primary bg-primary text-white'
                          : 'border-border bg-white'
                      }`}
                    >
                      {String(i + 1).padStart(2, '0')}
                    </span>
                    <span className="min-w-0">
                      <span className="block text-sm font-bold leading-snug">{t(step.title)}</span>
                      <span className="mt-0.5 block text-[13px] leading-relaxed text-muted-fg">
                        {t(step.text)}
                      </span>
                    </span>
                  </button>
                ))}
              </div>
            </div>

            {/* Right — phone mockup */}
            <div className="relative mx-auto flex w-full justify-center">
              <div className="relative">
                <div
                  aria-hidden
                  className="pointer-events-none absolute left-1/2 top-1/2 -z-10 h-[92%] w-[92%] -translate-x-1/2 -translate-y-1/2 rounded-full"
                  style={{
                    background:
                      'radial-gradient(circle, rgba(37,99,235,0.16) 0%, rgba(37,99,235,0.06) 55%, transparent 72%)',
                  }}
                />
                <div className="relative w-[248px] rounded-[34px] bg-[#292724] p-[6px] shadow-[0_28px_70px_rgba(15,23,42,0.35)] sm:w-[268px]">
                  <div className="relative h-[524px] overflow-hidden rounded-[29px] bg-white sm:h-[566px]">
                    {/* Status bar */}
                    <div className="flex h-8 items-center justify-between px-5 pt-2 text-[9px] font-semibold text-heading">
                      <span dir="ltr">9:41</span>
                      <span className="inline-flex items-center gap-[3px]">
                        <Signal className="h-3 w-3" aria-hidden />
                        <Wifi className="h-3 w-3" aria-hidden />
                        <BatteryFull className="h-3.5 w-3.5" aria-hidden />
                      </span>
                    </div>

                    {/* Dynamic island */}
                    <span
                      className="absolute left-1/2 top-2 h-5 w-[78px] -translate-x-1/2 rounded-full bg-[#080909]"
                      aria-hidden
                    >
                      <span
                        className="absolute right-2 top-1.5 h-2 w-2 rounded-full"
                        style={{
                          background:
                            'radial-gradient(circle at 35% 30%, #526981 0%, #13202b 28%, #080c12 60%)',
                        }}
                      />
                    </span>

                    {/* Screen */}
                    <div className="absolute inset-x-0 bottom-5 top-9 overflow-hidden">
                      {/* Step 01 — the sticker on the windshield */}
                      {howStep === 0 && (
                        <div className="flex h-full flex-col gap-3 p-3">
                          <div className="flex items-center gap-2 border-b border-border px-1 pb-2.5 pt-1">
                            <span className="grid h-8 w-8 place-items-center rounded-lg bg-primary text-white">
                              <QrCode className="h-4 w-4" aria-hidden />
                            </span>
                            <span className="text-[1.125rem] font-bold tracking-[-0.02em] text-heading">
                              CarLink
                            </span>
                          </div>
                          <div className="flex flex-1 flex-col items-center justify-center rounded-[1.4rem] border border-border bg-primary-soft p-4 text-center">
                            <span className="grid h-28 w-28 place-items-center rounded-2xl border border-border bg-white shadow-sm">
                              <QrCode className="h-20 w-20 text-heading" aria-hidden />
                            </span>
                            <p className="mt-4 text-[0.9rem] font-bold leading-snug text-heading">
                              {t('landing.how1Title')}
                            </p>
                            <p className="mt-1.5 text-[0.65rem] leading-relaxed text-muted-fg">
                              {t('landing.how1Text')}
                            </p>
                          </div>
                          <div className="flex items-center justify-center gap-1.5 pb-1 text-[0.55rem] text-muted-fg">
                            <ShieldCheck className="h-3 w-3 text-primary" aria-hidden />
                            {t('landing.trust1')}
                          </div>
                        </div>
                      )}

                      {/* Step 02 — the passer-by scans and picks a reason */}
                      {howStep === 1 && (
                        <div className="flex h-full flex-col">
                          {/* Vehicle panel */}
                          <div className="flex-none border-b border-border p-2.5">
                            <div className="relative overflow-hidden rounded-xl bg-primary p-2.5 pt-[1rem] text-white shadow-[0_9px_22px_rgba(37,99,235,0.25)]">
                              <span
                                aria-hidden
                                className="pointer-events-none absolute -right-6 -top-7 h-[9rem] w-[9rem] rounded-full bg-white/10"
                              />
                              <span
                                aria-hidden
                                className="pointer-events-none absolute -bottom-10 -left-8 h-[8rem] w-[8rem] rounded-full bg-black/5"
                              />
                              <div className="relative">
                                <p className="m-0 text-[1.125rem] font-bold leading-[1.2] tracking-[-0.025em]">
                                  {t('contact.publicTitle')}
                                </p>
                                <p className="mt-1.5 max-w-[24rem] text-[0.625rem] leading-[1.5] text-white/85">
                                  {t('contact.publicSubtitle')}
                                </p>
                                <div className="mt-2.5 flex min-w-0 items-center gap-2 rounded-xl border border-white/70 bg-white p-2 shadow-[0_2px_6px_rgba(15,23,42,0.08)]">
                                  <span className="grid h-8 w-8 shrink-0 place-items-center rounded-[0.75rem] bg-primary text-white ring-1 ring-primary/20">
                                    <CarFront className="h-4 w-4" aria-hidden />
                                  </span>
                                  <span className="flex min-w-0 flex-col items-start">
                                    <span className="max-w-full truncate text-[0.875rem] font-bold leading-[1.3] tracking-[-0.02em] text-heading">
                                      {t('landing.howPhoneVehName')}
                                    </span>
                                    <span
                                      dir="ltr"
                                      className="mt-px block font-mono text-[0.5625rem] font-medium leading-none text-muted-fg"
                                    >
                                      {t('landing.howPhoneVehCode')}
                                    </span>
                                  </span>
                                </div>
                              </div>
                            </div>
                          </div>

                          {/* Form body */}
                          <div className="flex min-h-0 flex-1 flex-col gap-1.5 overflow-hidden p-2.5 pt-2">
                            <div className="grid gap-1.5">
                              <p className="m-0 text-[0.82rem] font-bold text-heading">
                                {t('contact.reason')}
                              </p>
                              <div className="grid gap-1">
                                {phoneReasons.map(({ key, icon: RIcon }, i) => {
                                  const chosen = i === 0
                                  return (
                                    <span
                                      key={key}
                                      className={`flex min-h-[2.05rem] items-center gap-[0.42rem] rounded-[0.85rem] border px-[0.42rem] py-[0.32rem] ${
                                        chosen ? 'border-primary bg-primary/10' : 'border-border bg-white'
                                      }`}
                                    >
                                      <span
                                        className={`grid h-[1.55rem] w-[1.55rem] shrink-0 place-items-center rounded-[0.5rem] ${
                                          chosen ? 'bg-primary text-white' : 'bg-muted text-primary'
                                        }`}
                                      >
                                        <RIcon className="h-[0.85rem] w-[0.85rem]" aria-hidden />
                                      </span>
                                      <span
                                        className={`min-w-0 flex-1 text-[0.6rem] leading-[1.3] ${
                                          chosen ? 'font-semibold text-primary' : 'font-medium text-heading'
                                        }`}
                                      >
                                        {t(key)}
                                      </span>
                                      <span
                                        className={`grid h-[0.9rem] w-[0.9rem] shrink-0 place-items-center rounded-full border ${
                                          chosen ? 'border-primary bg-primary text-white' : 'border-border bg-white'
                                        }`}
                                      >
                                        {chosen && <Check className="h-3 w-3" aria-hidden />}
                                      </span>
                                    </span>
                                  )
                                })}
                              </div>
                            </div>

                            <div className="grid gap-1">
                              <p className="m-0 text-[0.82rem] font-bold text-heading">
                                {t('contact.note')}
                              </p>
                              <div className="min-h-[2.15rem] rounded-[0.85rem] border border-border bg-white px-[0.55rem] py-[0.45rem]">
                                <p className="m-0 text-[0.6rem] leading-[1.35] text-muted-fg">
                                  {t('contact.notePlaceholder')}
                                </p>
                              </div>
                            </div>
                          </div>

                          {/* Send bar */}
                          <div className="mt-auto flex-none border-t border-border bg-white/95 px-3 pb-3 pt-2 backdrop-blur">
                            <span className="flex h-[2.15rem] w-full items-center justify-center rounded-[0.85rem] bg-primary text-[0.72rem] font-semibold text-white shadow-[0_8px_18px_rgba(37,99,235,0.3)]">
                              {t('contact.sendContact')}
                            </span>
                          </div>
                        </div>
                      )}

                      {/* Step 03 — the owner receives it */}
                      {howStep === 2 && (
                        <div className="flex h-full flex-col items-center justify-center bg-primary-soft px-6 text-center">
                          <span
                            className="grid h-14 w-14 place-items-center rounded-full text-white shadow-[0_10px_22px_rgba(5,150,105,0.35)]"
                            style={{ background: 'linear-gradient(160deg, #10b981 0%, #059669 100%)' }}
                          >
                            <Check className="h-7 w-7" aria-hidden />
                          </span>
                          <p className="mt-4 text-[0.68rem] font-bold uppercase tracking-[0.14em] text-success">
                            {t('landing.howPhoneDoneEyebrow')}
                          </p>
                          <p className="mt-1.5 max-w-[12.5rem] text-[1.12rem] font-bold leading-[1.3] text-heading">
                            {t('contact.contactSuccess')}
                          </p>
                          <p className="mt-1.5 max-w-[13rem] text-[0.78rem] leading-[1.5] text-muted-fg">
                            {t('landing.howPhoneDoneCopy')}
                          </p>
                        </div>
                      )}
                    </div>

                    {/* Home indicator */}
                    <span
                      className="absolute bottom-2 left-1/2 h-1 w-[92px] -translate-x-1/2 rounded-full bg-[#292929]"
                      aria-hidden
                    />
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Trust bar */}
          <div className="mt-14 rounded-2xl border border-border bg-white/70 p-6 sm:p-8">
            <div className="flex flex-col items-center justify-between gap-6 sm:flex-row">
              <div className="flex items-center gap-4">
                <div className="grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-primary text-white">
                  <Check className="h-6 w-6" aria-hidden />
                </div>
                <div className="text-center sm:text-start">
                  <p className="font-semibold text-heading">{t('landing.howTrustTitle')}</p>
                  <p className="text-sm text-muted-fg">{t('landing.howTrustText')}</p>
                </div>
              </div>
              <div className="flex items-center gap-6 text-sm text-muted-fg">
                <span className="inline-flex items-center gap-2">
                  <Users className="h-4 w-4 text-primary" aria-hidden />
                  {t('landing.howEasyText')}
                </span>
                <span className="inline-flex items-center gap-2">
                  <Clock className="h-4 w-4 text-primary" aria-hidden />
                  {t('landing.howTimeText')}
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* Advantages — a boxed panel: copy and a masked-number card on top,
          the three advantages as divided columns underneath. */}
      <section id="advantages" className="scroll-mt-20 py-16 sm:py-20 lg:py-24">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          <div className="rounded-[28px] border border-border bg-white p-6 sm:p-10 lg:p-[clamp(28px,4vw,52px)]">
            {/* Intro — copy left, masked number card right */}
            <div className="grid gap-8 lg:grid-cols-[minmax(0,1.5fr)_minmax(240px,1fr)] lg:items-center lg:gap-14">
              <div>
                <p className="m-0 mb-[18px] flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.07em] text-accent">
                  <ShieldCheck className="h-4 w-4" aria-hidden />
                  {t('landing.advBadge')}
                </p>
                <h2 className="m-0 max-w-[570px] text-[clamp(27px,6.8vw,38px)] font-black leading-[1.3] tracking-[-0.03em] text-heading [text-wrap:balance] sm:text-[clamp(30px,3.6vw,44px)]">
                  {t('landing.advTitle')}
                </h2>
                <p className="mt-4 max-w-[460px] text-[15px] leading-[1.9] text-muted-fg">
                  {t('landing.advSubtitle')}
                </p>
              </div>

              {/* Masked number card — the privacy advantage, made concrete */}
              <div className="mx-auto w-full max-w-[340px] -rotate-1 rounded-[20px] border border-border bg-white p-5 shadow-[0_12px_28px_rgba(15,23,42,0.05)] lg:mx-0 lg:-rotate-2 lg:p-[26px]">
                <span className="block text-[11px] text-muted-fg">{t('landing.advNumberLabel')}</span>
                <div className="flex items-center justify-between gap-3.5 border-b border-border py-5">
                  <span
                    dir="ltr"
                    className="whitespace-nowrap font-mono text-[clamp(18px,2vw,25px)] tracking-[0.03em] text-heading"
                  >
                    01 ••• ••• •••
                  </span>
                  <LockKeyhole className="h-5 w-5 shrink-0 text-primary" aria-hidden />
                </div>
                <span className="mt-4 flex items-center gap-[7px] text-[11px] leading-[1.6] text-muted-fg">
                  <ShieldCheck className="h-3.5 w-3.5 shrink-0 text-primary" aria-hidden />
                  {t('landing.advNumberNote')}
                </span>
              </div>
            </div>

            {/* Advantages grid — three columns divided by rules */}
            <ol className="mt-10 grid list-none border-t border-border ps-0 pt-2 lg:grid-cols-3 lg:pt-8">
              {advantages.map(({ icon: AIcon, title, text }) => (
                <li
                  key={title}
                  className="border-t border-border py-[22px] first:border-t-0 last:pb-0 lg:border-t-0 lg:border-s lg:border-border lg:py-0 lg:ps-[26px] lg:pe-[26px] lg:first:border-s-0 lg:first:ps-0 lg:last:pe-0"
                >
                  <div className="mb-3 flex items-center justify-between lg:mb-[18px]">
                    <span className="grid h-10 w-10 place-items-center rounded-xl bg-primary-soft text-primary lg:h-12 lg:w-12 lg:rounded-[14px]">
                      <AIcon className="h-[21px] w-[21px] lg:h-6 lg:w-6" aria-hidden />
                    </span>
                  </div>
                  <h3 className="mb-2.5 text-lg font-semibold leading-[1.5] text-heading">{t(title)}</h3>
                  <p className="m-0 text-[13px] leading-[1.9] text-muted-fg">{t(text)}</p>
                </li>
              ))}
            </ol>
          </div>
        </div>
      </section>

      {/* We reach you — a boxed card: icon, copy and condition pills on the
          left, one card per delivery channel on the right. */}
      <section className="py-16 sm:py-20 lg:py-24">
        <div className="mx-auto max-w-6xl px-4 sm:px-6">
          <div className="overflow-hidden rounded-[2rem] border border-border bg-white px-5 py-8 shadow-[0_16px_50px_rgba(15,23,42,0.06)] sm:px-8 sm:py-10 lg:px-12 lg:py-12">
            <div className="grid items-center gap-9 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:gap-14">
              {/* Left — icon, copy, condition pills */}
              <div>
                <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary-soft text-primary">
                  <Signal className="h-6 w-6" aria-hidden />
                </div>
                <h2 className="max-w-[14ch] text-3xl font-black leading-tight text-heading sm:text-4xl">
                  {t('landing.reachTitle')}
                </h2>
                <p className="mt-4 max-w-xl text-base leading-8 text-muted-fg sm:text-lg">
                  {t('landing.reachSubtitle')}
                </p>
                <div className="mt-6 flex flex-wrap gap-2">
                  <span className="inline-flex items-center gap-2 rounded-full bg-muted px-3.5 py-2 text-sm font-bold text-heading">
                    <Signal className="h-4 w-4 text-primary" aria-hidden />
                    {t('landing.reachPillNetwork')}
                  </span>
                  <span className="inline-flex items-center gap-2 rounded-full bg-muted px-3.5 py-2 text-sm font-bold text-heading">
                    <Wifi className="h-4 w-4 text-primary" aria-hidden />
                    {t('landing.reachPillInternet')}
                  </span>
                </div>
              </div>

              {/* Right — one card per channel */}
              <div className="grid gap-4 sm:grid-cols-2">
                {reachChannels.map(({ icon: CIcon, name, signal, text }) => (
                  <article
                    key={name}
                    className="rounded-[1.75rem] border border-border bg-muted p-5 transition-[transform,box-shadow,border-color] duration-[250ms] ease-[cubic-bezier(.22,1,.36,1)] hover:-translate-y-[0.35rem] hover:border-primary/40 hover:shadow-[0_20px_45px_rgba(15,23,42,0.10)] sm:p-6"
                  >
                    <span className="flex h-12 w-12 items-center justify-center rounded-2xl bg-white text-primary shadow-sm">
                      <CIcon className="h-6 w-6" aria-hidden />
                    </span>
                    <p className="mt-5 text-xs font-bold text-primary">{t(name)}</p>
                    <h3 className="mt-1 text-xl font-bold leading-snug text-heading">{t(signal)}</h3>
                    <p className="mt-2 text-sm leading-7 text-muted-fg">{t(text)}</p>
                  </article>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* FAQ — a small uppercase title over an exclusive native accordion of
          bordered cards, each with a round chevron pill that fills when open. */}
      <section className="py-16 sm:py-20 lg:py-24">
        <div className="mx-auto flex max-w-3xl flex-col gap-[0.875rem] px-4 sm:px-6">
          <h2 className="text-[0.8125rem] font-extrabold uppercase tracking-[0.08em] text-muted-fg">
            {t('landing.faqTitle')}
          </h2>

          <div className="grid gap-3 sm:gap-[0.875rem]">
            {faqs.map((key) => (
              <details
                key={key}
                name="landing-faq"
                className="group overflow-hidden rounded-[1.375rem] border border-border bg-gradient-to-b from-white to-background shadow-[0_8px_22px_rgba(15,23,42,0.05)] transition-[border-color,box-shadow] duration-150 has-[summary:focus-visible]:border-primary/30 has-[summary:focus-visible]:shadow-[0_14px_34px_rgba(37,99,235,0.11)] hover:border-primary/30 hover:shadow-[0_14px_34px_rgba(37,99,235,0.11)] open:border-primary/20"
              >
                <summary className="flex cursor-pointer list-none items-center justify-between gap-3.5 p-[0.9375rem_1rem] [&::-webkit-details-marker]:hidden sm:gap-4 sm:p-[1.0625rem_1.125rem]">
                  <span className="min-w-0 flex-1 text-[1.0625rem] font-bold leading-[1.35] text-heading">
                    {t(`landing.${key}Q`)}
                  </span>
                  <span className="inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary transition-colors duration-150 group-open:bg-primary group-open:text-white">
                    <ChevronDown
                      className="h-4 w-4 transition-transform duration-200 group-open:rotate-180"
                      aria-hidden
                    />
                  </span>
                </summary>
                <div className="px-4 pb-4 sm:px-[1.125rem] sm:pb-[1.125rem]">
                  <p className="text-[0.875rem] leading-[1.7] text-muted-fg">{t(`landing.${key}A`)}</p>
                </div>
              </details>
            ))}
          </div>
        </div>
      </section>
    </div>
  )
}