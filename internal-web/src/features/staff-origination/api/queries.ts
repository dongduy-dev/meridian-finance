import { queryOptions } from '@tanstack/react-query'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { getCustomer, getIntake, listBankAccounts, listEvidence, listOpenIntakes } from './staff-origination-api'

export const originationKeys = {
  all: ['staff-origination'] as const,
  list: () => ['staff-origination', 'list'] as const,
  case: (id: string) => ['staff-origination', 'case', id] as const,
  customer: (id: string) => ['staff-origination', 'customer', id] as const,
  banks: (id: string) => ['staff-origination', 'banks', id] as const,
  evidence: (id: string) => ['staff-origination', 'evidence', id] as const,
}
export const intakeListQuery = (manager: AuthSessionManager, enabled: boolean) => queryOptions({ queryKey: originationKeys.list(), queryFn: () => listOpenIntakes(manager), enabled })
export const intakeCaseQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({ queryKey: originationKeys.case(id), queryFn: () => getIntake(manager, id), enabled })
export const customerQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({ queryKey: originationKeys.customer(id), queryFn: () => getCustomer(manager, id), enabled })
export const banksQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({ queryKey: originationKeys.banks(id), queryFn: () => listBankAccounts(manager, id), enabled })
export const evidenceQuery = (manager: AuthSessionManager, id: string, enabled: boolean) => queryOptions({ queryKey: originationKeys.evidence(id), queryFn: () => listEvidence(manager, id), enabled })
