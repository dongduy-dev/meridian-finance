export function validateWholeVnd(value: string, min?: number, max?: number) {
  if (!value) return 'Enter an amount.'
  if (!/^\d+$/.test(value)) return 'Enter a positive whole-VND amount using digits only.'
  const amount = BigInt(value)
  if (amount <= 0n) return 'Amount must be greater than zero.'
  if (amount > BigInt(Number.MAX_SAFE_INTEGER)) return 'Amount is too large to submit safely.'
  if (min !== undefined && amount < BigInt(min)) return 'Amount is below the current product minimum.'
  if (max !== undefined && amount > BigInt(max)) return 'Amount is above the current product maximum.'
  return true
}
