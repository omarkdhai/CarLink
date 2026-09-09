import { useEffect, useRef, useState } from 'react'
import { Languages } from 'lucide-react'
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
        className="inline-flex items-center gap-2 h-9 px-3 rounded-lg text-sm font-medium text-muted-fg hover:bg-muted hover:text-foreground transition-colors"
        aria-haspopup="menu"
        aria-expanded={open}
      >
        <Languages className="h-4 w-4" aria-hidden />
        <span>{current.label}</span>
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