function readSetupToken(): string | undefined {
  return new URLSearchParams(window.location.hash.slice(1)).get('token') || undefined
}

function captureSetupToken(): string | undefined {
  const hash = window.location.hash
  if (!hash) return undefined
  const token = readSetupToken()
  window.history.replaceState(window.history.state, '', `${window.location.pathname}${window.location.search}`)
  return token
}

export class StaffSetupAttempt {
  private token?: string
  private initialized = false
  private mounted = false
  private generation = 0
  private readonly listeners = new Set<() => void>()

  subscribe = (listener: () => void) => {
    this.listeners.add(listener)
    return () => { this.listeners.delete(listener) }
  }
  // The first render can show the form without mutating the browser or retaining the secret.
  hasToken = () => Boolean(this.initialized ? this.token : readSetupToken())
  isMounted = () => this.mounted

  mount() {
    this.mounted = true
    const generation = ++this.generation
    // Capture only on a committed mount, before paint. Discarded renders cannot lose the fragment.
    if (!this.initialized) {
      this.initialized = true
      this.token = captureSetupToken()
      this.notify()
    }
    return () => {
      this.mounted = false
      // Synchronous Strict Mode effect replay keeps this attempt; a real departure clears it.
      queueMicrotask(() => {
        if (this.generation === generation) this.takeToken()
      })
    }
  }

  takeToken() {
    const token = this.token
    this.token = undefined
    this.notify()
    return token
  }

  private notify() { this.listeners.forEach((listener) => listener()) }
}
