import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { Badge } from './Badge'

describe('Badge', () => {
  it('renders children', () => {
    render(<Badge tone="primary">Hello</Badge>)
    expect(screen.getByText('Hello')).toBeInTheDocument()
  })

  it('applies tone class', () => {
    render(<Badge tone="primary">Test</Badge>)
    const badge = screen.getByText('Test')
    expect(badge.className).toContain('bg-primary-soft')
  })

  it('applies danger tone', () => {
    render(<Badge tone="danger">Danger</Badge>)
    const badge = screen.getByText('Danger')
    expect(badge.className).toContain('bg-destructive-soft')
  })
})
