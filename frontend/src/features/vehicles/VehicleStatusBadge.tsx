import { useTranslation } from 'react-i18next'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import type { VehicleStatus } from '@/types'

export function VehicleStatusBadge({ status }: { status: VehicleStatus }) {
  const { t } = useTranslation()
  const tones: Record<VehicleStatus, BadgeTone> = { ACTIVE: 'success', ARCHIVED: 'neutral' }
  const labels: Record<VehicleStatus, string> = { ACTIVE: t('vehicle.active'), ARCHIVED: t('vehicle.archived') }
  return <Badge tone={tones[status]}>{labels[status]}</Badge>
}