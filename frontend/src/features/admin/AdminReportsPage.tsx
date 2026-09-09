import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function AdminReportsPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('admin.reports')} />
}