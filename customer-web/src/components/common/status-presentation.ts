import { CircleHelp, type LucideIcon } from 'lucide-react'

export type StatusTone = 'neutral' | 'information' | 'warning' | 'success' | 'danger'

export interface StatusPresentation {
  label: string
  tone: StatusTone
  icon?: LucideIcon
}

export const unavailableStatus: StatusPresentation = {
  label: 'Status unavailable',
  tone: 'neutral',
  icon: CircleHelp,
}
