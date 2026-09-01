const DEFAULT_API_BASE_URL = '/api/v1'

function normalizeApiBaseUrl(value: string | undefined) {
  const normalized = value?.trim() || DEFAULT_API_BASE_URL
  return normalized.endsWith('/') ? normalized.slice(0, -1) : normalized
}

export const environment = Object.freeze({
  apiBaseUrl: normalizeApiBaseUrl(import.meta.env.VITE_API_BASE_URL),
})
