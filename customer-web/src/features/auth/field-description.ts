export function fieldDescriptionIds(field: string, hasDescription: boolean, hasError: boolean) {
  return [hasDescription ? `${field}-description` : undefined, hasError ? `${field}-error` : undefined]
    .filter(Boolean)
    .join(' ') || undefined
}
