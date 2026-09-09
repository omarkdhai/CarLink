import { useTranslation } from 'react-i18next'
import { PlaceholderPage } from '@/components/shared/PlaceholderPage'

export default function AdminAuditLogsPage() {
  const { t } = useTranslation()
  return <PlaceholderPage title={t('admin.auditLogs')} />
}