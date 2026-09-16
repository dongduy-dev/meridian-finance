import { describe, expect, it } from 'vitest'
import type { StaffActor } from '@/features/auth/model/access-control'
import { ADMIN_HOME_ROUTE, ADMIN_PARTNER_DETAIL_ROUTE, ADMIN_PARTNERS_ROUTE, ADMIN_PRODUCTS_ROUTE, ADMIN_ROUTES, canAccessAdminRoute, permittedAdminRoutes } from './admin-route-metadata'

const actor = (permissions: readonly string[], roles: readonly string[] = []): StaffActor => ({
  userId: 'staff-1',
  email: 'staff@meridian.local',
  roles,
  permissions,
})

describe('Admin route metadata', () => {
  it('defines the executable administration home, Partner, and Loan Product routes', () => {
    expect(ADMIN_ROUTES).toEqual([
      ADMIN_HOME_ROUTE,
      ADMIN_PARTNERS_ROUTE,
      ADMIN_PARTNER_DETAIL_ROUTE,
      ADMIN_PRODUCTS_ROUTE,
    ])
    expect(ADMIN_HOME_ROUTE.path).toBe('/admin')
    expect(ADMIN_PARTNERS_ROUTE.requiredPermissions).toEqual(['partner:read'])
    expect(ADMIN_PARTNER_DETAIL_ROUTE.navigation).toBe(false)
    expect(ADMIN_PRODUCTS_ROUTE.requiredPermissions).toEqual(['loan:product:manage'])
  })

  it('uses exact capability membership for route access', () => {
    expect(canAccessAdminRoute(actor(['partner:read']), ADMIN_HOME_ROUTE)).toBe(true)
    expect(canAccessAdminRoute(actor(['partner:read:all']), ADMIN_HOME_ROUTE)).toBe(false)
    expect(canAccessAdminRoute(actor([], ['BACK_OFFICE_ADMIN']), ADMIN_HOME_ROUTE)).toBe(false)
    expect(permittedAdminRoutes(actor(['audit:read']))).toEqual([])
    expect(permittedAdminRoutes(actor(['partner:read']))).toEqual([ADMIN_HOME_ROUTE, ADMIN_PARTNERS_ROUTE])
    expect(permittedAdminRoutes(actor(['partner:manage']))).toEqual([ADMIN_HOME_ROUTE])
    expect(permittedAdminRoutes(actor(['loan:product:manage']))).toEqual([ADMIN_HOME_ROUTE, ADMIN_PRODUCTS_ROUTE])
  })
})
