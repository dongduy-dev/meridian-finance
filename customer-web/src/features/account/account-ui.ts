export function fieldDescriptionIds(
  fieldId: string,
  hasDescription: boolean,
  hasError: boolean,
) {
  return [hasDescription ? `${fieldId}-description` : undefined, hasError ? `${fieldId}-error` : undefined]
    .filter(Boolean)
    .join(' ') || undefined
}

export function focusAccountError() {
  window.requestAnimationFrame(() => {
    document.querySelector<HTMLElement>('[data-account-error]')?.focus()
  })
}
