import type { QueryClient } from '@tanstack/react-query'
import { apiRequest, ApiError } from '@/lib/api'
import { clearAccessToken, getAccessToken, setAccessToken } from './access-credential'
import type { StaffActor } from './access-control'
import type { AuthResponse } from '../api/auth-api'
import * as authApi from '../api/auth-api'

export type AnonymousReason = 'INTERNAL_ACCESS_REQUIRED' | 'SESSION_EXPIRED'
export type SessionState =
  | { status: 'checking'; epoch: number; error?: string }
  | { status: 'anonymous'; epoch: number; reason?: AnonymousReason }
  | { status: 'authenticated'; epoch: number; actor: StaffActor }

export class InternalAccessRequiredError extends Error {
  constructor() {
    super('Use a Meridian staff account to access the internal workspace.')
    this.name = 'InternalAccessRequiredError'
  }
}

const SESSION_ERROR_CODES = new Set(['AUTHENTICATION_REQUIRED', 'TOKEN_EXPIRED', 'INVALID_TOKEN'])

export class AuthSessionManager {
  private state: SessionState = { status: 'checking', epoch: 0 }
  private refreshPromise?: Promise<AuthResponse>
  private listeners = new Set<() => void>()
  private readonly queryClient: QueryClient

  constructor(queryClient: QueryClient) { this.queryClient = queryClient }

  getSnapshot = (): SessionState => this.state
  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  private publish(state: SessionState): void {
    this.state = state
    this.listeners.forEach((listener) => listener())
  }

  private clearPrivateState(reason?: AnonymousReason): void {
    clearAccessToken()
    this.queryClient.clear()
    this.publish({ status: 'anonymous', epoch: this.state.epoch + 1, reason })
  }

  private async rejectNonStaff(response: AuthResponse): Promise<never> {
    this.clearPrivateState('INTERNAL_ACCESS_REQUIRED')
    try {
      await authApi.logout(response.accessToken)
    } catch {
      // Local clearance remains authoritative even if best-effort cookie revocation fails.
    }
    throw new InternalAccessRequiredError()
  }

  private async accept(response: AuthResponse): Promise<StaffActor> {
    if (response.userType !== 'STAFF' || response.customerId !== null) return this.rejectNonStaff(response)
    const actor: StaffActor = {
      userId: response.userId,
      email: response.email,
      roles: [...response.roles],
      permissions: [...response.permissions],
    }
    const previousActor = this.state.status === 'authenticated' ? this.state.actor.userId : undefined
    if (previousActor !== actor.userId) this.queryClient.clear()
    setAccessToken(response.accessToken)
    this.publish({ status: 'authenticated', actor, epoch: this.state.epoch + 1 })
    return actor
  }

  async login(email: string, password: string): Promise<StaffActor> {
    return this.accept(await authApi.login(email, password))
  }

  async restore(): Promise<void> {
    this.publish({ status: 'checking', epoch: this.state.epoch })
    try {
      await this.refresh()
    } catch (error) {
      if (error instanceof InternalAccessRequiredError) return
      if (error instanceof ApiError && error.status === 401) this.clearPrivateState('SESSION_EXPIRED')
      else this.publish({ status: 'checking', epoch: this.state.epoch, error: 'Session verification is temporarily unavailable.' })
    }
  }

  async refresh(): Promise<AuthResponse> {
    if (!this.refreshPromise) {
      this.refreshPromise = authApi.refresh().then(async (response) => {
        await this.accept(response)
        return response
      }).finally(() => { this.refreshPromise = undefined })
    }
    return this.refreshPromise
  }

  async logout(): Promise<void> {
    const token = getAccessToken()
    try {
      await authApi.logout(token)
    } finally {
      this.clearPrivateState()
    }
  }

  async protectedRequest<T>(path: string, options: RequestInit & { body?: unknown } = {}): Promise<T> {
    if (!getAccessToken()) {
      try {
        await this.refresh()
      } catch (error) {
        this.clearPrivateState('SESSION_EXPIRED')
        throw error
      }
    }
    const execute = () => apiRequest<T>(path, {
      ...options,
      body: options.body,
      headers: { ...Object.fromEntries(new Headers(options.headers)), Authorization: `Bearer ${getAccessToken() ?? ''}` },
    })
    try {
      return await execute()
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401 || !SESSION_ERROR_CODES.has(error.errorCode)) throw error
      try {
        await this.refresh()
        return await execute()
      } catch (retryError) {
        if (retryError instanceof ApiError && retryError.status === 401) this.clearPrivateState('SESSION_EXPIRED')
        throw retryError
      }
    }
  }
}
