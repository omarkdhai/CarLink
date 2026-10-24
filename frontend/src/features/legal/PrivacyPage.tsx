import { useTranslation } from 'react-i18next'

type SectionDef = { paragraphs: number; bullets?: number }

const SECTIONS: SectionDef[] = [
  { paragraphs: 1 },
  { paragraphs: 1, bullets: 4 },
  { paragraphs: 1 },
  { paragraphs: 2 },
  { paragraphs: 2 },
  { paragraphs: 2 },
  { paragraphs: 1 },
  { paragraphs: 1, bullets: 4 },
  { paragraphs: 1 },
  { paragraphs: 1 },
  { paragraphs: 1 },
]

export default function PrivacyPage() {
  const { t } = useTranslation()

  return (
    <div className="pt-12 pb-16 sm:pt-16 sm:pb-20 lg:pb-24">
      <div className="mx-auto w-[min(1120px,calc(100%_-_1.5rem))]">
        <div className="overflow-hidden rounded-[1.5rem] border border-[#f1f5f9] bg-white shadow-[0_8px_24px_rgba(26,26,26,0.06)] md:rounded-[2rem]">
          <div className="flex flex-col gap-6 px-5 py-6 sm:px-6 sm:py-7 md:gap-8 md:px-8 md:py-10 lg:gap-10 lg:px-12 lg:py-14 xl:px-14 xl:py-16">
            <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between lg:gap-8">
              <div className="max-w-3xl space-y-3 lg:space-y-4">
                <p className="text-[0.72rem] font-semibold uppercase leading-4 tracking-[0.14em] text-primary/70 rtl:tracking-[0.04em]">
                  {t('footer.legalTitle')}
                </p>
                <h1 className="text-3xl font-bold tracking-tight text-heading sm:text-4xl md:text-6xl">
                  {t('landing.legalPrivacyTitle')}
                </h1>
                <p className="max-w-2xl text-sm leading-7 text-muted-fg sm:text-base md:text-lg">
                  {t('landing.legalPrivacySubtitle')}
                </p>
              </div>
            </div>

            <article className="mx-auto w-full max-w-3xl space-y-8 text-start">
              <p className="text-sm text-muted-fg">{t('landing.legalPrivacyUpdated')}</p>

              {SECTIONS.map((section, index) => {
                const n = index + 1
                return (
                  <section key={n} className="space-y-3">
                    <h2 className="text-lg font-semibold tracking-tight text-heading sm:text-xl">
                      {t(`landing.legalPrivacySection${n}Title`)}
                    </h2>
                    {Array.from({ length: section.paragraphs }, (_, p) => (
                      <p key={p} className="text-sm leading-7 text-muted-fg sm:text-base">
                        {t(`landing.legalPrivacySection${n}Text${p === 0 ? '' : p + 1}`)}
                      </p>
                    ))}
                    {section.bullets ? (
                      <ul className="list-disc space-y-2 ps-5 text-sm leading-7 text-muted-fg sm:text-base">
                        {Array.from({ length: section.bullets }, (_, b) => (
                          <li key={b}>{t(`landing.legalPrivacySection${n}Item${b + 1}`)}</li>
                        ))}
                      </ul>
                    ) : null}
                  </section>
                )
              })}
            </article>
          </div>
        </div>
      </div>
    </div>
  )
}
