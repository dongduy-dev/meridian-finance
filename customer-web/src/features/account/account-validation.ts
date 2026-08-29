import { z } from 'zod'

function requiredText(label: string, maximum: number) {
  return z
    .string()
    .trim()
    .min(1, `Enter ${label}.`)
    .max(maximum, `${label[0]?.toUpperCase()}${label.slice(1)} must be ${maximum} characters or fewer.`)
}

export const profileFieldSchemas = {
  fullName: requiredText('your full name', 200),
  identityReference: requiredText('your identity reference', 100),
  phoneNumber: requiredText('your phone number', 50),
  residentialAddress: requiredText('your residential address', 500),
  employmentStatus: requiredText('your employment status', 50),
  employerName: z.string().trim().max(200, 'Employer name must be 200 characters or fewer.'),
}

export const bankAccountFieldSchemas = {
  bankCode: requiredText('the bank code', 50),
  bankNameSnapshot: requiredText('the bank name', 150),
  accountHolderName: requiredText('the account holder name', 200),
  accountNumber: requiredText('the account number', 100).refine(
    (value) => value.replace(/[\s-]+/g, '').length >= 6,
    'Account number must contain at least 6 characters, excluding spaces and hyphens.',
  ),
}

export function validateWith(schema: z.ZodType<string>) {
  return (value: string) => {
    const result = schema.safeParse(value)
    return result.success || result.error.issues[0]?.message || 'Check this field.'
  }
}
