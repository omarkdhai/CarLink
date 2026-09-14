import { useTranslation } from 'react-i18next'

type LegalType = 'privacy' | 'terms' | 'communication'

const LEGAL_META: Record<LegalType, { badgeKey: string; titleKey: string; updatedKey: string; sectionCount: number }> = {
  privacy:      { badgeKey: 'legalPrivacyBadge',      titleKey: 'legalPrivacyTitle',      updatedKey: 'legalPrivacyUpdated',      sectionCount: 5 },
  terms:        { badgeKey: 'legalTermsBadge',        titleKey: 'legalTermsTitle',        updatedKey: 'legalTermsUpdated',        sectionCount: 5 },
  communication:{ badgeKey: 'legalCommsBadge',        titleKey: 'legalCommsTitle',        updatedKey: 'legalCommsUpdated',        sectionCount: 5 },
}

const SECTION_PREFIX: Record<LegalType, string> = {
  privacy:       'legalPrivacy',
  terms:         'legalTerms',
  communication: 'legalComms',
}

export default function LegalPage({ type }: { type: LegalType }) {
  const { t } = useTranslation()
  const meta = LEGAL_META[type]
  const prefix = SECTION_PREFIX[type]

  const sections = Array.from({ length: meta.sectionCount }, (_, i) => ({
    title: t(`landing.${prefix}Section${i + 1}Title`),
    text:  t(`landing.${prefix}Section${i + 1}Text`),
  }))

  return (
    <>
      <section className="pt-16 pb-8 sm:pt-20">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 text-center">
          <span className="inline-flex items-center gap-2 rounded-full bg-primary-soft text-primary text-sm font-semibold px-3 py-1 mb-4">
            {t(meta.badgeKey)}
          </span>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-heading leading-tight tracking-tight">
            {t(meta.titleKey)}
          </h1>
          <p className="text-muted-fg mt-4 text-sm">
            {t(meta.updatedKey)}
          </p>
        </div>
      </section>

      <section className="py-8 sm:py-12">
        <div className="max-w-3xl mx-auto px-4 sm:px-6 space-y-8">
          {sections.map((sec, i) => (
            <div key={i}>
              <h2 className="font-bold text-heading text-lg mb-2">{sec.title}</h2>
              <p className="text-sm text-muted-fg leading-relaxed">{sec.text}</p>
            </div>
          ))}
        </div>
      </section>
    </>
  )
}
