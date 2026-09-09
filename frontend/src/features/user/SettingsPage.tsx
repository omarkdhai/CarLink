import { useTranslation } from 'react-i18next'
import { Languages, Trash2, Info } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'
import { Button } from '@/components/ui/Button'

export default function SettingsPage() {
  const { t } = useTranslation()

  return (
    <div>
      <PageHeader title={t('settings.title')} subtitle={t('settings.subtitle')} />

      <div className="max-w-2xl space-y-6">
        {/* Language */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-lg bg-primary-soft text-primary flex items-center justify-center">
                <Languages className="h-5 w-5" aria-hidden />
              </div>
              <div>
                <CardTitle>{t('settings.language')}</CardTitle>
                <CardDescription>{t('settings.languageHint')}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <LanguageSwitcher />
          </CardContent>
        </Card>

        {/* Danger zone */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-lg bg-destructive-soft text-destructive flex items-center justify-center">
                <Trash2 className="h-5 w-5" aria-hidden />
              </div>
              <div>
                <CardTitle>{t('settings.dangerZone')}</CardTitle>
                <CardDescription>{t('settings.deleteAccountHint')}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex items-start gap-3 rounded-lg bg-muted p-4 mb-4">
              <Info className="h-4 w-4 text-muted-fg shrink-0 mt-0.5" aria-hidden />
              <p className="text-sm text-muted-fg">{t('profile.deleteNotAvailable')}</p>
            </div>
            <Button variant="danger" disabled>
              <Trash2 className="h-4 w-4" aria-hidden />
              {t('settings.deleteAccount')}
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
