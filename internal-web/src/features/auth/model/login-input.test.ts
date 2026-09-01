import { describe, expect, it } from 'vitest'
import { loginInputSchema } from './login-input'

describe('login input', () => {
  it('accepts a valid email and nonblank password and normalizes surrounding email whitespace', () => {
    expect(loginInputSchema.parse({ email: ' staff@meridian.local ', password: 'secret' })).toEqual({
      email: 'staff@meridian.local',
      password: 'secret',
    })
  })

  it.each([
    [{ email: 'invalid', password: 'secret' }, 'email'],
    [{ email: 'staff@meridian.local', password: '   ' }, 'password'],
  ])('rejects invalid browser input for %s', (input, field) => {
    const result = loginInputSchema.safeParse(input)
    expect(result.success).toBe(false)
    if (!result.success) expect(result.error.issues[0]?.path[0]).toBe(field)
  })
})
