type FragmentFlow = 'email-verification' | 'password-reset'

const capturedTokens = new Map<string, string>()

export interface ActivationTokens {
  verificationToken?: string
  setupToken?: string
}

const capturedActivations = new Map<string, ActivationTokens>()

export function captureActivationTokens(locationKey: string): ActivationTokens {
  const hash = window.location.hash
  if (!hash) return capturedActivations.get(locationKey) ?? {}
  const parameters = new URLSearchParams(hash.slice(1))
  window.history.replaceState(window.history.state, '', `${window.location.pathname}${window.location.search}`)
  const tokens = {
    verificationToken: parameters.get('verificationToken') || undefined,
    setupToken: parameters.get('setupToken') || undefined,
  }
  // Share only through the synchronous Strict Mode initialization, never across reloads.
  capturedActivations.set(locationKey, tokens)
  queueMicrotask(() => capturedActivations.delete(locationKey))
  return tokens
}

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
