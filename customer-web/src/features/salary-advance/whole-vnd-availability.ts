// Limit facts may contain decimals; requests may use only complete VND.
export function wholeVndAvailability(availableAmount: number): number | null {
  if (!Number.isFinite(availableAmount) || availableAmount < 0) return null
  const wholeAmount = Math.floor(availableAmount)
  return Number.isSafeInteger(wholeAmount) ? wholeAmount : null
}
