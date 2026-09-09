import type { ReactNode } from 'react'
import { Logo } from '@/components/layout/Logo'
import { LanguageSwitcher } from '@/components/shared/LanguageSwitcher'

/** Minimal chrome for non-app pages (landing, auth). */
export function PublicLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col bg-background">
      <a href="#main-content" className="sr-only focus:not-sr-only focus:absolute focus:z-[100] focus:m-2 focus:px-4 focus:py-2 focus:bg-primary focus:text-white focus:rounded-lg">
        Skip to content
      </a>
      <header className="px-6 py-4 flex items-center justify-between border-b border-border bg-surface/60">
        <Logo />
        <LanguageSwitcher />
      </header>
      <main id="main-content" className="flex-1 flex flex-col px-4 py-8">{children}</main>
      <footer className="py-4 text-center text-xs text-muted-fg">© {new Date().getFullYear()} CarLink</footer>
    </div>
  )
}

// Copyright year is non-deterministic for SSR but fine for this SPA.