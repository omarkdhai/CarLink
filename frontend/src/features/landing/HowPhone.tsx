import { useRef } from 'react'
import { useTranslation } from 'react-i18next'
import { BatteryFull, MessageSquare, Signal, Wifi } from 'lucide-react'
import { canAnimate, gsap, MQ, useGSAP } from '@/lib/gsap'
import step1En from '@/assets/how/step1-en.webp'
import step1Fr from '@/assets/how/step1-fr.webp'
import step1Ar from '@/assets/how/step1-ar.webp'
import step2En from '@/assets/how/step2-en.webp'
import step2Fr from '@/assets/how/step2-fr.webp'
import step2Ar from '@/assets/how/step2-ar.webp'
import step3En from '@/assets/how/step3-en.webp'
import step3Fr from '@/assets/how/step3-fr.webp'
import step3Ar from '@/assets/how/step3-ar.webp'

/** Real app screenshots (iPhone 390 × 844 @2x) per language and step. */
const SCREENS: Record<'en' | 'fr' | 'ar', [string, string, string]> = {
  en: [step1En, step2En, step3En],
  fr: [step1Fr, step2Fr, step3Fr],
  ar: [step1Ar, step2Ar, step3Ar],
}

/** How long each step stays on screen while the demo plays by itself. */
export const HOW_STEP_MS = 4800

/**
 * The "How CarLink works" phone. Each step shows a real screenshot of the app:
 * the owner's sticker linked to their car, the passer-by's alert form, and the
 * alert waiting in the owner's space. Screens slide and fade between steps;
 * the alert form scrolls through itself like a visitor filling it in, and the
 * owner screen opens with an SMS notification dropping in. Reduced-motion
 * users get instant, static screens.
 */
export function HowPhone({ step }: { step: number }) {
  const { t, i18n } = useTranslation()
  const lang = i18n.language?.startsWith('ar') ? 'ar' : i18n.language?.startsWith('en') ? 'en' : 'fr'
  const screens = SCREENS[lang]
  const scope = useRef<HTMLDivElement>(null)
  const prev = useRef<number | null>(null)

  useGSAP(
    () => {
      const root = scope.current
      if (!root) return
      const layers = gsap.utils.toArray<HTMLElement>('[data-screen]', root)
      const from = prev.current
      prev.current = step
      const motion = canAnimate() && window.matchMedia(MQ.MOTION).matches

      // Static fallback (and first paint): only the current screen, at rest.
      layers.forEach((el, i) => gsap.set(el, { autoAlpha: i === step ? 1 : 0, xPercent: 0, scale: 1, zIndex: i === step ? 2 : 1 }))
      gsap.set('[data-shot]', { y: 0, scale: 1 })
      gsap.set('[data-notif]', { yPercent: -140, autoAlpha: 0 })
      if (!motion) return

      const rtl = document.documentElement.dir === 'rtl'
      const dir = (from === null || step > from || (from === 2 && step === 0) ? 1 : -1) * (rtl ? -1 : 1)
      const incoming = layers[step]
      const outgoing = from === null || from === step ? null : layers[from]
      const tl = gsap.timeline()

      if (outgoing) {
        gsap.set(outgoing, { autoAlpha: 1, zIndex: 1 })
        tl.to(outgoing, { xPercent: -22 * dir, scale: 0.94, autoAlpha: 0, duration: 0.55, ease: 'power3.inOut' }, 0)
      }
      tl.fromTo(
        incoming,
        { xPercent: outgoing ? 28 * dir : 0, scale: outgoing ? 1 : 0.97, autoAlpha: outgoing ? 1 : 0 },
        { xPercent: 0, scale: 1, autoAlpha: 1, duration: 0.65, ease: 'power3.out' },
        outgoing ? 0.08 : 0,
      )

      const shot = incoming.querySelector<HTMLElement>('[data-shot]')
      if (!shot) return
      if (step === 1) {
        // The alert form: scroll down to the note and the Send button, like a visitor.
        const distance = Math.max(0, shot.offsetHeight - incoming.clientHeight)
        if (distance > 0) {
          tl.to(shot, { y: -distance * 0.5, duration: 1.4, ease: 'power2.inOut' }, 0.9)
            .to(shot, { y: -distance, duration: 1.3, ease: 'power2.inOut' }, '+=0.35')
        }
      } else if (step === 2) {
        // The owner side: an SMS notification drops in, then the alert itself.
        const notif = root.querySelector('[data-notif]')
        tl.fromTo(notif, { yPercent: -140, autoAlpha: 0 }, { yPercent: 0, autoAlpha: 1, duration: 0.6, ease: 'back.out(1.6)' }, 0.45)
          .to(notif, { yPercent: -140, autoAlpha: 0, duration: 0.45, ease: 'power2.in' }, '+=1.5')
          .fromTo(shot, { scale: 1 }, { scale: 1.035, duration: 2.6, ease: 'sine.inOut', transformOrigin: '50% 30%' }, 0.6)
      } else {
        // The sticker card: a gentle push-in.
        tl.fromTo(shot, { scale: 1 }, { scale: 1.04, duration: 3.4, ease: 'sine.inOut', transformOrigin: '50% 45%' }, 0.5)
      }
    },
    { dependencies: [step, lang], scope, revertOnUpdate: false },
  )

  return (
    <div ref={scope} className="relative">
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 top-1/2 -z-10 h-[92%] w-[92%] -translate-x-1/2 -translate-y-1/2 rounded-full"
        style={{ background: 'radial-gradient(circle, rgba(37,99,235,0.16) 0%, rgba(37,99,235,0.06) 55%, transparent 72%)' }}
      />
      <div className="relative w-[248px] rounded-[34px] bg-[#292724] p-[6px] shadow-[0_28px_70px_rgba(15,23,42,0.35)] transition-transform duration-500 ease-[cubic-bezier(.22,1,.36,1)] hover:-translate-y-1.5 sm:w-[268px]">
        <div className="relative h-[524px] overflow-hidden rounded-[29px] bg-[#f8fafc] sm:h-[566px]">
          {/* Status bar */}
          <div className="relative z-20 flex h-8 items-center justify-between bg-[#f8fafc] px-5 pt-2 text-[9px] font-semibold text-heading">
            <span dir="ltr">9:41</span>
            <span className="inline-flex items-center gap-[3px]">
              <Signal className="h-3 w-3" aria-hidden />
              <Wifi className="h-3 w-3" aria-hidden />
              <BatteryFull className="h-3.5 w-3.5" aria-hidden />
            </span>
          </div>

          {/* Dynamic island */}
          <span className="absolute left-1/2 top-2 z-30 h-5 w-[78px] -translate-x-1/2 rounded-full bg-[#080909]" aria-hidden>
            <span
              className="absolute right-2 top-1.5 h-2 w-2 rounded-full"
              style={{ background: 'radial-gradient(circle at 35% 30%, #526981 0%, #13202b 28%, #080c12 60%)' }}
            />
          </span>

          {/* Screens */}
          <div className="absolute inset-x-0 bottom-0 top-8 overflow-hidden">
            {screens.map((src, i) => (
              <div key={i} data-screen className="absolute inset-0 overflow-hidden bg-[#f8fafc]">
                <img
                  data-shot
                  src={src}
                  alt={t(`landing.how${i + 1}Title`)}
                  width={390}
                  loading={i === 0 ? 'eager' : 'lazy'}
                  draggable={false}
                  className="block w-full select-none will-change-transform"
                />
              </div>
            ))}

            {/* Incoming SMS notification (owner step) */}
            <div
              data-notif
              className="absolute inset-x-2 top-1.5 z-10 flex items-start gap-2.5 rounded-2xl bg-white/90 p-2.5 shadow-[0_10px_30px_rgba(15,23,42,0.22)] backdrop-blur"
              aria-hidden
            >
              <span className="grid h-7 w-7 shrink-0 place-items-center rounded-lg bg-success text-white">
                <MessageSquare className="h-4 w-4" />
              </span>
              <span className="min-w-0 text-start">
                <span className="flex items-center justify-between gap-2 text-[9px] font-semibold text-muted-fg">
                  <span>CarLink · SMS</span>
                  <span>{t('landing.howPhoneNow')}</span>
                </span>
                <span className="mt-0.5 block truncate text-[11px] font-bold text-heading">
                  {t('contact.reasons.BLOCKING')}
                </span>
              </span>
            </div>
          </div>

          {/* Home indicator */}
          <span className="absolute bottom-2 left-1/2 z-20 h-1 w-[92px] -translate-x-1/2 rounded-full bg-[#292929]" aria-hidden />
        </div>
      </div>
    </div>
  )
}
