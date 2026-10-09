import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Input } from './input'

describe('Input file-selection treatment', () => {
  it('styles the picker, preserves file attributes, and supports disabled and invalid states', () => {
    render(<Input aria-label="Evidence" type="file" accept="application/pdf" disabled aria-invalid="true" className="mt-2" />)
    const input = screen.getByLabelText('Evidence')
    expect(input).toHaveAttribute('accept', 'application/pdf')
    expect(input).toBeDisabled()
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveClass('min-w-0', 'h-auto', 'overflow-hidden', 'file:border-0', 'file:bg-selected', 'file:rounded-sm', 'file:font-semibold', 'focus-visible:ring-2', 'disabled:opacity-60', 'mt-2')
  })

  it('keeps ordinary text inputs at their established height without file-picker styling', () => {
    render(<Input aria-label="Name" />)
    expect(screen.getByLabelText('Name')).toHaveClass('h-11')
    expect(screen.getByLabelText('Name')).not.toHaveClass('file:bg-selected')
  })
})
