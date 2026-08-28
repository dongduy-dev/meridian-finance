import { ApiError } from '@/lib/api'
import { queryClient } from '@/app/providers/query-client'
import {
  clearAccessCredential,
  getCurrentAccessToken,
  getUsableAccessToken,
  setAccessCredential,
} from '@/lib/auth/access-credential'

import { authApi, type AuthApi, type AuthResponse, type LoginInput, type RegistrationInput } from './auth-api'

export interface CustomerActor {
  userId: string
  email: string
  userType: 'CUSTOMER'
  customerId: string
  roles: string[]
  permissions: string[]
  expiresAt: string
}

export interface SessionCheckError {
  requestId?: string
  retryAfter?: string
  rateLimited: boolean
}

export type AuthState =
  | { status: 'checking'; error?: SessionCheckError }
  | { status: 'authenticated'; actor: CustomerActor }
  | { status: 'anonymous' }

type Listener = () => void

const SESSION_ERROR_CODES = new Set([
  'AUTHENTICATION_REQUIRED',
  'TOKEN_EXPIRED',
  'INVALID_TOKEN',
])

export class CustomerSessionRequiredError extends Error {
  constructor() {
    super('Customer authentication is required for Customer Web.')
    this.name = 'CustomerSessionRequiredError'
  }
}

function isDefinitiveAnonymous(error: unknown) {
  return (
    error instanceof ApiError &&
    error.status === 401 &&
    error.errorCode === 'INVALID_REFRESH_TOKEN'
  )
}

function isSessionRejection(error: unknown) {
  return (
    error instanceof ApiError &&
    error.status === 401 &&
    SESSION_ERROR_CODES.has(error.errorCode)
  )
}

function toSessionCheckError(error: unknown): SessionCheckError {
  return {
    requestId: error instanceof ApiError ? error.requestId : undefined,
    retryAfter: error instanceof ApiError ? error.retryAfter : undefined,
    rateLimited:
      error instanceof ApiError &&
      error.status === 429 &&
      error.errorCode === 'RATE_LIMIT_EXCEEDED',
  }
}

export class AuthSessionManager {
  private state: AuthState = { status: 'checking' }
  private readonly listeners = new Set<Listener>()
  private bootstrapPromise?: Promise<void>
  private refreshPromise?: Promise<CustomerActor>
  private readonly confirmationPromises = new Map<string, Promise<void>>()
  private readonly api: AuthApi
  private readonly clearPrivateQueryData: () => void

  constructor(
    api: AuthApi,
    clearPrivateQueryData: () => void,
  ) {
    this.api = api
    this.clearPrivateQueryData = clearPrivateQueryData
  }

  readonly getSnapshot = () => this.state

  readonly subscribe = (listener: Listener) => {
    this.listeners.add(listener)
    return () => this.listeners.delete(listener)
  }

  bootstrap() {
    if (!this.bootstrapPromise) {
      this.bootstrapPromise = this.runBootstrap()
    }
    return this.bootstrapPromise
  }

  retryBootstrap() {
    this.bootstrapPromise = undefined
    this.setState({ status: 'checking' })
    return this.bootstrap()
  }

  async login(input: LoginInput) {
    return this.acceptCustomerSession(await this.api.login(input))
  }

  async register(input: RegistrationInput) {
    await this.api.register(input)
  }

  async requestEmailVerification(email: string) {
    await this.api.requestEmailVerification(email)
  }

  confirmEmailVerificationOnce(flowKey: string, token: string) {
    const existing = this.confirmationPromises.get(flowKey)
    if (existing) {
      return existing
    }

    const confirmation = this.api.confirmEmailVerification(token)
    this.confirmationPromises.set(flowKey, confirmation)
    return confirmation
  }

  async requestPasswordReset(email: string) {
    await this.api.requestPasswordReset(email)
  }

  async confirmPasswordReset(token: string, newPassword: string) {
    await this.api.confirmPasswordReset(token, newPassword)
    this.clearLocalSession()
  }

  async logout() {
    try {
      await this.api.logout(getCurrentAccessToken())
    } finally {
      this.clearLocalSession()
    }
  }

  async requestProtected<T>(operation: (accessToken: string) => Promise<T>) {
    let accessToken = getUsableAccessToken()
    if (!accessToken) {
      try {
        await this.refreshCustomerSession()
      } catch (error) {
        this.clearLocalSession()
        throw error
      }
      accessToken = getUsableAccessToken()
    }

    if (!accessToken) {
      this.clearLocalSession()
      throw new CustomerSessionRequiredError()
    }

    try {
      return await operation(accessToken)
    } catch (error) {
      if (!isSessionRejection(error)) {
        throw error
      }
    }

    try {
      await this.refreshCustomerSession()
      accessToken = getUsableAccessToken()
      if (!accessToken) {
        throw new CustomerSessionRequiredError()
      }
    } catch (error) {
      this.clearLocalSession()
      throw error
    }

    try {
      return await operation(accessToken)
    } catch (error) {
      if (isSessionRejection(error)) {
        this.clearLocalSession()
      }
      throw error
    }
  }

  private async runBootstrap() {
    try {
      await this.refreshCustomerSession()
    } catch (error) {
      if (isDefinitiveAnonymous(error) || error instanceof CustomerSessionRequiredError) {
        this.clearLocalSession()
        return
      }

      this.setState({ status: 'checking', error: toSessionCheckError(error) })
    }
  }

  private refreshCustomerSession() {
    if (!this.refreshPromise) {
      this.refreshPromise = this.api
        .refresh()
        .then((response) => this.acceptCustomerSession(response))
        .finally(() => {
          this.refreshPromise = undefined
        })
    }

    return this.refreshPromise
  }

  private async acceptCustomerSession(response: AuthResponse) {
    if (response.userType !== 'CUSTOMER' || response.customerId === null) {
      await this.bestEffortLogout(response.accessToken)
      this.clearLocalSession()
      throw new CustomerSessionRequiredError()
    }

    setAccessCredential(response.accessToken, response.expiresAt)
    const actor: CustomerActor = {
      userId: response.userId,
      email: response.email,
      userType: 'CUSTOMER',
      customerId: response.customerId,
      roles: response.roles,
      permissions: response.permissions,
      expiresAt: response.expiresAt,
    }
    this.setState({ status: 'authenticated', actor })
    return actor
  }

  private async bestEffortLogout(accessToken: string) {
    try {
      await this.api.logout(accessToken)
    } catch {
      // Local rejection still prevents a non-Customer principal entering Customer Web.
    }
  }

  private clearLocalSession() {
    clearAccessCredential()
    this.clearPrivateQueryData()
    this.setState({ status: 'anonymous' })
  }

  private setState(state: AuthState) {
    this.state = state
    this.listeners.forEach((listener) => listener())
  }
}

export const authSessionManager = new AuthSessionManager(authApi, () => queryClient.clear())
