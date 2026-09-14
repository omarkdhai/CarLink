import { useTranslation } from 'react-i18next'
import { Construction } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'

/** Temporary page shown for features built in later phases. */
export function PlaceholderPage({ title, subtitle }: { title: string; subtitle?: string }) {
  const { t } = useTranslation()
  return (
    <div>
      <PageHeader title={title} subtitle={subtitle} />
      <div className="flex flex-col items-center justify-center gap-3 py-16 text-center bg-surface border border-border rounded-lg">
        <div className="h-12 w-12 rounded-full bg-muted flex items-center justify-center">
          <Construction className="h-6 w-6 text-muted-fg" aria-hidden />
        </div>
        <p className="text-sm text-muted-fg">{t('common.loading')}</p>
      </div>
    </div>
  )
}

export default PlaceholderPage