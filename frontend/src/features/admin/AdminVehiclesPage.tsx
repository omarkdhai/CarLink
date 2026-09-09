import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function AdminVehiclesPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('admin.vehicles')} />
}