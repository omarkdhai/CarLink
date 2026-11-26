import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Banknote, Calendar, Info, KeyRound, Package, Receipt, ShoppingBag } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { ErrorState } from '@/components/shared/ErrorState'
import { CountUp } from '@/components/shared/CountUp'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { buttonClasses } from '@/components/ui/Button'
import { InlineEmpty } from '@/features/account/OwnerUI'
import { useAuth } from '@/features/auth/AuthContext'
import { orderApi, orderQueryKeys, PACK_LABELS } from '@/services/orderApi'
import { formatDate } from '@/lib/format'
import { cn } from '@/lib/cn'
import type { MyOrderResponse, OrderStatus } from '@/types'

const STATUS_TONE: Record<OrderStatus, BadgeTone> = {
  PLACED: 'primary',
  DELIVERED: 'success',
  CANCELLED: 'neutral',
}

/** Plain metric tile, same look as the dashboard's StatTile but not a link. */
function SummaryTile({ icon: Icon, tone, value, suffix, label }: {
  icon: typeof Receipt
  tone: string
  value: number
  suffix?: string
  label: string
}) {
  return (
    <div className="bg-surface border border-border rounded-2xl p-4 sm:p-5 shadow-card flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4 min-w-0">
      <span className={cn('h-10 w-10 sm:h-12 sm:w-12 shrink-0 rounded-xl flex items-center justify-center', tone)} aria-hidden>
        <Icon className="h-5 w-5 sm:h-6 sm:w-6" />
      </span>
      <span className="min-w-0">
        <span className="block font-display text-2xl sm:text-3xl font-bold text-heading tabular-nums leading-none">
          <CountUp value={value} />
          {suffix && <span className="ms-1 text-sm sm:text-base font-semibold text-muted-fg">{suffix}</span>}
        </span>
        <span className="block text-xs sm:text-sm text-muted-fg mt-1.5 leading-snug">{label}</span>
      </span>
    </div>
  )
}

/** One purchase: reference, date, status, the packs bought, total, activation progress. */
function OrderCard({ order }: { order: MyOrderResponse }) {
  const { t } = useTranslation()
  const cancelled = order.status === 'CANCELLED'
  const remaining = Math.max(order.stickerCount - order.activatedCount, 0)
  const progress = order.stickerCount > 0 ? Math.min(order.activatedCount / order.stickerCount, 1) : 0

  return (
    <article className={cn('bg-surface border border-border rounded-2xl shadow-card overflow-hidden', cancelled && 'opacity-75')}>
      <div className="p-5 sm:p-6">
        <div className="flex items-start gap-3">
          <span className="h-12 w-12 shrink-0 rounded-xl bg-primary-soft text-primary flex items-center justify-center" aria-hidden>
            <Package className="h-6 w-6" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <h3 className="font-bold text-heading leading-snug font-mono tracking-wide" dir="ltr">
                {order.reference}
              </h3>
              <Badge tone={STATUS_TONE[order.status]} className="shrink-0">
                {t(`orders.status.${order.status}`)}
              </Badge>
            </div>
            <p className="text-sm text-muted-fg mt-1 flex items-center gap-1.5">
              <Calendar className="h-4 w-4 shrink-0" aria-hidden />
              {formatDate(order.createdAt)}
            </p>
          </div>
        </div>

        <ul className="mt-5 divide-y divide-border rounded-xl bg-muted/40">
          {order.items.map((item, i) => (
            <li key={i} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
              <span className="min-w-0">
                <span className="font-semibold text-foreground">
                  {item.quantity} × {t(PACK_LABELS[item.stickerPackage])}
                </span>
                <span className="block text-xs text-muted-fg mt-0.5">
                  {t('orders.stickersCount', { count: item.quantity * item.stickersPerPack })}
                </span>
              </span>
              <span className="shrink-0 font-semibold text-heading tabular-nums" dir="ltr">
                {item.subtotal} {order.currency}
              </span>
            </li>
          ))}
        </ul>
      </div>

      <div className="border-t border-border bg-muted/20 px-5 sm:px-6 py-4 flex flex-col sm:flex-row sm:items-center gap-4">
        <div className="min-w-0 flex-1">
          {cancelled ? (
            <p className="text-sm text-muted-fg">{t('orders.cancelledHint')}</p>
          ) : (
            <>
              <div className="flex items-center justify-between gap-3 text-sm">
                <span className="font-semibold text-foreground">
                  {t('orders.activated', { done: order.activatedCount, total: order.stickerCount })}
                </span>
                {remaining === 0 && <span className="text-success font-semibold">{t('orders.allActive')}</span>}
              </div>
              <div
                className="mt-2 h-2 rounded-full bg-muted overflow-hidden"
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={order.stickerCount}
                aria-valuenow={order.activatedCount}
                aria-label={t('orders.activated', { done: order.activatedCount, total: order.stickerCount })}
              >
                <div className="h-full rounded-full bg-success transition-[width] duration-700" style={{ width: `${progress * 100}%` }} />
              </div>
            </>
          )}
        </div>
        <div className="flex items-center justify-between sm:justify-end gap-4 shrink-0">
          <span className="text-end">
            <span className="block text-xs text-muted-fg">{t('orders.total')}</span>
            <span className="block font-display text-lg font-bold text-heading tabular-nums" dir="ltr">
              {order.totalAmount} {order.currency}
            </span>
          </span>
          {!cancelled && remaining > 0 && (
            <Link to="/stickers" className={buttonClasses({ variant: 'outline', size: 'sm' })}>
              <KeyRound className="h-4 w-4" aria-hidden />
              {t('orders.activateCta')}
            </Link>
          )}
        </div>
      </div>
    </article>
  )
}

/**
 * The signed-in owner's sticker purchases: orders placed with their verified
 * e-mail, and orders whose stickers they activated. No delivery details are
 * shown (the server never sends them).
 */
export default function MyOrdersPage() {
  const { t } = useTranslation()
  const { user } = useAuth()

  const mine = useQuery({ queryKey: orderQueryKeys.mine, queryFn: () => orderApi.listMine() })
  const orders = mine.data ?? []
  const counted = orders.filter((o) => o.status !== 'CANCELLED')
  const stickers = counted.reduce((n, o) => n + o.stickerCount, 0)
  const spent = counted.reduce((n, o) => n + Number(o.totalAmount), 0)

  const orderLink = (variant: 'primary' | 'outline') => (
    <Link to="/order" className={buttonClasses({ variant })}>
      <ShoppingBag className="h-4 w-4" aria-hidden />
      {t('stickers.orderCta')}
    </Link>
  )

  return (
    <div className="motion-stagger space-y-6 sm:space-y-8">
      <PageHeader title={t('orders.title')} subtitle={t('orders.subtitle')} actions={orderLink('primary')} />

      {mine.isError && <ErrorState message={t('errors.generic')} onRetry={() => mine.refetch()} />}

      {mine.isPending ? (
        <div className="space-y-4" aria-busy="true">
          <div className="grid grid-cols-3 gap-3 sm:gap-4">
            {[0, 1, 2].map((i) => (
              <div key={i} className="h-24 rounded-2xl border border-border bg-surface animate-pulse" />
            ))}
          </div>
          {[0, 1].map((i) => (
            <div key={i} className="h-56 rounded-2xl border border-border bg-surface p-5 space-y-4">
              <div className="flex gap-3">
                <div className="h-12 w-12 rounded-xl bg-muted animate-pulse" />
                <div className="flex-1 space-y-2 pt-1">
                  <div className="h-4 w-1/3 rounded bg-muted animate-pulse" />
                  <div className="h-3 w-1/4 rounded bg-muted animate-pulse" />
                </div>
              </div>
              <div className="h-16 w-full rounded-xl bg-muted animate-pulse" />
            </div>
          ))}
        </div>
      ) : orders.length > 0 ? (
        <>
          <div className="grid grid-cols-3 gap-3 sm:gap-4">
            <SummaryTile icon={Receipt} tone="bg-primary-soft text-primary" value={counted.length} label={t('orders.statOrders')} />
            <SummaryTile icon={Package} tone="bg-success-soft text-success" value={stickers} label={t('orders.statStickers')} />
            <SummaryTile icon={Banknote} tone="bg-warning-soft text-warning" value={Math.round(spent)} suffix="TND" label={t('orders.statSpent')} />
          </div>
          <ul className="motion-stagger space-y-4">
            {orders.map((o) => (
              <li key={o.reference}>
                <OrderCard order={o} />
              </li>
            ))}
          </ul>
        </>
      ) : (
        !mine.isError && (
          <InlineEmpty icon={Receipt} title={t('orders.empty')} hint={t('orders.emptyHint')} action={orderLink('primary')} />
        )
      )}

      {!mine.isPending && !mine.isError && (
        <p className="flex items-start gap-2 text-sm text-muted-fg leading-relaxed max-w-prose">
          <Info className="h-4 w-4 mt-0.5 shrink-0" aria-hidden />
          <span>{user && !user.emailVerified ? t('orders.verifyHint') : t('orders.whichHint')}</span>
        </p>
      )}
    </div>
  )
}
