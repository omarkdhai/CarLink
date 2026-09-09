import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function NotificationsPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('notifications.title')} subtitle={t('notifications.subtitle')} />
}