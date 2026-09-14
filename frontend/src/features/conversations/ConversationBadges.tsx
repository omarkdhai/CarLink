import { useTranslation } from 'react-i18next'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import type { ContactChannel, ConversationStatus } from '@/types'

const STATUS_TONE: Record<ConversationStatus, BadgeTone> = {
  SENT: 'success',
  PENDING: 'warning',
  FAILED: 'danger',
  EXPIRED: 'neutral',
}

export function ChannelBadge({ channel }: { channel: ContactChannel }) {
  const { t } = useTranslation()
  return (
    <Badge tone={channel === 'WHATSAPP' ? 'whatsapp' : 'sms'}>
      {t(`contact.${channel.toLowerCase()}`)}
    </Badge>
  )
}

export function ConversationStatusBadge({ status }: { status: ConversationStatus }) {
  const { t } = useTranslation()
  return <Badge tone={STATUS_TONE[status]}>{t(`message.${status.toLowerCase()}`)}</Badge>
}
