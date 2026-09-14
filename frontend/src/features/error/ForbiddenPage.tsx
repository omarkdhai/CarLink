import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ShieldX } from 'lucide-react'
import { buttonClasses } from '@/components/ui/Button'

export default function ForbiddenPage() {
  const { t } = useTranslation()
  return (
    <div className="min-h-screen flex items-center justify-center px-4 bg-background">
      <div className="text-center max-w-sm">
        <div className="h-16 w-16 rounded-full bg-destructive-soft flex items-center justify-center mx-auto mb-5">
          <ShieldX className="h-8 w-8 text-destructive" aria-hidden />
        </div>
        <h1 className="text-2xl font-bold text-heading">403</h1>
        <p className="text-muted-fg mt-2 mb-6">{t('errors.forbidden')}</p>
        <Link to="/dashboard" className={buttonClasses()}>
          {t('routes.dashboard')}
        </Link>
      </div>
    </div>
  )
}