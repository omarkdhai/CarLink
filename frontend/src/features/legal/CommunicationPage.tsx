import { LegalDocument, type SectionDef } from '@/features/legal/LegalDocument'

const SECTIONS: SectionDef[] = [
  { paragraphs: 1 },
  { paragraphs: 1, bullets: 4 },
  { paragraphs: 1 },
  { paragraphs: 1 },
  { paragraphs: 1 },
  { paragraphs: 1 },
  { paragraphs: 1, bullets: 5 },
  { paragraphs: 1 },
  { paragraphs: 1 },
  { paragraphs: 1 },
]

export default function CommunicationPage() {
  return <LegalDocument doc="Comms" sections={SECTIONS} />
}
