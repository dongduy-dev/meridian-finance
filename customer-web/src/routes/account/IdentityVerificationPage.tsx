import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { useOwnCustomerQuery, accountKeys } from '@/features/account/account-queries'
import { AccountNavigation } from '@/features/account/components/AccountNavigation'
import { AccountErrorFeedback } from '@/features/account/components/AccountFeedback'
import { createIdentityApi, identityReasons } from '@/features/account/identity-api'
import { useAuth } from '@/features/auth/auth-context'

export function IdentityVerificationPage() {
  const { state } = useAuth()
  if (state.status !== 'authenticated' || !state.actor.permissions.includes('customer:identity:read:own'))
    return <div className="min-w-0 space-y-[var(--section-transactional)]">
      <PageHeader eyebrow="Your account" title="Identity verification" />
      <AccountNavigation />
      <p role="alert" className="max-w-[70ch] border-t border-border pt-6">Identity verification is not available for your account. Contact Meridian support for help.</p>
    </div>
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
      form.reset(); setSelectedFile(null); setMessage('Identity document submitted. Meridian will review it before confirming your identity.')
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
  return <div className="min-w-0 space-y-[var(--section-transactional)] [overflow-wrap:anywhere]">
    <PageHeader eyebrow="Your account" title="Identity verification" description="We need to confirm your identity before you apply for a loan. Complete your profile, then upload one clear identity document for review." />
    <AccountNavigation />
    {customer.isSuccess && customer.fetchStatus === 'idle' && customer.data.status === 'ACTIVE'
      && state.status === 'authenticated' && state.actor.permissions.includes('customer:profile:write:own')
      && customer.data.profileCompletionStatus === 'COMPLETE'
      && ['UNVERIFIED', 'REJECTED'].includes(customer.data.verificationStatus)
      ? <Button asChild variant="secondary"><Link to="/account/profile">Correct identity reference</Link></Button> : null}
    {history.isPending || customer.isPending ? <div role="status" aria-label="Loading identity verification" className="space-y-6">
      <Skeleton className="h-72 w-full max-w-[var(--width-flow)]" />
      <Skeleton className="h-40 w-full" />
    </div> : null}
    {history.isError || customer.isError ? <div className="space-y-4"><AccountErrorFeedback error={history.error ?? customer.error} title="Identity verification could not be loaded" /><Button variant="secondary" onClick={() => void refresh()}>Try again</Button></div> : null}
    {message ? <p role="status" className="max-w-[70ch] border-l-2 border-primary bg-muted p-4 text-base leading-6">{message}</p> : null}
    {history.data && customer.data ? <Card className="max-w-[var(--width-flow)] border-0"><CardHeader><CardTitle>{verified ? 'Identity verified' : current?.status === 'PENDING_REVIEW' ? 'Pending review' : current?.status === 'REJECTED' ? 'Verification could not be completed' : 'Upload identity document'}</CardTitle></CardHeader><CardContent className="space-y-6">
      <p className="text-sm text-muted-foreground">{verified ? 'Your identity has been verified. Loan eligibility and approval are assessed separately.' : current?.status === 'PENDING_REVIEW' ? 'Your document is being reviewed. You do not need to submit it again unless you need to replace it. Identity verification does not guarantee loan approval.' : 'Your identity is verified only after Meridian accepts your document. Identity verification does not guarantee loan approval.'}</p>
      {current?.rejectionReason ? <p className="border-l-2 border-danger bg-danger-subtle p-4 text-danger">{identityReasons[current.rejectionReason] ?? 'Review could not be completed. Supply acceptable identity evidence.'}</p> : null}
      {customer.data.profileCompletionStatus !== 'COMPLETE' ? <Button asChild variant="secondary"><Link to="/account/profile">Complete profile first</Link></Button> : null}
      {!supported ? <p role="alert">Your verification status could not be confirmed. Refresh before submitting a document.</p> : null}
      {canUpload && supported && !verified && customer.data.status === 'ACTIVE' && customer.data.profileCompletionStatus === 'COMPLETE' ? <form noValidate className="space-y-6 border-t border-border pt-6" onSubmit={event => { event.preventDefault(); void upload(event.currentTarget) }}>
        <label className="grid min-w-0 gap-2 text-sm leading-5 font-semibold">Identity document (PDF, JPEG, PNG; up to 10 MiB)<Input className="block py-3 file:mr-3 file:rounded-sm file:border-0 file:bg-selected file:px-3 file:py-2 file:text-sm file:font-semibold file:text-foreground" name="file" type="file" onChange={event => setSelectedFile(event.target.files?.[0] ?? null)} accept="application/pdf,image/jpeg,image/png" required disabled={busy || uncertain} /></label>
        <Button className="w-full sm:w-auto" size="lg" type="submit" disabled={busy || uncertain || !selectedFile}>{busy ? 'Submitting…' : current ? 'Submit replacement document' : 'Submit identity document'}</Button>
      </form> : null}
      <Button variant="secondary" disabled={busy} onClick={() => void refresh().then(confirmed => { if (confirmed) setUncertain(false) })}>Refresh verification</Button>
    </CardContent></Card> : null}
    {history.data?.length ? <section aria-labelledby="verification-history-heading" className="min-w-0 space-y-6">
      <h2 id="verification-history-heading" className="type-section">Verification history</h2>
      <ul className="divide-y divide-border border-y border-border">{history.data.map(v => <li className="flex min-w-0 flex-col items-start gap-6 py-6 md:flex-row md:justify-between" key={v.verificationId}>
        <div className="min-w-0 space-y-2">
          <h3 className="text-base leading-6 font-semibold">Attempt {v.sequence} · {({ PENDING_REVIEW: 'Pending review', VERIFIED: 'Verified', REJECTED: 'Not verified', SUPERSEDED: 'Replaced evidence' } as Record<string, string>)[v.status] ?? 'Status unavailable'}</h3>
          {v.evidence ? <p className="text-base leading-6 text-muted-foreground">{v.evidence.filename} · Version {v.evidence.versionNumber}</p> : null}
        </div>
        {v.evidence ? <Button className="max-w-full md:shrink-0" variant="secondary" size="sm" onClick={() => void download(v.verificationId, v.evidence!.filename)}>Download document</Button> : null}
      </li>)}</ul>
    </section> : null}
  </div>
}
