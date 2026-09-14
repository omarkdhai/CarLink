import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import fr from '@/locales/fr.json'
import en from '@/locales/en.json'
import ar from '@/locales/ar.json'

/**
 * Internationalization — French is the default language.
 * Architecture supports Arabic (RTL) later via i18next; the `dir` attribute
 * is set on <html> by App.tsx based on the active language.
 */
export const SUPPORTED_LANGUAGES = [
  { code: 'fr', label: 'Français', short: 'fr' },
  { code: 'en', label: 'English', short: 'en' },
  { code: 'ar', label: 'العربية', short: 'ع' },
] as const

export type LanguageCode = (typeof SUPPORTED_LANGUAGES)[number]['code']

const saved = localStorage.getItem('carlink.lang') as LanguageCode | null
const detected = saved ?? 'fr'

i18n.use(initReactI18next).init({
  resources: {
    fr: { translation: fr },
    en: { translation: en },
    ar: { translation: ar },
  },
  lng: detected,
  fallbackLng: 'fr',
  interpolation: {
    escapeValue: false, // React already escapes
  },
  returnNull: false,
})

export function getDirection(lng: string): 'ltr' | 'rtl' {
  return lng === 'ar' ? 'rtl' : 'ltr'
}

export function applyDocumentDirection(lng: string): void {
  document.documentElement.lang = lng
  document.documentElement.dir = getDirection(lng)
}

i18n.on('languageChanged', applyDocumentDirection)
applyDocumentDirection(i18n.language)

export default i18n