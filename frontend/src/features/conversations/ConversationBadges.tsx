import { useTranslation } from 'react-i18next'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import type { ContactChannel, ConversationDelivery, ConversationStatus, DeliveryStatus } from '@/types'

const STATUS_TONE: Record<ConversationStatus, BadgeTone> = {
  DELIVERED: 'success',
  SENT: 'primary',
  PENDING: 'warning',
  FAILED: 'danger',
  EXPIRED: 'neutral',
}

export function ChannelBadge({ channel }: { channel: ContactChannel }) {
  const { t } = useTranslation()
  return (
    <Badge tone="sms">
      {t(`contact.${channel.toLowerCase()}`)}
    </Badge>
  )
}

const DELIVERY_TONE: Record<DeliveryStatus, BadgeTone> = {
  READ: 'success',
  DELIVERED: 'success',
  SENT: 'primary',
  FAILED: 'danger',
}

/** One badge per channel, e.g. "SMS · Delivered" and "WhatsApp · Read". */
export function DeliveryBadges({ deliveries }: { deliveries: ConversationDelivery[] }) {
  const { t } = useTranslation()
  return (
    <>
      {deliveries.map((d) => (
        <Badge key={d.channel} tone={DELIVERY_TONE[d.status]}>
          {t(`contact.${d.channel.toLowerCase()}`)} · {t(`message.delivery.${d.status.toLowerCase()}`)}
        </Badge>
      ))}
    </>
  )
}

export function ConversationStatusBadge({ status }: { status: ConversationStatus }) {
  const { t } = useTranslation()
  return <Badge tone={STATUS_TONE[status]}>{t(`message.${status.toLowerCase()}`)}</Badge>
}
