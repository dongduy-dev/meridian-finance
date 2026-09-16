import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { ApiError, NetworkError } from '@/lib/api'
import { formatVnd } from '@/lib/format/presentation'
import { changeProductActivation as submitProductActivation, updateProductLimits } from '../api/admin-products-api'
import {
  updateProductLimitsInputSchema,
  type AdminLoanProduct,
  type UpdateProductLimitsInput,
} from '../api/contracts'
import { adminProductKeys } from '../api/queries'

const knownProductCodes = new Set([
  'SALARY_ADVANCE',
  'UNSECURED_CONSUMER_LOAN',
  'COLLATERAL_LOAN',
])
const knownProductTypes: Readonly<Record<string, string>> = {
  SALARY_BASED: 'Salary based',
  UNSECURED: 'Unsecured',
  SECURED: 'Secured',
}

type LimitFields = { minAmount: number; maxAmount: number }

function commandMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof NetworkError) return 'The result is unknown because Meridian did not confirm the response. Refresh authoritative product state before retrying the same target state.'
  return 'The product command was not confirmed. Refresh product state before trying again.'
}

export function LoanProductCard({ product, manager }: {
  product: AdminLoanProduct
  manager: AuthSessionManager
}) {
  const queryClient = useQueryClient()
  const form = useForm<LimitFields>({
    defaultValues: { minAmount: product.minAmount, maxAmount: product.maxAmount },
  })
  const [limitError, setLimitError] = useState<string>()
  const [activationError, setActivationError] = useState<string>()
  const [limitPending, setLimitPending] = useState(false)
  const [activationPending, setActivationPending] = useState(false)

  useEffect(() => {
    form.reset({ minAmount: product.minAmount, maxAmount: product.maxAmount })
  }, [form, product.maxAmount, product.minAmount])

  const retainReturnedProduct = async (returned: AdminLoanProduct) => {
    queryClient.setQueryData<AdminLoanProduct[]>(adminProductKeys.list(), (current) =>
      current?.map((item) => item.productCode === returned.productCode ? returned : item) ?? [returned])
    await queryClient.invalidateQueries({ queryKey: adminProductKeys.list() }).catch(() => undefined)
  }

  const submitLimits = form.handleSubmit(async (raw) => {
    form.clearErrors()
    const parsed = updateProductLimitsInputSchema.safeParse(raw)
    if (!parsed.success) {
      for (const issue of parsed.error.issues) {
        const field = issue.path[0]
        if (field === 'minAmount' || field === 'maxAmount') form.setError(field, { message: issue.message })
      }
      setLimitError('Review the amount limits and try again.')
      return
    }
    setLimitPending(true)
    setLimitError(undefined)
    try {
      const returned = await updateProductLimits(manager, product.productCode, parsed.data as UpdateProductLimitsInput)
      form.reset({ minAmount: returned.minAmount, maxAmount: returned.maxAmount })
      await retainReturnedProduct(returned)
    } catch (error) {
      setLimitError(commandMessage(error))
    } finally {
      setLimitPending(false)
    }
  })

  const changeActivation = async () => {
    const target = !product.active
    setActivationPending(true)
    setActivationError(undefined)
    try {
      const returned = await submitProductActivation(manager, product.productCode, target)
      await retainReturnedProduct(returned)
    } catch (error) {
      setActivationError(commandMessage(error))
    } finally {
      setActivationPending(false)
    }
  }

  const name = knownProductCodes.has(product.productCode) ? product.name : 'Unknown product'
  const type = knownProductTypes[product.productType] ?? 'Unknown type'
  const minError = form.formState.errors.minAmount?.message
  const maxError = form.formState.errors.maxAmount?.message

  return <Card>
    <CardHeader>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <CardTitle>{name}</CardTitle>
          <p className="mt-1 break-all font-mono text-xs text-muted-foreground">{product.productCode}</p>
        </div>
        <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ${product.active ? 'bg-success-subtle text-success' : 'bg-muted text-muted-foreground'}`}>
          {product.active ? 'Active' : 'Inactive'}
        </span>
      </div>
    </CardHeader>
    <CardContent className="space-y-6">
      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div><dt className="font-medium text-muted-foreground">Product type</dt><dd>{type} <span className="font-mono text-xs">({product.productType})</span></dd></div>
        <div><dt className="font-medium text-muted-foreground">Configured range</dt><dd>{formatVnd(product.minAmount)} – {formatVnd(product.maxAmount)}</dd></div>
      </dl>
      {product.description ? <p className="text-sm leading-6 text-muted-foreground">{product.description}</p> : null}

      <form className="space-y-4 rounded-md border p-4" onSubmit={submitLimits}>
        <div><h3 className="font-semibold">Amount limits</h3><p className="text-sm text-muted-foreground">Set the allowed range for future applications. Product-specific policy remains backend-owned.</p></div>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="space-y-1 text-sm font-medium">Minimum amount
            <Input type="number" min="0" step="0.01" aria-invalid={Boolean(minError)} {...form.register('minAmount', { required: 'Minimum amount is required.', valueAsNumber: true, min: { value: 0, message: 'Minimum amount must be nonnegative.' } })} />
            {minError ? <span className="block text-xs text-danger">{minError}</span> : null}
          </label>
          <label className="space-y-1 text-sm font-medium">Maximum amount
            <Input type="number" min="0" step="0.01" aria-invalid={Boolean(maxError)} {...form.register('maxAmount', { required: 'Maximum amount is required.', valueAsNumber: true, min: { value: 0, message: 'Maximum amount must be nonnegative.' } })} />
            {maxError ? <span className="block text-xs text-danger">{maxError}</span> : null}
          </label>
        </div>
        <Button type="submit" disabled={limitPending || !form.formState.isDirty}>{limitPending ? 'Saving limits…' : 'Save amount limits'}</Button>
        {limitError ? <Alert variant="destructive"><AlertTitle>Limits were not confirmed</AlertTitle><AlertDescription>{limitError}</AlertDescription></Alert> : null}
      </form>

      <div className="space-y-3 rounded-md border p-4">
        <div><h3 className="font-semibold">Product availability</h3><p className="text-sm text-muted-foreground">{product.active ? 'Deactivation removes this product from Customer discovery and blocks future submission. Historical lending records are unchanged.' : 'Activation makes this product available for Customer discovery and future submission under current backend policy.'}</p></div>
        <Button type="button" variant={product.active ? 'destructive' : 'default'} disabled={activationPending} onClick={() => void changeActivation()}>
          {activationPending ? 'Waiting for confirmation…' : product.active ? 'Deactivate product' : 'Activate product'}
        </Button>
        {activationError ? <Alert variant="destructive"><AlertTitle>Activation state was not confirmed</AlertTitle><AlertDescription>{activationError}</AlertDescription></Alert> : null}
      </div>
    </CardContent>
  </Card>
}
