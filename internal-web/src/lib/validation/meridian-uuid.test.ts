import { describe, expect, it } from 'vitest'
import { meridianUuidSchema } from './meridian-uuid'

describe('Meridian UUID schema', () => {
  it.each([
    '00000000-0000-0000-0000-000000000304',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  ])('accepts canonical UUID text %s', (value) => {
    expect(meridianUuidSchema.parse(value)).toBe(value)
  })

  it.each([
    'not-a-uuid',
    '123',
    '00000000-0000-0000',
  ])('rejects malformed identifier %s', (value) => {
    expect(meridianUuidSchema.safeParse(value).success).toBe(false)
  })
})
