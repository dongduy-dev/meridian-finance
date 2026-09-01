let accessToken: string | undefined

export function getAccessToken(): string | undefined {
  return accessToken
}

export function setAccessToken(token: string): void {
  accessToken = token
}

export function clearAccessToken(): void {
  accessToken = undefined
}
