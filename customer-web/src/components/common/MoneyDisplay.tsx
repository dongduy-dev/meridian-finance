import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/format/presentation'

export function MoneyDisplay({
  value,
  className,
  emphasis = 'inline',
}: {
  value: number
  className?: string
  emphasis?: 'inline' | 'primary'
}) {
  return (
    <span className={cn('min-w-0 [overflow-wrap:anywhere] font-semibold tabular-nums', emphasis === 'primary' && 'type-money', className)}>
      {formatMoney(value)}
    </span>
  )
}
