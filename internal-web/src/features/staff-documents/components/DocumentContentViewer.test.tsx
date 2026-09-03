import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AuthSessionManager } from '@/features/auth/model/auth-session'
import { DocumentContentViewer } from './DocumentContentViewer'

afterEach(() => vi.unstubAllGlobals())

describe('DocumentContentViewer', () => {
  it('does not fetch before explicit view and revokes the memory URL on close', async () => {
    const protectedRequest = vi.fn().mockResolvedValue({
      blob: new Blob(['image'], { type: 'image/png' }), contentType: 'image/png',
    })
    const revoke = vi.fn()
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:document'), revokeObjectURL: revoke })
    const user = userEvent.setup()
    render(<DocumentContentViewer manager={{ protectedRequest } as unknown as AuthSessionManager} loanApplicationId="application" checklistItemId="item" documentVersionId="version" filename="proof.png" />)
    expect(protectedRequest).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'View document' }))
    expect(await screen.findByRole('img', { name: 'Document evidence: proof.png' })).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Close viewer' }))
    expect(revoke).toHaveBeenCalledWith('blob:document')
  })
})
