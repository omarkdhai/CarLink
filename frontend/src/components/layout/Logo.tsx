import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'
import carlinkLogo from '@/assets/CarLink-logo.svg'
import { useAuth } from '@/features/auth/AuthContext'

export function Logo({ className, link = true }: { className?: string; link?: boolean }) {
  const { isAuthenticated } = useAuth()
  const mark = (
    <span className={cn('inline-flex items-center gap-1 sm:gap-1.5', className)}>
      <img src={carlinkLogo} alt="" className="h-7 sm:h-8 w-auto" aria-hidden />
      <span className="font-display text-lg sm:text-[1.375rem] font-black leading-tight tracking-[-0.028em]">
        <span className="text-[#0F172A] text-3xl">Car</span>
        <span className="text-[#2563EB] text-3xl">Link</span>
      </span>
    </span>
  )
  return link ? <Link to={isAuthenticated ? '/dashboard' : '/'}>{mark}</Link> : mark
}