import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { KeyRound, MessageSquareText, ScanLine, ShoppingBag, Sticker } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Button, buttonClasses } from '@/components/ui/Button'
import { ErrorState } from '@/components/shared/ErrorState'
import { useToast } from '@/components/ui/Toast'
import { stickerApi, stickerQueryKeys } from '@/services/stickerApi'
import { StickerCard } from '@/features/stickers/StickerCard'
import { ActivateStickerSheet } from '@/features/stickers/ActivateStickerSheet'
import { InlineEmpty, SectionCard } from '@/features/account/OwnerUI'

/**
 * The authenticated user's physical stickers. One sticker = one car; new
 * stickers are activated here with their printed code, bound ones can be
 * released.
 */
export default function StickersPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [activating, setActivating] = useState(false)

  const mine = useQuery({
    queryKey: stickerQueryKeys.mine,
    queryFn: () => stickerApi.listMine(),
  })
  const stickers = mine.data ?? []

  const orderLink = (variant: 'primary' | 'outline') => (
    <Link to="/order" className={buttonClasses({ variant })}>
      <ShoppingBag className="h-4 w-4" aria-hidden />
      {t('stickers.orderCta')}
    </Link>
  )

  return (
    <div className="motion-stagger space-y-6 sm:space-y-8">
      <PageHeader
        title={t('stickers.title')}
        subtitle={t('stickers.subtitle')}
        actions={
          <>
            {orderLink('outline')}
            <Button onClick={() => setActivating(true)}>
              <KeyRound className="h-4 w-4" aria-hidden />
              {t('stickers.activateCta')}
            </Button>
          </>
        }
      />

      {mine.isError && <ErrorState message={t('errors.generic')} onRetry={() => mine.refetch()} />}

      {mine.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-busy="true">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-56 rounded-2xl border border-border bg-surface p-5 space-y-4">
              <div className="flex gap-3">
                <div className="h-12 w-12 rounded-xl bg-muted animate-pulse" />
                <div className="flex-1 space-y-2 pt-1">
                  <div className="h-4 w-2/3 rounded bg-muted animate-pulse" />
                  <div className="h-3 w-1/2 rounded bg-muted animate-pulse" />
                </div>
              </div>
              <div className="h-3 w-full rounded bg-muted animate-pulse" />
              <div className="h-3 w-3/4 rounded bg-muted animate-pulse" />
            </div>
          ))}
        </div>
      ) : stickers.length > 0 ? (
        <ul className="motion-stagger grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {stickers.map((s) => (
            <li key={s.id}>
              <StickerCard sticker={s} onMessage={toast.show} />
            </li>
          ))}
        </ul>
      ) : (
        !mine.isError && (
          <InlineEmpty
            icon={ScanLine}
            title={t('stickers.empty')}
            hint={t('stickers.emptyHint')}
            action={
              <div className="flex flex-wrap justify-center gap-2">
                {orderLink('primary')}
                <Button variant="outline" onClick={() => setActivating(true)}>
                  <KeyRound className="h-4 w-4" aria-hidden />
                  {t('stickers.activateCta')}
                </Button>
              </div>
            }
          />
        )
      )}

      <SectionCard title={t('stickers.howTitle')}>
        <ol className="motion-stagger grid gap-4 sm:grid-cols-3">
          {[
            { icon: ShoppingBag, key: 'step1' },
            { icon: Sticker, key: 'step2' },
            { icon: MessageSquareText, key: 'step3' },
          ].map(({ icon: Icon, key }, i) => (
            <li key={key} className="flex gap-3 rounded-xl bg-muted/40 p-4">
              <span className="h-10 w-10 shrink-0 rounded-xl bg-surface text-primary border border-border flex items-center justify-center" aria-hidden>
                <Icon className="h-5 w-5" />
              </span>
              <span className="min-w-0">
                <span className="block text-xs font-semibold uppercase tracking-wide text-muted-fg">
                  {t('stickers.stepLabel', { n: i + 1 })}
                </span>
                <span className="block font-semibold text-heading mt-0.5">{t(`stickers.how.${key}`)}</span>
                <span className="block text-sm text-muted-fg mt-1 leading-relaxed">{t(`stickers.how.${key}Hint`)}</span>
              </span>
            </li>
          ))}
        </ol>
      </SectionCard>

      <ActivateStickerSheet
        open={activating}
        onClose={() => setActivating(false)}
        onDone={() => {
          setActivating(false)
          toast.show(t('stickers.activated'))
        }}
      />
      {toast.node}
    </div>
  )
}
