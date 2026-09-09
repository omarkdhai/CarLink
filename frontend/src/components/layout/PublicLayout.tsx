import type { ReactNode } from 'react'
import { Logo } from '@/components/layout/Logo'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'

/** Minimal chrome for non-app pages (landing, auth). */
export function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <header className="px-6 py-4 flex items-center justify-between border-b border-border bg-surface/60">
        <Logo />
        <LanguageSwitcher />
      </header>
      <main className="flex-1 flex flex-col px-4 py-8">{children}</main>
      <footer className="py-4 text-center text-xs text-muted-fg">© {new Date().getFullYear()} CarLink</footer>
    </div>
  )
}

// Copyright year is non-deterministic for SSR but fine for this SPA.