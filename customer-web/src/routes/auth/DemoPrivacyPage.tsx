import { Link } from 'react-router-dom'

import { AuthCard } from '@/features/auth/components/AuthCard'

export function DemoPrivacyPage() {
  return (
    <AuthCard
      eyebrow="Portfolio demo"
      title="Demo and privacy notice"
      description="Meridian is a public software-engineering portfolio/demo. It is not a real financial service."
      footer={<Link className="text-sm font-semibold text-primary underline underline-offset-4" to="/login">Return to login</Link>}
    >
      <div className="space-y-6 text-sm leading-6 text-muted-foreground">
        <section className="space-y-2">
          <h2 className="font-semibold text-foreground">Use fictional information</h2>
          <p>
            Use fictional information only. Do not submit real identity or personal documents, banking
            details, employment or payroll information, or real financial information.
          </p>
          <p>
            Registration requires an email address you can access for verification and password recovery.
            Use a dedicated demo email address and a password you do not use elsewhere. Keep profile,
            application, and uploaded document information fictional.
          </p>
        </section>
        <section className="space-y-2">
          <h2 className="font-semibold text-foreground">Demo data and deletion</h2>
          <p>Demo data may be manually reset or deleted by the operator. Do not rely on this demo to retain records.</p>
          <p>
            To request deletion or report information submitted by mistake, contact the operator through
            the portfolio or deployment page that linked you here. Share only your demo account email
            and a description of the request; do not send documents, banking details, or passwords.
          </p>
        </section>
      </div>
    </AuthCard>
  )
}
