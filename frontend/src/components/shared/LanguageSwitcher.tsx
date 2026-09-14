import { useEffect, useRef, useState } from 'react'
import { Globe, ChevronDown } from 'lucide-react'
import i18n, { SUPPORTED_LANGUAGES, type LanguageCode } from '@/lib/i18n'

/** Compact language dropdown. RTL-ready: Arabic flips the whole document. */
export function LanguageSwitcher() {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDocClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [open])

  const current = SUPPORTED_LANGUAGES.find((l) => l.code === i18n.language) ?? SUPPORTED_LANGUAGES[0]

  function select(code: LanguageCode) {
    void i18n.changeLanguage(code)
    localStorage.setItem('carlink.lang', code)
    setOpen(false)
  }

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="inline-flex items-center gap-2 h-10 px-3.5 rounded-xl border border-border bg-surface text-sm font-semibold text-foreground hover:border-primary/50 hover:text-primary transition-colors"
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <Globe className="h-4 w-4" aria-hidden />
        <span>{current.short}</span>
        <ChevronDown className={`h-3.5 w-3.5 text-muted-fg transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden />
      </button>
      {open && (
        <div
          role="menu"
          className="absolute end-0 mt-2 w-40 bg-surface border border-border rounded-lg shadow-pop py-1 z-50"
        >
          {SUPPORTED_LANGUAGES.map((lang) => (
            <button
              key={lang.code}
              role="menuitemradio"
              aria-checked={lang.code === current.code}
              onClick={() => select(lang.code)}
              className={`w-full text-start px-3 py-2 text-sm hover:bg-muted transition-colors ${
                lang.code === current.code ? 'font-semibold text-primary' : 'text-foreground'
              }`}
            >
              {lang.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}