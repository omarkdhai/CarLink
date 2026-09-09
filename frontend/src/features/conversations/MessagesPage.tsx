import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function MessagesPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('message.title')} subtitle={t('message.subtitle')} />
}