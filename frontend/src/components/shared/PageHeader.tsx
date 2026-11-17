import type { ReactNode } from 'react'

interface PageHeaderProps {
  title: string
  subtitle?: string
  actions?: ReactNode
}

/**
 * Page title block. Title uses the display font (h1); the subtitle is capped
 * at a readable line length. Actions wrap under the title on phones and keep
 * their text labels so no button is ever icon-only.
 */
export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between mb-6 sm:mb-8">
      <div className="min-w-0">
        <h1 className="text-2xl sm:text-3xl font-bold text-heading tracking-tight leading-tight">{title}</h1>
        {subtitle && <p className="text-sm sm:text-base text-muted-fg mt-1.5 max-w-prose leading-relaxed">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2 shrink-0">{actions}</div>}
    </div>
  )
}
