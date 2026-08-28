import { apiClient, type ApiClient, type ApiRequestOptions } from './client'

export interface ProtectedRequestCoordinator {
  requestProtected<T>(operation: (accessToken: string) => Promise<T>): Promise<T>
}
export function createProtectedApiClient(
  coordinator: ProtectedRequestCoordinator,
  client: ApiClient = apiClient,
): ApiClient {
  return {
    request<TResponse>(path: string, options: ApiRequestOptions = {}) {
      return coordinator.requestProtected((accessToken) => {
        const headers = new Headers(options.headers)
        headers.set('Authorization', `Bearer ${accessToken}`)
        return client.request<TResponse>(path, { ...options, headers })
      })
    },
  }
}
