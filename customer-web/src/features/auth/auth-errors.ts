import { ApiError } from '@/lib/api'

export function isAuthApiError(error: unknown, status: number, errorCode: string) {
  return (
    error instanceof ApiError &&
    error.status === status &&
    error.errorCode === errorCode
  )
}
export function unexpectedAuthError(error: unknown) {
  return {
    title: 'We could not complete that request',
    description: 'Your information is still here. Please try again.',
    requestId: error instanceof ApiError ? error.requestId : undefined,
  }
}

export function focusServerError() {
  window.requestAnimationFrame(() => {
    document.querySelector<HTMLElement>('[data-server-error]')?.focus()
  })
}
