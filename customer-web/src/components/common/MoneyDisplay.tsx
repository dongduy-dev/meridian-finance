import { cn } from '@/lib/cn'
import { formatMoney } from '@/lib/format/presentation'

export function MoneyDisplay({
  value,
  className,
}: {
  value: number
  className?: string
}) {
  return (
    <span className={cn('min-w-0 break-words font-semibold tabular-nums', className)}>
      {formatMoney(value)}
    </span>
  )
}
