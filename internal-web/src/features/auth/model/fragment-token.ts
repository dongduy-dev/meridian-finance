const capturedTokens = new Map<string, string>()

export function captureSetupToken(locationKey: string): string | undefined {
  const hash = window.location.hash
  if (!hash) return capturedTokens.get(locationKey)
  const token = new URLSearchParams(hash.slice(1)).get('token') || undefined
  window.history.replaceState(window.history.state, '', `${window.location.pathname}${window.location.search}`)
  if (token) {
    capturedTokens.set(locationKey, token)
    queueMicrotask(() => {
      if (capturedTokens.get(locationKey) === token) capturedTokens.delete(locationKey)
    })
  }
  return token
}
