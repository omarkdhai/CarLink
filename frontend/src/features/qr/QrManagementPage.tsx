import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function QrManagementPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('qr.title')} subtitle={t('qr.subtitle')} />
}