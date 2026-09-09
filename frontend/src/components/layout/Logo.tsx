import { Link } from 'react-router-dom'
import { CarFront } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { cn } from '@/lib/cn'

export function Logo({ className, link = true }: { className?: string; link?: boolean }) {
  const { t } = useTranslation()
  const mark = (
    <span className={cn('inline-flex items-center gap-2 font-bold text-heading tracking-tight', className)}>
      <span className="h-9 w-9 rounded-lg bg-primary flex items-center justify-center text-white">
        <CarFront className="h-5 w-5" aria-hidden />
      </span>
      <span className="text-lg">{t('app.name')}</span>
    </span>
  )
  return link ? <Link to="/">{mark}</Link> : mark
}