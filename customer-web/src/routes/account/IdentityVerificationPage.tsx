import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useOwnCustomerQuery, accountKeys } from '@/features/account/account-queries'
import { AccountNavigation } from '@/features/account/components/AccountNavigation'
import { AccountErrorFeedback } from '@/features/account/components/AccountFeedback'
import { createIdentityApi, identityReasons } from '@/features/account/identity-api'
import { useAuth } from '@/features/auth/auth-context'

export function IdentityVerificationPage() {
  const { state } = useAuth()
  if (state.status !== 'authenticated' || !state.actor.permissions.includes('customer:identity:read:own'))
    return <p role="alert">Own identity verification access is required.</p>
  return <OwnIdentityWorkspace key={state.actor.userId} />
}
function OwnIdentityWorkspace() {
  const { manager, state } = useAuth()
  const canUpload = state.status === 'authenticated' && state.actor.permissions.includes('customer:identity:write:own')
  const api = useMemo(() => createIdentityApi(manager), [manager])
  const client = useQueryClient()
  const customer = useOwnCustomerQuery()
  const history = useQuery({ queryKey: ['account', 'identity-verifications'], queryFn: () => api.history() })
  const contentGeneration = useRef(0)
  const downloadUrls = useRef(new Set<string>())
  useEffect(() => () => {
    contentGeneration.current++
    for (const url of downloadUrls.current) URL.revokeObjectURL(url)
    downloadUrls.current.clear()
  }, [])
  const [busy, setBusy] = useState(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [message, setMessage] = useState<string>()
  const [uncertain, setUncertain] = useState(false)
  const current = history.data?.[0]
  const verified = customer.data?.verificationStatus === 'VERIFIED'
  const supported = !current || (['PENDING_REVIEW', 'VERIFIED', 'REJECTED', 'SUPERSEDED'].includes(current.status) && ['CUSTOMER_DIGITAL', 'STAFF_ASSISTED_INTAKE'].includes(current.source) && current.method === 'MANUAL_STAFF_DOCUMENT_REVIEW')
  const refresh = async () => { const result = await history.refetch(); await client.invalidateQueries({ queryKey: accountKeys.customer() }); return !result.isError }
  const upload = async (form: HTMLFormElement) => {
    const file = selectedFile
    if (!file || !file.size || busy) return
    if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type) || file.size > 10 * 1024 * 1024) {
      setMessage('Choose a PDF, JPEG, or PNG file up to 10 MiB.'); return
    }
    setBusy(true); setMessage(undefined)
    try {
      await api.upload(file, crypto.randomUUID(), history.data?.find(v => v.source === 'CUSTOMER_DIGITAL')?.evidence?.versionId)
      form.reset(); setSelectedFile(null); setMessage('Identity evidence submitted. A Staff review is required before your identity is verified.')
    } catch {
      setUncertain(true); setMessage('Submission was not confirmed. Refresh your verification history before sending another file.')
    } finally { await refresh(); setBusy(false) }
  }
  const download = async (id: string, filename: string) => {
    const generation = contentGeneration.current
    try {
      const blob = await api.content(id)
      if (generation !== contentGeneration.current) return
      const url = URL.createObjectURL(blob); downloadUrls.current.add(url)
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click()
      setTimeout(() => { URL.revokeObjectURL(url); downloadUrls.current.delete(url) }, 1000)
    } catch { if (generation === contentGeneration.current) setMessage('Evidence could not be downloaded. Refresh and try again.') }
  }
  return <div className="space-y-6">
    <PageHeader eyebrow="Your account" title="Identity verification" description="Submit one identity document for manual Staff review. Verification is required before applying for a Meridian loan." />
    <AccountNavigation />
    {history.isPending || customer.isPending ? <p role="status">Loading identity verification…</p> : null}
    {history.isError || customer.isError ? <><AccountErrorFeedback error={history.error ?? customer.error} title="Identity verification could not be loaded" /><Button onClick={() => void refresh()}>Try again</Button></> : null}
    {message ? <p role="status">{message}</p> : null}
    {history.data && customer.data ? <Card><CardHeader><CardTitle>{verified ? 'Identity verified' : current?.status === 'PENDING_REVIEW' ? 'Pending review' : current?.status === 'REJECTED' ? 'Verification could not be completed' : 'Upload identity document'}</CardTitle></CardHeader><CardContent className="space-y-4">
      <p className="text-sm text-muted-foreground">Profile completion and document upload do not verify your identity. Manual review does not indicate credit approval.</p>
      {current?.rejectionReason ? <p>{identityReasons[current.rejectionReason] ?? 'Review could not be completed. Supply acceptable identity evidence.'}</p> : null}
      {customer.data.profileCompletionStatus !== 'COMPLETE' ? <Button asChild variant="secondary"><Link to="/account/profile">Complete profile first</Link></Button> : null}
      {!supported ? <p role="alert">Verification state is unavailable. Refresh before submitting evidence.</p> : null}
      {canUpload && supported && !verified && customer.data.status === 'ACTIVE' && customer.data.profileCompletionStatus === 'COMPLETE' ? <form noValidate className="space-y-3" onSubmit={event => { event.preventDefault(); void upload(event.currentTarget) }}>
        <label className="grid gap-2 font-medium">Identity document (PDF, JPEG, PNG; up to 10 MiB)<input name="file" type="file" onChange={event => setSelectedFile(event.target.files?.[0] ?? null)} accept="application/pdf,image/jpeg,image/png" required disabled={busy || uncertain} /></label>
        <Button type="submit" disabled={busy || uncertain || !selectedFile}>{busy ? 'Submitting…' : current ? 'Submit replacement evidence' : 'Submit identity evidence'}</Button>
      </form> : null}
      <Button variant="secondary" disabled={busy} onClick={() => void refresh().then(confirmed => { if (confirmed) setUncertain(false) })}>Refresh verification</Button>
    </CardContent></Card> : null}
    {history.data?.length ? <Card><CardHeader><CardTitle>Verification history</CardTitle></CardHeader><CardContent className="space-y-3">{history.data.map(v => <div className="rounded-md border p-3" key={v.verificationId}>
      <p>Attempt {v.sequence} · {({ PENDING_REVIEW: 'Pending review', VERIFIED: 'Verified', REJECTED: 'Rejected', SUPERSEDED: 'Replaced evidence' } as Record<string, string>)[v.status] ?? 'Status unavailable'}</p>
      {v.evidence ? <><p className="text-sm break-words">{v.evidence.filename} · Version {v.evidence.versionNumber}</p><Button variant="secondary" size="sm" onClick={() => void download(v.verificationId, v.evidence!.filename)}>Download evidence</Button></> : null}
    </div>)}</CardContent></Card> : null}
  </div>
}
