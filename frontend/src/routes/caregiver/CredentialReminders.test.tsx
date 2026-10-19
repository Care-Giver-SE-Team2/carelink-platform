import { cleanup, render as renderPage, screen } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import type { CredentialAlert, CredentialAlertContext, RenewalState } from '../../features/caregiver/api'
import CredentialReminders from './CredentialReminders'

afterEach(cleanup)
const render = (element: ReactElement) => renderPage(<MemoryRouter>{element}</MemoryRouter>)
const context: CredentialAlertContext = { asOfDate: '2026-09-25', warningDays: 30, reviewRequired: false }
const base: CredentialAlert = { id: 1, name: 'First aid', certificateNo: 'DEMO-1', expiryDate: '2026-09-25',
  status: 'PUBLISHED', warning: 'EXPIRING', daysUntilExpiry: 0, renewalState: 'NONE', renewalValidFrom: null }

describe('credential reminders', () => {
  it.each([
    [-2, 'EXPIRED', 'Expired 2 days ago.'], [-1, 'EXPIRED', 'Expired 1 day ago.'],
    [0, 'EXPIRING', 'Valid through today (SGT).'], [1, 'EXPIRING', 'Expires in 1 day.'],
    [30, 'EXPIRING', 'Expires in 30 days.'],
  ] as const)('uses server days %s and warning %s', (daysUntilExpiry, warning, text) => {
    render(<CredentialReminders alerts={[{ ...base, daysUntilExpiry, warning }]} context={context} />)
    expect(screen.getByText(new RegExp(text.replace(/[().]/g, '\\$&')))).toBeInTheDocument()
    expect(screen.getByText('Certificate: DEMO-1')).toBeInTheDocument()
    if (daysUntilExpiry === 0) expect(screen.getByText('First aid · Expires today')).toBeInTheDocument()
    expect(screen.queryByText(/Recorded status/)).not.toBeInTheDocument()
  })

  it.each([
    ['PENDING_REVIEW', /Renewal pending review/], ['REJECTED', /Renewal not approved/],
    ['APPROVED_NOT_EFFECTIVE', /Renewal approved; effective 2 Oct 2026/],
    ['REVOKED', /Replacement revoked/], ['CHECK_REQUIRED', /Credential records need review/],
    ['NONE', /Contact your manager about renewal/],
  ] satisfies [RenewalState, RegExp][])('explains renewal state %s', (renewalState, message) => {
    render(<CredentialReminders alerts={[{ ...base, renewalState, renewalValidFrom: '2026-10-02' }]} context={context} />)
    expect(screen.getByText(message)).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('shows context and avoids treating an empty reminder list as credential approval', () => {
    render(<CredentialReminders alerts={[]} context={{ ...context, warningDays: 0, reviewRequired: true }} />)
    expect(screen.getByText(/Assessed 25 Sept 2026.*0-day warning window/)).toBeInTheDocument()
    expect(screen.getByText(/Changing schedule dates does not change/)).toBeInTheDocument()
    expect(screen.getByText('No credential expiry reminders.')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/manager’s review/)
    expect(screen.getByText(/not confirmation that all credentials are approved/)).toBeInTheDocument()
  })

  it('supports a response from the previous slice without browser-date calculations', () => {
    const { daysUntilExpiry: _days, renewalState: _state, ...legacy } = base
    render(<CredentialReminders alerts={[{ ...legacy, certificateNo: null }]} />)
    expect(screen.getByText(/current Singapore date/)).toBeInTheDocument()
    expect(screen.queryByText(/Certificate:/)).not.toBeInTheDocument()
    expect(screen.getByText('First aid · Expiring soon')).toBeInTheDocument()
  })

  it('does not invent a start date when a renewal response is incomplete', () => {
    render(<CredentialReminders alerts={[{ ...base, renewalState: 'APPROVED_NOT_EFFECTIVE' }]} context={context} />)
    expect(screen.getByText(/confirm the start date/)).toBeInTheDocument()
  })

  it('removes an old reminder when refreshed data no longer includes it', () => {
    const { rerender } = render(<CredentialReminders alerts={[base]} context={context} />)
    expect(screen.getByText('First aid · Expires today')).toBeInTheDocument()
    rerender(<CredentialReminders alerts={[]} context={context} />)
    expect(screen.queryByText('First aid · Expires today')).not.toBeInTheDocument()
    expect(screen.getByText('No credential expiry reminders.')).toBeInTheDocument()
  })
})
