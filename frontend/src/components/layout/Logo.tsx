import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'
import carlinkLogo from '@/assets/CarLink-logo.png'

export function Logo({ className, link = true }: { className?: string; link?: boolean }) {
  const mark = (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <img src={carlinkLogo} alt="" className="h-11 w-auto" aria-hidden />
      <span className="text-3xl tracking-tight leading-tight" style={{ fontFamily: "'Sora', sans-serif" }}>
        <span className="text-[#0F172A]" style={{ fontWeight: 600 }}>Car</span>
        <span className="text-[#2563EB]" style={{ fontWeight: 700 }}>Link</span>
      </span>
    </span>
  )
  return link ? <Link to="/">{mark}</Link> : mark
}