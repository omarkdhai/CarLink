import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'
import carlinkLogo from '@/assets/CarLink-logo.svg'
import { useAuth } from '@/features/auth/AuthContext'

type LogoSize = 'sm' | 'md'

/** Fixed heights for the app shell: `sm` for 56px mobile bars, `md` for the sidebar. */
const SIZES: Record<LogoSize, string> = {
  sm: 'h-7',
  md: 'h-8',
}

/** The horizontal CarLink lockup (symbol + wordmark in one SVG). */
export function Logo({ className, link = true, size }: { className?: string; link?: boolean; size?: LogoSize }) {
  const { isAuthenticated } = useAuth()
  const mark = (
    <span className={cn('inline-flex items-center', className)}>
      <img src={carlinkLogo} alt="CarLink" className={cn('w-auto', size ? SIZES[size] : 'h-8 sm:h-9')} />
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
