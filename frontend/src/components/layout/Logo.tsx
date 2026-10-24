import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'
import carlinkLogo from '@/assets/CarLink-logo.svg'

export function Logo({ className, link = true }: { className?: string; link?: boolean }) {
  const mark = (
    <span className={cn('inline-flex items-center gap-1.5 sm:gap-2', className)}>
      <img src={carlinkLogo} alt="" className="h-9 sm:h-8 w-auto" aria-hidden />
      <span className="font-display text-2xl sm:text-4xl font-black leading-tight tracking-[-0.028em]">
        <span className="text-[#0F172A]">Car</span>
        <span className="text-[#2563EB]">Link</span>
      </span>
    </span>
  )
  return link ? <Link to="/">{mark}</Link> : mark
}