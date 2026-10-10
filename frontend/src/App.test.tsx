import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('renders the landing page with a sign-in form and the way in for families without an account', () => {
    render(<App />)

    expect(screen.getByText('CareLink')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /Sign in/i })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Register as a family member/ })).toHaveAttribute('href', '/apply')
    for (const label of ['Manager', 'Caregiver', 'Family', 'Elder', 'Admin']) {
      expect(screen.queryByRole('link', { name: label })).not.toBeInTheDocument()
    }
  })
})
