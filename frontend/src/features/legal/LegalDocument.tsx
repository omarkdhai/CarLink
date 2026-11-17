import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ArrowRight, CalendarDays } from 'lucide-react'
import { PageIntro, PublicContainer } from '@/components/shared/PublicPage'
import { cn } from '@/lib/cn'

export type SectionDef = { paragraphs: number; bullets?: number }
type DocKey = 'Privacy' | 'Terms' | 'Comms'

const DOCS: { key: DocKey; to: string; label: string }[] = [
  { key: 'Privacy', to: '/privacy', label: 'footer.privacyLink' },
  { key: 'Terms', to: '/terms', label: 'footer.termsLink' },
  { key: 'Comms', to: '/communication', label: 'footer.commsLink' },
]

/**
 * Shared layout for the three legal pages: readable column (≈70ch), numbered
 * sections, a sticky table of contents on desktop and links to the other
 * policies. Copy lives in `landing.legal{Doc}Section{n}…` keys.
 */
export function LegalDocument({ doc, sections }: { doc: DocKey; sections: SectionDef[] }) {
  const { t } = useTranslation()
  const k = (suffix: string) => t(`landing.legal${doc}${suffix}`)
  const anchor = (n: number) => `section-${n}`
  const [active, setActive] = useState(1)

  // Scroll-spy: highlight the section currently being read.
  useEffect(() => {
    if (typeof IntersectionObserver === 'undefined') return
    const io = new IntersectionObserver(
      (entries) => {
        const visible = entries.filter((e) => e.isIntersecting).sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)
        if (visible[0]) setActive(Number(visible[0].target.id.replace('section-', '')))
      },
      { rootMargin: '-20% 0px -65% 0px' },
    )
    sections.forEach((_, i) => {
      const el = document.getElementById(anchor(i + 1))
      if (el) io.observe(el)
    })
    return () => io.disconnect()
  }, [sections])

  return (
    <PublicContainer>
      <PageIntro eyebrow={t('footer.legalTitle')} title={k('Title')} lead={k('Subtitle')}>
        <p className="mt-5 inline-flex items-center gap-2 rounded-full bg-muted/60 px-3 py-1.5 text-sm text-muted-fg">
          <CalendarDays className="h-4 w-4" aria-hidden />
          {k('Updated')}
        </p>
      </PageIntro>

      <div className="grid gap-10 lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-14">
        {/* Table of contents */}
        <nav aria-label={t('legal.contents')} className="lg:sticky lg:top-24 lg:self-start">
          <p className="mb-3 text-xs font-semibold uppercase tracking-[0.12em] text-muted-fg rtl:tracking-normal">{t('legal.contents')}</p>
          <ol className="space-y-1 border-s border-border">
            {sections.map((_, i) => (
              <li key={i}>
                <a
                  href={`#${anchor(i + 1)}`}
                  aria-current={active === i + 1 ? 'location' : undefined}
                  className={cn(
                    '-ms-px flex gap-3 border-s-2 py-1.5 ps-4 text-sm leading-snug transition-colors duration-300',
                    active === i + 1
                      ? 'border-primary font-semibold text-heading'
                      : 'border-transparent text-muted-fg hover:border-primary/40 hover:text-foreground',
                  )}
                >
                  <span className="tabular-nums text-muted-fg/70">{i + 1}.</span>
                  <span>{k(`Section${i + 1}Title`)}</span>
                </a>
              </li>
            ))}
          </ol>
        </nav>

        <article className="max-w-[70ch] space-y-10">
          {sections.map((section, index) => {
            const n = index + 1
            return (
              <section key={n} id={anchor(n)} className="scroll-mt-24">
                <h2 className="flex items-baseline gap-3 text-xl font-bold text-heading sm:text-2xl">
                  <span className="text-base font-semibold tabular-nums text-primary">{n}.</span>
                  {k(`Section${n}Title`)}
                </h2>
                <div className="mt-4 space-y-4 text-base leading-relaxed text-foreground/80">
                  {Array.from({ length: section.paragraphs }, (_, p) => (
                    <p key={p}>{k(`Section${n}Text${p === 0 ? '' : p + 1}`)}</p>
                  ))}
                  {section.bullets ? (
                    <ul className="list-disc space-y-2 ps-6 marker:text-primary">
                      {Array.from({ length: section.bullets }, (_, b) => (
                        <li key={b}>{k(`Section${n}Item${b + 1}`)}</li>
                      ))}
                    </ul>
                  ) : null}
                </div>
              </section>
            )
          })}

          {/* Other policies */}
          <aside className="border-t border-border pt-8">
            <p className="mb-4 text-sm font-semibold text-heading">{t('legal.otherPolicies')}</p>
            <div className="grid gap-3 sm:grid-cols-2">
              {DOCS.filter((d) => d.key !== doc).map((d) => (
                <Link
                  key={d.key}
                  to={d.to}
                  className="hover-lift group flex items-center justify-between gap-3 rounded-2xl border border-border bg-surface p-4 text-sm font-semibold text-heading hover:border-primary/40"
                >
                  {t(d.label)}
                  <ArrowRight className="nudge h-4 w-4 text-primary rtl:rotate-180" aria-hidden />
                </Link>
              ))}
            </div>
            <p className="mt-6 text-sm text-muted-fg">
              {t('legal.questions')}{' '}
              <Link to="/contact" className="font-semibold text-primary underline underline-offset-4">
                {t('navbar.contact')}
              </Link>
            </p>
          </aside>
        </article>
      </div>
    </PublicContainer>
  )
}
