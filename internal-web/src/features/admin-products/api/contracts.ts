import { z } from 'zod'

const rawValue = z.string().trim().min(1)
const money = z.number().finite().nonnegative()

export const adminLoanProductSchema = z.object({
  productCode: rawValue,
  productType: rawValue,
  name: z.string().trim().min(1),
  description: z.string().nullable(),
  active: z.boolean(),
  minAmount: money,
  maxAmount: money,
})

export const updateProductLimitsInputSchema = z.object({
  minAmount: money,
  maxAmount: money,
}).refine((value) => value.maxAmount >= value.minAmount, {
  message: 'Maximum amount must be greater than or equal to minimum amount.',
  path: ['maxAmount'],
})

export type AdminLoanProduct = z.infer<typeof adminLoanProductSchema>
export type UpdateProductLimitsInput = z.infer<typeof updateProductLimitsInputSchema>
