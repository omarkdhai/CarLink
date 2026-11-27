import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Repeat2, Ruler } from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'
import { useMagnetic } from '@/hooks/useMagnetic'
import { canAnimate, gsap, MQ, useGSAP } from '@/lib/gsap'
import { cn } from '@/lib/cn'
import stickerDark from '@/assets/carlink-sticker-dark.png'
import stickerLight from '@/assets/carlink-sticker-light.png'

type Style = 'dark' | 'light'
const STYLES: Style[] = ['dark', 'light']
const IMAGES: Record<Style, string> = { dark: stickerDark, light: stickerLight }
const AUTO_MS = 4200

/** Shared section-title scale (same as the rest of the landing page). */
const H2 = 'text-3xl font-black leading-[1.12] tracking-[-0.03em] [text-wrap:balance] sm:text-4xl lg:text-[2.75rem]'
const EASE = 'ease-[cubic-bezier(.22,1,.36,1)]'

/**
 * "Pick your style" showcase: a Dark / Light switch drives a fanned pair of
 * sticker cards (the chosen one swings to the front) and re-themes the whole
 * panel so the chosen sticker always sits on a contrasting background. It
 * alternates on its own until the visitor interacts. Reduced-motion users get
 * instant switches and no auto-play.
 */
export function StickerStyles() {
  const { t } = useTranslation()
  const [style, setStyle] = useState<Style>('dark')
  const [auto, setAuto] = useState(true)
  const [paused, setPaused] = useState(false)
  const scope = useRef<HTMLElement>(null)
  const stage = useRef<HTMLDivElement>(null)
  const ctaRef = useRef<HTMLAnchorElement>(null)
  const firstPose = useRef(true)
  useMagnetic(ctaRef, 0.2)

  const navy = style === 'light' // panel turns navy to frame the light sticker

  // Auto-alternate until the visitor picks one (paused while hovering the stage).
  useEffect(() => {
    if (!auto || paused || !canAnimate() || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
    const id = window.setTimeout(() => setStyle((s) => (s === 'dark' ? 'light' : 'dark')), AUTO_MS)
    return () => window.clearTimeout(id)
  }, [auto, paused, style])

  const pick = (s: Style) => {
    setAuto(false)
    setStyle(s)
  }

  // Entrance: the stage rises in, the copy staggers, the size tag pops.
  useGSAP(
    () => {
      if (!canAnimate()) return
      gsap.matchMedia().add(MQ.MOTION, () => {
        const tl = gsap.timeline({ scrollTrigger: { trigger: scope.current, start: 'top 72%', once: true } })
        tl.from('[data-styles="copy"] > *', { y: 22, opacity: 0, duration: 0.7, stagger: 0.08, ease: 'power3.out' })
          .from('[data-styles="stage"]', { y: 60, opacity: 0, scale: 0.94, duration: 1.1, ease: 'expo.out' }, 0.1)
          .from('[data-styles="tag"]', { scale: 0, opacity: 0, duration: 0.6, ease: 'back.out(2)' }, 0.7)
        gsap.to('[data-styles="tag"]', { y: -8, duration: 2.6, ease: 'sine.inOut', yoyo: true, repeat: -1, delay: 1.4 })
        gsap.to('[data-styles="glow"]', { scale: 1.15, duration: 5, ease: 'sine.inOut', yoyo: true, repeat: -1 })
      })
    },
    { scope },
  )

  // Fan pose: the chosen card in front, the other tucked behind and tilted.
  useGSAP(
    () => {
      const rtl = document.documentElement.dir === 'rtl'
      const instant = firstPose.current || !canAnimate() || window.matchMedia('(prefers-reduced-motion: reduce)').matches
      firstPose.current = false
      for (const s of STYLES) {
        const el = stage.current?.querySelector<HTMLElement>(`[data-card="${s}"]`)
        if (!el) continue
        const front = s === style
        const pose = front
          ? { xPercent: 0, yPercent: 0, rotate: -3, scale: 1, opacity: 1 }
          : { xPercent: rtl ? -42 : 42, yPercent: 5, rotate: rtl ? -9 : 9, scale: 0.84, opacity: 0.92 }
        if (instant) {
          gsap.set(el, { ...pose, zIndex: front ? 2 : 1 })
          continue
        }
        // Swing out, swap depth at the widest point, settle into place.
        gsap.timeline()
          .to(el, { xPercent: front ? (rtl ? 30 : -30) : (rtl ? -70 : 70), rotate: front ? -10 : 14, duration: 0.38, ease: 'power2.out' })
          .set(el, { zIndex: front ? 2 : 1 })
          .to(el, { ...pose, duration: 0.7, ease: 'power3.out' })
      }
    },
    { dependencies: [style], scope: stage },
  )

  // Pointer tilt on the whole stage (fine pointers only).
  useGSAP(
    () => {
      const el = stage.current
      if (!el || !canAnimate()) return
      const mm = gsap.matchMedia()
      mm.add(MQ.FINE_POINTER_MOTION, () => {
        const fan = el.querySelector<HTMLElement>('[data-styles="fan"]')
        if (!fan) return
        gsap.set(fan, { transformPerspective: 1000 })
        const rx = gsap.quickTo(fan, 'rotationX', { duration: 0.6, ease: 'power3.out' })
        const ry = gsap.quickTo(fan, 'rotationY', { duration: 0.6, ease: 'power3.out' })
        const move = (e: PointerEvent) => {
          const r = el.getBoundingClientRect()
          ry(((e.clientX - r.left) / r.width - 0.5) * 14)
          rx(-((e.clientY - r.top) / r.height - 0.5) * 10)
        }
        const leave = () => {
          rx(0)
          ry(0)
        }
        el.addEventListener('pointermove', move)
        el.addEventListener('pointerleave', leave)
        return () => {
          el.removeEventListener('pointermove', move)
          el.removeEventListener('pointerleave', leave)
        }
      })
    },
    { scope: stage },
  )

  const other: Style = style === 'dark' ? 'light' : 'dark'

  return (
    <section id="stickers" ref={scope} className="scroll-mt-20 py-14 sm:py-16 lg:py-20">
      <div className="mx-auto max-w-6xl px-4 sm:px-6">
        <div
          className={cn(
            'relative overflow-hidden rounded-[2rem] border transition-[background-color,border-color] duration-700',
            EASE,
            navy ? 'border-heading bg-heading' : 'border-border bg-primary-soft',
          )}
        >
          {/* Ambient glow that follows the theme */}
          <span
            data-styles="glow"
            className={cn(
              'pointer-events-none absolute -end-24 -top-24 h-96 w-96 rounded-full blur-3xl transition-colors duration-700',
              navy ? 'bg-primary/35' : 'bg-primary/15',
            )}
            aria-hidden
          />
          <span
            className={cn(
              'pointer-events-none absolute -bottom-32 -start-24 h-80 w-80 rounded-full blur-3xl transition-colors duration-700',
              navy ? 'bg-accent/20' : 'bg-accent/10',
            )}
            aria-hidden
          />

          <div className="relative grid items-center gap-10 px-5 py-10 sm:px-10 sm:py-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-6 lg:px-14 lg:py-14">
            {/* Copy + switch */}
            <div data-styles="copy" className="flex flex-col items-center text-center lg:items-start lg:text-start">
              <h2 className={cn('m-0 max-w-[15ch] transition-colors duration-700', H2, navy ? 'text-white' : 'text-heading')}>
                {t('landing.stylesTitle')}
              </h2>
              <p
                className={cn(
                  'mt-4 max-w-[30rem] text-base leading-relaxed transition-colors duration-700 sm:text-lg',
                  navy ? 'text-white/70' : 'text-muted-fg',
                )}
              >
                {t('landing.stylesSubtitle')}
              </p>

              {/* Dark / Light switch */}
              <div
                role="group"
                aria-label={t('landing.stylesPick')}
                className={cn(
                  'relative mt-7 grid grid-cols-2 rounded-full p-1.5 transition-colors duration-700',
                  navy ? 'bg-white/10' : 'bg-white shadow-[0_8px_24px_rgba(15,23,42,0.08)]',
                )}
              >
                <span
                  data-on={style}
                  className={cn(
                    'pointer-events-none absolute inset-y-1.5 start-1.5 w-[calc(50%-0.375rem)] rounded-full transition-[transform,background-color] duration-500',
                    EASE,
                    'data-[on=light]:translate-x-full rtl:data-[on=light]:-translate-x-full',
                    navy ? 'bg-white' : 'bg-heading',
                  )}
                  aria-hidden
                />
                {STYLES.map((s) => {
                  const on = s === style
                  return (
                    <button
                      key={s}
                      type="button"
                      aria-pressed={on}
                      onClick={() => pick(s)}
                      className={cn(
                        'relative z-10 flex min-w-[8.5rem] items-center justify-center gap-2 rounded-full px-5 py-2.5 text-sm font-bold transition-colors duration-500',
                        on ? (navy ? 'text-heading' : 'text-white') : navy ? 'text-white/70 hover:text-white' : 'text-muted-fg hover:text-heading',
                      )}
                    >
                      <span
                        className={cn(
                          'h-3.5 w-3.5 rounded-full ring-2 transition-transform duration-300',
                          s === 'dark' ? 'bg-heading ring-white/80' : 'bg-white ring-heading/20',
                          on && 'scale-110',
                        )}
                        aria-hidden
                      />
                      {t(`landing.style${s === 'dark' ? 'Dark' : 'Light'}Name`)}
                    </button>
                  )
                })}
              </div>

              <Link
                ref={ctaRef}
                to={`/order?style=${style}`}
                className={cn(buttonClasses({ variant: 'primary', size: 'lg' }), 'group/cta mt-8 rounded-full ps-6 pe-2')}
              >
                {t(`landing.stylesCta${style === 'dark' ? 'Dark' : 'Light'}`)}
                <span className="grid h-9 w-9 place-items-center rounded-full bg-white/20 transition-transform duration-300 group-hover/cta:translate-x-1 rtl:group-hover/cta:-translate-x-1">
                  <ArrowRight className="h-4 w-4 rtl:rotate-180" aria-hidden />
                </span>
              </Link>
            </div>

            {/* Stage — the fanned pair */}
            <div
              ref={stage}
              data-styles="stage"
              className="group/stage relative flex h-[25rem] items-center justify-center sm:h-[30rem]"
              onPointerEnter={() => setPaused(true)}
              onPointerLeave={() => setPaused(false)}
            >
              <div data-styles="fan" className="grid place-items-center">
                {STYLES.map((s) => {
                  const front = s === style
                  return (
                    <button
                      key={s}
                      type="button"
                      data-card={s}
                      tabIndex={front ? -1 : 0}
                      aria-label={front ? t(`landing.style${s === 'dark' ? 'Dark' : 'Light'}Alt`) : t('landing.stylesShow', { name: t(`landing.style${s === 'dark' ? 'Dark' : 'Light'}Name`) })}
                      onClick={() => !front && pick(s)}
                      className={cn('col-start-1 row-start-1 block rounded-[1.4rem]', front ? 'cursor-default' : 'cursor-pointer')}
                    >
                      <span
                        className={cn(
                          'block transition-transform duration-500',
                          EASE,
                          front
                            ? 'group-hover/stage:scale-[1.035]'
                            : 'group-hover/stage:translate-x-8 group-hover/stage:rotate-3 rtl:group-hover/stage:-translate-x-8 rtl:group-hover/stage:-rotate-3',
                        )}
                      >
                        <img
                          src={IMAGES[s]}
                          alt=""
                          width={1417}
                          height={2598}
                          loading="lazy"
                          draggable={false}
                          style={{ borderRadius: '7% / 3.8%' }}
                          className={cn(
                            'h-[clamp(17rem,36vw,24rem)] w-auto select-none transition-shadow duration-500',
                            front
                              ? 'shadow-[0_30px_60px_rgba(15,23,42,0.35)] group-hover/stage:shadow-[0_40px_80px_rgba(15,23,42,0.45)]'
                              : 'shadow-[0_18px_36px_rgba(15,23,42,0.25)]',
                          )}
                        />
                      </span>
                    </button>
                  )
                })}
              </div>

              {/* Floating size tag */}
              <span
                data-styles="tag"
                className="absolute start-0 top-6 z-10 inline-flex items-center gap-2 rounded-2xl bg-white px-3.5 py-2.5 text-sm font-bold text-heading shadow-[0_14px_30px_rgba(15,23,42,0.16)] sm:start-4 lg:top-10"
              >
                <span className="grid h-7 w-7 place-items-center rounded-lg bg-primary-soft text-primary">
                  <Ruler className="h-4 w-4" aria-hidden />
                </span>
                <span dir="ltr">6 × 12 cm</span>
              </span>

              {/* Switch to the other one */}
              <button
                type="button"
                onClick={() => pick(other)}
                className={cn(
                  'group/hint absolute bottom-2 end-0 inline-flex items-center gap-1.5 rounded-full px-3.5 py-2 text-xs font-semibold transition-[background-color,color,transform] duration-300 hover:-translate-y-0.5 sm:end-4',
                  navy ? 'bg-white/10 text-white/80 hover:bg-white/20 hover:text-white' : 'bg-white/80 text-muted-fg hover:bg-white hover:text-heading',
                )}
              >
                <Repeat2 className="h-3.5 w-3.5 transition-transform duration-500 group-hover/hint:rotate-180" aria-hidden />
                {t('landing.stylesShow', { name: t(`landing.style${other === 'dark' ? 'Dark' : 'Light'}Name`) })}
              </button>
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
