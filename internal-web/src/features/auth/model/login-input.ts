import { z } from 'zod'

export const loginInputSchema = z.object({
  email: z.string().trim().email('Enter a valid email address.'),
  password: z.string().refine((value) => value.trim().length > 0, 'Enter your password.'),
})

export type LoginInput = z.infer<typeof loginInputSchema>
