import { Link } from 'react-router-dom'

export function PublicDemoNotice() {
  return (
    <aside aria-label="Portfolio demo notice" className="mb-6 rounded-lg border border-border bg-muted/40 p-4 text-sm leading-6">
      <p className="font-semibold text-foreground">Portfolio demo</p>
      <p className="mt-1 text-muted-foreground">
        Meridian is a portfolio/demo application. Use fictional information only. Do not submit real
        identity, banking, employment, payroll, financial, or personal documents.
      </p>
      <Link className="mt-2 inline-block font-semibold text-primary underline underline-offset-4" to="/demo-privacy">
        Demo and privacy notice
      </Link>
    </aside>
  )
}
