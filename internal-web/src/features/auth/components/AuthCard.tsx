import type { PropsWithChildren } from 'react'
import { MeridianLogo } from '@/components/common/MeridianLogo'
import { Card, CardContent, CardDescription, CardHeader } from '@/components/ui/card'

export function AuthCard({ children }: PropsWithChildren) {
  return (
    <main className="grid min-h-screen place-items-center bg-[linear-gradient(145deg,#09122d_0%,#151f42_48%,#f5f6f8_48%)] p-4 sm:p-8">
      <Card className="w-full max-w-md border-white/40 shadow-2xl">
        <CardHeader className="space-y-5">
          <MeridianLogo className="h-9 w-auto" />
          <div className="space-y-1.5">
            <h1 data-route-heading tabIndex={-1} className="text-lg font-semibold tracking-tight">Staff sign in</h1>
            <CardDescription>Use your Meridian staff credentials to enter the internal workspace.</CardDescription>
          </div>
        </CardHeader>
        <CardContent>{children}</CardContent>
      </Card>
    </main>
  )
}
