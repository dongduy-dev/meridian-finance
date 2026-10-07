import { ApiError } from '@/lib/api'
import { customerErrorMessage } from '@/lib/errors/customer-error-message'

const messages: Record<string, string> = {
  CUSTOMER_NOT_ACTIVE: 'Your account is not active. Contact Meridian support for help.',
  PROFILE_INCOMPLETE: 'Complete your profile before continuing.',
  VERIFIED_IDENTITY_CHANGE_NOT_ALLOWED: 'Your verified name and identity reference cannot be changed here. You can still update your contact and employment details.',
  IDENTITY_REFERENCE_IMMUTABLE: 'Your identity reference cannot be changed here. Review your identity verification status or contact Meridian support.',
  IDENTITY_REFERENCE_ALREADY_IN_USE: 'This identity reference cannot be saved. Check it against your identity document or contact Meridian support.',
  DUPLICATE_BANK_ACCOUNT: 'This bank account is already saved. Choose it from your saved accounts.',
  BANK_ACCOUNT_UPDATE_NOT_ALLOWED: 'This bank account cannot be updated right now. Review your saved accounts and primary account before trying again.',
  BANK_ACCOUNT_NOT_FOUND: 'This bank account is unavailable. Refresh your saved accounts.',
  VALIDATION_FAILED: 'Check the details you entered before trying again.',
}

export function accountErrorMessage(error: unknown) {
  const fallback = 'We could not confirm the result. Check your connection and refresh the page before trying again.'
  return error instanceof ApiError && Object.hasOwn(messages, error.errorCode)
    ? messages[error.errorCode]!
    : customerErrorMessage(error, fallback)
}
