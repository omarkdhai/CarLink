import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Languages, Trash2, Info, Bell, Check } from 'lucide-react'
import { PageHeader } from '@/components/shared/PageHeader'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/Card'
import { Button } from '@/components/ui/Button'
import { cn } from '@/lib/cn'

const languages = [
  { code: 'en', label: 'English', flag: '🇬🇧' },
  { code: 'fr', label: 'Français', flag: '🇫🇷' },
  { code: 'ar', label: 'العربية', flag: '🇹🇳' },
]

export default function SettingsPage() {
  const { t, i18n } = useTranslation()
  const [notificationsEnabled, setNotificationsEnabled] = useState(true)

  const currentLang = i18n.language?.split('-')[0] || 'en'

  const changeLanguage = (lng: string) => {
    i18n.changeLanguage(lng)
  }

  return (
    <div>
      <div className="mb-6">
        <PageHeader title={t('settings.title')} subtitle={t('settings.subtitle')} />
      </div>

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
          <CardContent className="space-y-2">
            {languages.map((lang) => (
              <button
                key={lang.code}
                onClick={() => changeLanguage(lang.code)}
                className={cn(
                  'w-full flex items-center justify-between p-3 rounded-lg border transition-all touch-target',
                  currentLang === lang.code
                    ? 'border-primary bg-primary-soft/50'
                    : 'border-border hover:border-primary/40 hover:bg-muted/50'
                )}
              >
                <div className="flex items-center gap-3">
                  <span className="text-lg">{lang.flag}</span>
                  <span className={cn('text-sm font-medium', currentLang === lang.code ? 'text-primary' : 'text-foreground')}>
                    {lang.label}
                  </span>
                </div>
                {currentLang === lang.code && <Check className="h-4 w-4 text-primary" />}
              </button>
            ))}
          </CardContent>
        </Card>

        {/* Notifications */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-3">
              <div className="h-10 w-10 rounded-lg bg-primary-soft text-primary flex items-center justify-center">
                <Bell className="h-5 w-5" aria-hidden />
              </div>
              <div>
                <CardTitle>{t('notifications.title') || 'Notifications'}</CardTitle>
                <CardDescription>{t('notifications.subtitle') || 'Manage notification preferences'}</CardDescription>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-foreground">{t('notifications.title') || 'Notifications'}</p>
                <p className="text-xs text-muted-fg">{t('notifications.subtitle') || 'Receive alerts for new messages'}</p>
              </div>
              <button
                onClick={() => setNotificationsEnabled(!notificationsEnabled)}
                className={cn(
                  'relative inline-flex h-6 w-11 items-center rounded-full transition-colors',
                  notificationsEnabled ? 'bg-primary' : 'bg-muted'
                )}
                role="switch"
                aria-checked={notificationsEnabled}
              >
                <span
                  className={cn(
                    'inline-block h-4 w-4 transform rounded-full bg-white transition-transform',
                    notificationsEnabled ? 'translate-x-6' : 'translate-x-1'
                  )}
                />
              </button>
            </div>
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
