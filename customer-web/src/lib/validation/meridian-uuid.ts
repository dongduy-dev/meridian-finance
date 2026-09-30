import { z } from 'zod'

// Meridian seed data may use canonical UUID text without RFC version or variant bits.
export const meridianUuidSchema = z.string().regex(
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
)
