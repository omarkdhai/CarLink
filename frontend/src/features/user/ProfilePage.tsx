import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function ProfilePage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('profile.title')} />
}