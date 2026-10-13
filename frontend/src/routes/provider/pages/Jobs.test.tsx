import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it } from 'vitest'
import {
  approveApplication,
  collectCredentials,
  declineJob,
  listApplicationsForReview,
  listJobs,
  requestToken,
  signIn,
  submitApplication,
} from '../../../features/provider-platform/mockPlatform'
import { connectSeeded, renderWorkbench, resetWorkbench, signInAs } from '../testing'

beforeEach(resetWorkbench)
afterEach(cleanup)

function rows() {
  return within(screen.getByRole('table')).getAllByRole('row').slice(1)
}

it('lists new jobs five to a page, with every tab’s count', async () => {
  connectSeeded()
  renderWorkbench('/provider/sandbox')

  await screen.findByRole('table')
  const tabs = screen.getByRole('tablist', { name: 'Job states' })
  expect(within(tabs).getByRole('tab', { name: /New/ })).toHaveAttribute('aria-selected', 'true')
  expect(within(tabs).getByRole('tab', { name: /New/ })).toHaveTextContent('7')
  expect(within(tabs).getByRole('tab', { name: /Accepted/ })).toHaveTextContent('2')
  expect(within(tabs).getByRole('tab', { name: /Completed/ })).toHaveTextContent('3')
  expect(within(tabs).getByRole('tab', { name: /Declined/ })).toHaveTextContent('2')

  expect(rows()).toHaveLength(5)
  expect(rows()[0]).toHaveTextContent(/^JOB-\d+/)
  expect(rows()[0]).toHaveTextContent('NEW')
  expect(rows()[0]).toHaveTextContent(/D1[689] · (Bedok|Tampines|Hougang)/)
  expect(screen.getByText(/Showing/).parentElement).toHaveTextContent('Showing 1–5 of 7')
  expect(screen.getByText(/^Last refreshed \d\d:\d\d:\d\d$/)).toBeInTheDocument()
  expect(screen.getByRole('region', { name: 'Sandbox connection' })).toHaveTextContent('Connected')

  expect(screen.getByRole('button', { name: '← Previous' })).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: 'Next →' }))
  expect(rows()).toHaveLength(2)
  expect(screen.getByTestId('location')).toHaveTextContent('/provider/sandbox?tab=new&page=2')
  expect(screen.getByRole('button', { name: 'Next →' })).toBeDisabled()
})

it('switches tabs and opens a job from its row', async () => {
  connectSeeded()
  renderWorkbench('/provider/sandbox')
  await screen.findByRole('table')

  await userEvent.click(screen.getByRole('tab', { name: /Completed/ }))
  expect(rows()).toHaveLength(3)
  for (const row of rows()) expect(row).toHaveTextContent('COMPLETED')

  const id = within(rows()[0]).getByRole('link').textContent!
  await userEvent.click(rows()[0])
  expect(screen.getByTestId('location')).toHaveTextContent(`/provider/sandbox/jobs/${id}`)
})

it('shows skeleton rows while refreshing', async () => {
  connectSeeded()
  renderWorkbench('/provider/sandbox')
  await screen.findByRole('table')

  await userEvent.click(screen.getByRole('button', { name: 'Refresh' }))
  expect(await screen.findByRole('table')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Refresh' })).toBeEnabled()
})

it('says when a tab has nothing in it, and where new jobs will arrive', async () => {
  const { applicationId } = submitApplication({
    organisationName: 'Kinetic Care Pte Ltd',
    contactName: 'Rachel Ong',
    contactEmail: 'rachel@kinetic.sg',
    contactPhone: '+65 6123 4567',
    services: ['Companionship'],
    webhookUrl: 'https://hooks.kinetic.sg/carelink',
  }, 'kinetic-password')
  approveApplication(listApplicationsForReview().find((a) => a.id === applicationId)!.seq, null)
  const credentials = collectCredentials(signIn('rachel@kinetic.sg', 'kinetic-password').session)
  const token = requestToken(credentials.clientId, credentials.clientSecret)
  for (const job of listJobs(token.accessToken, 'new', 1).items) {
    declineJob(token.accessToken, job.id, { reason: 'NO_PRACTITIONER', note: null }, `decline-${job.id}`)
  }
  sessionStorage.setItem('carelink.workbench.token', JSON.stringify(token))
  signInAs('rachel@kinetic.sg', 'kinetic-password')
  renderWorkbench('/provider/sandbox')

  expect(await screen.findByText('No new jobs.')).toBeInTheDocument()
  expect(screen.getByText("You'll be notified by webhook when one is dispatched.")).toBeInTheDocument()
  expect(await screen.findByText('Listening at https://hooks.kinetic.sg/carelink')).toBeInTheDocument()

  await userEvent.click(screen.getByRole('tab', { name: /Completed/ }))
  expect(screen.getByText('Nothing here yet.')).toBeInTheDocument()
})

it('asks for a token first, and for a new one when it ran out', async () => {
  signInAs()
  renderWorkbench('/provider/status')
  await screen.findByText("You're approved")
  await userEvent.click(within(screen.getByRole('navigation', { name: 'Partner Portal' })).getByRole('link', { name: 'Sandbox' }))

  const connect = await screen.findByRole('region', { name: 'Sandbox connection' })
  expect(connect).toHaveTextContent('Get an access token')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  cleanup()

  const token = connectSeeded()
  sessionStorage.setItem('carelink.workbench.token', JSON.stringify({ ...token, expiresAt: Date.now() - 1000 }))
  renderWorkbench('/provider/sandbox')
  const expired = await screen.findByRole('region', { name: 'Sandbox connection' })
  expect(expired).toHaveTextContent('Access token expired')
  expect(within(expired).getByLabelText('Client ID')).toHaveValue('cl_prov_BrightHome9')
  expect(within(expired).getByRole('button', { name: 'Get access token' })).toBeInTheDocument()
})

it('sends old job links to the Sandbox', async () => {
  connectSeeded()
  renderWorkbench('/provider/jobs')
  expect(await screen.findByRole('heading', { name: 'Sandbox' })).toBeInTheDocument()
  expect(screen.getByTestId('location')).toHaveTextContent('/provider/sandbox')
})
