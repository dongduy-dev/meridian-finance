import { Check, Copy } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'

export function RequestCorrelation({ requestId }: { requestId: string }) {
  const [copied, setCopied] = useState(false)

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(requestId)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div className="mt-3 flex flex-wrap items-center gap-2 text-xs">
      <span className="break-all">Support reference: {requestId}</span>
      <Button type="button" variant="ghost" size="sm" className="min-h-9 h-9" onClick={() => void copy()}>
        {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
        {copied ? 'Copied' : 'Copy'}
      </Button>
    </div>
  )
}
