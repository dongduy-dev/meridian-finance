import { ApiError } from '../api/ApiError'

/** Safe cross-cutting copy; feature-specific business codes belong to their feature. */
export function customerErrorMessage(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) return fallback
  if (error.errorCode === 'CUSTOMER_DIRECT_ACTION_NOT_ALLOWED') {
    return 'This application is handled with Meridian staff. Contact your Loan Officer to arrange the next step.'
  }
  switch (error.status) {
    case 401: return 'Log in again to continue.'
    case 403: return 'This action or information is not available for your account.'
    case 404: return 'This information is unavailable. Return to your records and choose an available item.'
    case 409: return 'This request could not be completed. Review the latest details before trying again.'
    case 429: return 'Please wait a moment before trying again.'
    case 503: return 'This service is temporarily unavailable. Please try again later.'
    default: return fallback
  }
}
