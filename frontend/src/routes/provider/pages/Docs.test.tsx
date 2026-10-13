import { cleanup, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it } from 'vitest'
import { renderWorkbench, resetWorkbench } from '../testing'

beforeEach(resetWorkbench)
afterEach(cleanup)

it('explains the integration to anyone, before applying', () => {
  renderWorkbench('/provider/docs')

  expect(screen.getByRole('heading', { name: 'Integrating with CareLink' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Get an access token' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Receive webhooks' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Act on jobs' })).toBeInTheDocument()
  expect(screen.getByText(/Refuse anything older than 5 minutes/)).toBeInTheDocument()
  expect(screen.getByText(/retried after 1 min, 5 min, 30 min, 2 h, 6 h/)).toBeInTheDocument()
  expect(screen.getByText(/createHmac\('sha256'/)).toBeInTheDocument()
  expect(screen.getByText('/v1/jobs/{id}/accept')).toBeInTheDocument()
})

it('goes back to the CareLink home page', async () => {
  renderWorkbench('/provider/docs')
  await userEvent.click(screen.getByRole('link', { name: 'Back to the CareLink home page' }))
  expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
})
