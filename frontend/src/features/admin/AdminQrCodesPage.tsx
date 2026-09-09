import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function AdminQrCodesPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('admin.qrCodes')} />
}