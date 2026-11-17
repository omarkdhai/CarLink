import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'
import carlinkLogo from '@/assets/CarLink-logo.svg'
import { useAuth } from '@/features/auth/AuthContext'

type LogoSize = 'sm' | 'md'

/** Fixed sizes for the app shell: `sm` for 56px mobile bars, `md` for the sidebar. */
const SIZES: Record<LogoSize, { mark: string; word: string }> = {
  sm: { mark: 'h-6', word: 'text-xl' },
  md: { mark: 'h-7', word: 'text-2xl' },
}

export function Logo({ className, link = true, size }: { className?: string; link?: boolean; size?: LogoSize }) {
  const { isAuthenticated } = useAuth()
  const s = size ? SIZES[size] : null
  const mark = (
    <span className={cn('inline-flex items-center gap-1 sm:gap-1.5', className)}>
      <img src={carlinkLogo} alt="" className={cn('w-auto', s ? s.mark : 'h-6 sm:h-8')} aria-hidden />
      <span
        className={cn(
          'font-display font-black leading-none tracking-[-0.028em]',
          s ? s.word : 'text-3xl',
        )}
      >
        <span className="text-[#0F172A]">Car</span>
        <span className="text-[#2563EB]">Link</span>
      </span>
    </span>
  )
  return link ? (
    <Link to={isAuthenticated ? '/dashboard' : '/'} aria-label="CarLink" className="rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary">
      {mark}
    </Link>
  ) : (
    mark
  )
}
