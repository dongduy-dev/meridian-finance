import { useCallback, useState, type SetStateAction } from 'react'
import { useLocation } from 'react-router-dom'
import { useAuth } from '@/features/auth/model/auth-context'

export function useSensitiveValue<T>() {
  const { pathname } = useLocation()
  const { state } = useAuth()
  const scope = `${pathname}:${state.epoch}`
  const [scoped, setScoped] = useState<{ scope: string; value?: T }>({ scope })
  const value = scoped.scope === scope ? scoped.value : undefined
  const setValue = useCallback((next: SetStateAction<T | undefined>) => {
    setScoped((current) => {
      const currentValue = current.scope === scope ? current.value : undefined
      return { scope, value: typeof next === 'function' ? (next as (previous?: T) => T | undefined)(currentValue) : next }
    })
  }, [scope])
  return [value, setValue] as const
}
