import expandedLogo from '@/assets/brand/meridian-logo-expanded.svg'
import markLogo from '@/assets/brand/meridian-logo-mark.svg'
import primaryLogo from '@/assets/brand/meridian-logo.svg'
import { cn } from '@/lib/cn'

export interface MeridianLogoProps {
  variant?: 'primary' | 'expanded' | 'mark'
  className?: string
  decorative?: boolean
}

const logoByVariant = {
  primary: { src: primaryLogo, alt: 'Meridian' },
  expanded: { src: expandedLogo, alt: 'Meridian Finance' },
  mark: { src: markLogo, alt: 'Meridian' },
} as const

export function MeridianLogo({
  variant = 'primary',
  className,
  decorative = false,
}: MeridianLogoProps) {
  const logo = logoByVariant[variant]
  return (
    <img
      src={logo.src}
      alt={decorative ? '' : logo.alt}
      aria-hidden={decorative || undefined}
      className={cn('block h-auto max-w-full object-contain', className)}
      draggable={false}
    />
  )
}
