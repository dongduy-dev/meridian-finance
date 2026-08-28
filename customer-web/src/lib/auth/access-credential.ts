interface AccessCredential {
  accessToken: string
  expiresAt: number
}
let credential: AccessCredential | undefined

export function setAccessCredential(accessToken: string, expiresAt: string) {
  const parsedExpiry = Date.parse(expiresAt)
  if (!Number.isFinite(parsedExpiry)) {
    throw new TypeError('Authentication response contained an invalid access expiry.')
  }

  credential = { accessToken, expiresAt: parsedExpiry }
}

export function clearAccessCredential() {
  credential = undefined
}

export function getCurrentAccessToken() {
  return credential?.accessToken
}

export function getUsableAccessToken(now = Date.now()) {
  if (!credential || credential.expiresAt <= now) {
    return undefined
  }

  return credential.accessToken
}
