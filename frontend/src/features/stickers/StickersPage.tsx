import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { ScanLine, ShoppingBag } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { buttonClasses } from '@/components/ui/Button'
import { EmptyState } from '@/components/shared/EmptyState'
import { SkeletonGrid } from '@/components/shared/SkeletonGrid'
import { ErrorState } from '@/components/shared/ErrorState'
import { stickerApi, stickerQueryKeys } from '@/services/stickerApi'
import { StickerCard } from '@/features/stickers/StickerCard'
import type { StickerView } from '@/types'

/**
 * The authenticated user's physical stickers. One sticker = one car; bound
 * stickers can be deactivated (released) from here.
 */
export default function StickersPage() {
  const { t } = useTranslation()

  const mine = useQuery({
    queryKey: stickerQueryKeys.mine,
    queryFn: () => stickerApi.listMine(),
  })

  return (
    <div>
      <PageHeader
        title={t('stickers.title')}
        subtitle={t('stickers.subtitle')}
        actions={
          <Link to="/order" className={buttonClasses()}>
            <ShoppingBag className="h-4 w-4" aria-hidden />
            {t('stickers.orderCta')}
          </Link>
        }
      />

      {mine.isError && <ErrorState message={t('errors.generic')} onRetry={() => mine.refetch()} />}

      {mine.isPending ? (
        <SkeletonGrid count={4} />
      ) : mine.data && mine.data.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {mine.data.map((s: StickerView) => (
            <StickerCard key={s.id} sticker={s} />
          ))}
        </div>
      ) : (
        <div className="bg-surface border border-border rounded-2xl">
          <EmptyState
            icon={<ScanLine className="h-7 w-7" aria-hidden />}
            title={t('stickers.empty')}
            hint={t('stickers.emptyHint')}
            action={
              <Link to="/order" className={buttonClasses()}>
                <ShoppingBag className="h-4 w-4" aria-hidden />
                {t('stickers.orderCta')}
              </Link>
            }
          />
        </div>
      )}
    </div>
  )
}