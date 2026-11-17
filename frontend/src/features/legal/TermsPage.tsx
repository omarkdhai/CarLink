import { LegalDocument, type SectionDef } from '@/features/legal/LegalDocument'

const SECTIONS: SectionDef[] = [
  { paragraphs: 2 },
  { paragraphs: 2 },
  { paragraphs: 1 },
  { paragraphs: 2, bullets: 4 },
  { paragraphs: 1 },
  { paragraphs: 1 },
  { paragraphs: 1 },
  { paragraphs: 2 },
  { paragraphs: 1 },
  { paragraphs: 1 },
]

export default function TermsPage() {
  return <LegalDocument doc="Terms" sections={SECTIONS} />
}
