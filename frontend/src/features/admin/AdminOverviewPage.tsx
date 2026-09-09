import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function AdminOverviewPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('admin.title')} subtitle={t('admin.subtitle')} />
}