type FragmentFlow = 'email-verification' | 'password-reset'

const capturedTokens = new Map<string, string>()

export function captureFragmentToken(flow: FragmentFlow, locationKey: string) {
  const cacheKey = `${flow}:${locationKey}`
  const hash = window.location.hash
  if (!hash) {
    return capturedTokens.get(cacheKey)
  }

  const token = new URLSearchParams(hash.slice(1)).get('token') || undefined
  window.history.replaceState(
    window.history.state,
    '',
    `${window.location.pathname}${window.location.search}`,
  )

  if (token) {
    capturedTokens.set(cacheKey, token)
    queueMicrotask(() => {
      if (capturedTokens.get(cacheKey) === token) {
        capturedTokens.delete(cacheKey)
      }
    })
  }
  return token
}
