import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function ConversationDetailPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('message.conversation')} />
}