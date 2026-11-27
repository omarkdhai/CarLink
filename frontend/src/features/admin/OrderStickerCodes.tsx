import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { Check, Copy, Download, KeyRound, Lock, QrCode } from 'lucide-react'
import { adminApi } from '@/services/adminApi'
import { Badge } from '@/components/ui/Badge'
import { Spinner } from '@/components/shared/Spinner'
import type { AdminOrderStickerView, StickerStatus, StickerStyle } from '@/types'
import { DetailSection } from './components/AdminList'
import { renderStickerArt } from './stickerArt'

const STATUS_TONE: Record<StickerStatus, 'success' | 'warning' | 'neutral'> = {
  BOUND: 'success',
  UNBOUND: 'warning',
  DEACTIVATED: 'neutral',
}

/** Renders the print-ready sticker for a URL; returns an object URL (revoked on change). */
function useStickerArt(publicUrl: string | null, style: StickerStyle) {
  const [art, setArt] = useState<{ url: string | null; failed: boolean }>({ url: null, failed: false })
  useEffect(() => {
    if (!publicUrl) return
    let objectUrl: string | null = null
    let cancelled = false
    renderStickerArt(publicUrl, style)
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setArt({ url: objectUrl, failed: false })
      })
      .catch(() => !cancelled && setArt({ url: null, failed: true }))
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [publicUrl, style])
  return art
}

function CodeTile({ sticker, n, reference, style }: { sticker: AdminOrderStickerView; n: number; reference: string; style: StickerStyle }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const art = useStickerArt(sticker.codesAvailable ? sticker.publicUrl : null, style)

  async function copy() {
    if (!sticker.activationCode) return
    try {
      await navigator.clipboard.writeText(sticker.activationCode)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1500)
    } catch {
      /* clipboard blocked: the code stays selectable */
    }
  }

  return (
    <li className="flex gap-4 rounded-xl border border-border bg-surface p-3">
      {sticker.codesAvailable && art.url ? (
        <a href={art.url} target="_blank" rel="noreferrer" className="shrink-0" title={t('admin.orders.codes.openFull')}>
          <img
            src={art.url}
            alt={t('admin.orders.codes.qrAlt', { n })}
            className="h-44 w-auto rounded-xl shadow-card"
          />
        </a>
      ) : sticker.codesAvailable && !art.failed ? (
        <span className="h-44 w-24 shrink-0 rounded-xl bg-muted animate-pulse" aria-hidden />
      ) : sticker.codesAvailable && sticker.imageDataUri ? (
        <img
          src={sticker.imageDataUri}
          alt={t('admin.orders.codes.qrAlt', { n })}
          className="h-28 w-28 shrink-0 rounded-lg border border-border bg-white p-1.5"
        />
      ) : (
        <span className="h-28 w-28 shrink-0 rounded-lg border border-dashed border-border bg-muted/40 text-muted-fg flex items-center justify-center" aria-hidden>
          <QrCode className="h-8 w-8" />
        </span>
      )}
      <div className="min-w-0 flex-1">
        <div className="flex items-start justify-between gap-2">
          <p className="text-sm font-semibold text-heading">{t('admin.orders.codes.sticker', { n })}</p>
          <Badge tone={STATUS_TONE[sticker.status]} className="shrink-0">
            {t(`stickers.status.${sticker.status}`)}
          </Badge>
        </div>
        {sticker.codesAvailable && sticker.activationCode ? (
          <>
            <p className="mt-2 text-xs font-medium text-muted-fg">{t('admin.orders.codes.activationCode')}</p>
            <div className="mt-1 flex items-center gap-1.5">
              <span className="inline-flex items-center gap-1.5 rounded-lg border border-primary/30 bg-primary-soft px-2 py-1 font-mono text-sm font-semibold text-primary select-all" dir="ltr">
                <KeyRound className="h-3.5 w-3.5" aria-hidden />
                {sticker.activationCode}
              </span>
              <button
                type="button"
                onClick={copy}
                aria-label={t('admin.orders.codes.copy')}
                title={t('admin.orders.codes.copy')}
                className="h-8 w-8 rounded-lg flex items-center justify-center text-muted-fg hover:bg-muted hover:text-foreground transition-colors"
              >
                {copied ? <Check className="h-4 w-4 text-success" aria-hidden /> : <Copy className="h-4 w-4" aria-hidden />}
              </button>
            </div>
            <div className="mt-3 flex flex-col items-start gap-1.5">
              {art.url && (
                <a
                  href={art.url}
                  download={`${reference}-sticker-${n}.png`}
                  className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-3 py-1.5 text-xs font-semibold text-white hover:bg-primary-hover"
                >
                  <Download className="h-3.5 w-3.5" aria-hidden />
                  {t('admin.orders.codes.downloadSticker')}
                </a>
              )}
              <a
                href={sticker.imageDataUri ?? undefined}
                download={`${reference}-qr-${n}.png`}
                className="inline-flex items-center gap-1.5 text-xs font-semibold text-primary hover:text-primary-hover"
              >
                <Download className="h-3.5 w-3.5" aria-hidden />
                {t('admin.orders.codes.download')}
              </a>
            </div>
          </>
        ) : (
          <p className="mt-2 text-xs leading-5 text-muted-fg">{t('admin.orders.codes.unavailable')}</p>
        )}
      </div>
    </li>
  )
}

/**
 * The QR code and activation code of each sticker in an order, for printing.
 * Fetched only when the order is opened; every fetch is audited server-side.
 */
export function OrderStickerCodes({ reference, style }: { reference: string; style: StickerStyle }) {
  const { t } = useTranslation()
  const q = useQuery({
    queryKey: ['admin', 'orders', reference, 'stickers'],
    queryFn: () => adminApi.orders.stickers(reference),
    staleTime: 5 * 60_000,
  })

  return (
    <DetailSection icon={QrCode} title={t('admin.orders.codes.title')}>
      <p className="mb-3 flex items-start gap-2 text-xs leading-5 text-muted-fg">
        <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
        {t('admin.orders.codes.confidential')}
      </p>
      {q.isPending ? (
        <div className="flex justify-center py-6"><Spinner /></div>
      ) : q.isError ? (
        <p className="text-sm text-destructive">{t('errors.generic')}</p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {q.data.map((s, i) => (
            <CodeTile key={s.id} sticker={s} n={i + 1} reference={reference} style={style} />
          ))}
        </ul>
      )}
    </DetailSection>
  )
}
