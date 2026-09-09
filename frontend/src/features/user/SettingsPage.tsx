import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function SettingsPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('settings.title')} />
}