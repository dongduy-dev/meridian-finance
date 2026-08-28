import { z } from 'zod'

export const emailSchema = z
  .string()
  .trim()
  .min(1, 'Enter your email address.')
  .max(255, 'Email must be 255 characters or fewer.')
  .email('Enter a valid email address.')

export const displayNameSchema = z
  .string()
  .trim()
  .min(1, 'Enter your display name.')
  .max(150, 'Display name must be 150 characters or fewer.')

export const requiredPasswordSchema = z.string().min(1, 'Enter your password.')

export const newPasswordSchema = z
  .string()
  .min(12, 'Password must contain at least 12 characters.')
  .max(72, 'Password must contain no more than 72 characters.')

export function validateWith(schema: z.ZodType<string>) {
  return (value: string) => {
    const result = schema.safeParse(value)
    return result.success || result.error.issues[0]?.message || 'Check this field.'
  }
}
