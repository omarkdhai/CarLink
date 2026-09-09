import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { SearchX } from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'

export default function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <div className="min-h-screen flex items-center justify-center px-4 bg-background">
      <div className="text-center max-w-sm">
        <div className="h-16 w-16 rounded-full bg-muted flex items-center justify-center mx-auto mb-5">
          <SearchX className="h-8 w-8 text-muted-fg" aria-hidden />
        </div>
        <h1 className="text-4xl font-bold text-heading">404</h1>
        <p className="text-muted-fg mt-2 mb-6">{t('errors.notFound')}</p>
        <Link to="/" className={buttonClasses()}>
          {t('routes.dashboard')}
        </Link>
      </div>
    </div>
  )
}