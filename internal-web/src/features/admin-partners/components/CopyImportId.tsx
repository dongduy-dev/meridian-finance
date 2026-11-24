import { Check, Copy } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'

export function CopyImportId({ id }: { id: string }) {
  const [result, setResult] = useState<{ id: string; copied: boolean }>()
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(id)
      setResult({ id, copied: true })
    } catch {
      setResult({ id, copied: false })
    }
  }
  const feedback = result?.id === id ? result.copied ? 'Import ID copied.' : 'Could not copy import ID. Try again.' : ''
  return <span className="inline-flex flex-wrap items-center gap-2">
    <Button type="button" variant="outline" size="sm" onClick={() => void copy()} aria-label="Copy import ID">
      {result?.id === id && result.copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />} Copy import ID
    </Button>
    <span role="status" className="text-xs">{feedback}</span>
  </span>
}
