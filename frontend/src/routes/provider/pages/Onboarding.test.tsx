import { cleanup, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  approveApplication,
  listApplicationsForReview,
  listApprovedProviders,
  suspendProvider,
} from '../../../features/provider-platform/mockPlatform'
import { renderWorkbench, resetWorkbench, signInAs } from '../testing'

beforeEach(resetWorkbench)
afterEach(cleanup)

async function fillApplication() {
  await userEvent.type(screen.getByLabelText('Organisation name'), 'Kinetic Care Pte Ltd')
  await userEvent.type(screen.getByLabelText('Contact name'), 'Rachel Ong')
  await userEvent.type(screen.getByLabelText('Email'), 'rachel@kinetic.sg')
  await userEvent.type(screen.getByLabelText('Phone'), '+65 6123 4567')
  await userEvent.click(screen.getByLabelText('Companionship'))
  await userEvent.type(screen.getByLabelText('Webhook URL'), 'https://hooks.kinetic.sg/carelink')
  await userEvent.type(screen.getByLabelText('Password'), 'kinetic-password')
  await userEvent.type(screen.getByLabelText('Confirm password'), 'kinetic-password')
  await userEvent.click(screen.getByRole('checkbox', { name: /I agree to the/ }))
}

/** Applies through the form and returns the application ID shown. */
async function apply(): Promise<string> {
  renderWorkbench('/provider/apply')
  await fillApplication()
  await userEvent.click(screen.getByRole('button', { name: 'Submit application' }))
  await screen.findByRole('heading', { name: 'Application received' })
  return screen.getByText(/^APP-2026-\d{5}$/).textContent!
}

function approve(id: string) {
  approveApplication(listApplicationsForReview().find((a) => a.id === id)!.seq, null)
}

describe('Apply', () => {
  it('locks Credentials, Jobs and Webhooks until approval', () => {
    renderWorkbench('/provider/apply')

    const nav = screen.getByRole('navigation', { name: 'Partner Portal' })
    expect(within(nav).getByRole('link', { name: 'Apply' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Status' })).toBeInTheDocument()
    expect(within(nav).queryByRole('link', { name: 'Sandbox' })).not.toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Docs' })).toBeInTheDocument()
    expect(within(nav).getAllByTitle('Available after approval')).toHaveLength(3)
    expect(screen.getByText('API credentials, Webhooks and Sandbox unlock once CareLink approves your application.')).toBeInTheDocument()
    expect(screen.queryByText('PROVIDER')).not.toBeInTheDocument()
  })

  it('sends a locked page back to Status', async () => {
    renderWorkbench('/provider/jobs')
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/provider/status'))
  })

  it('lists every problem after Submit, each linked to its field', async () => {
    renderWorkbench('/provider/apply')
    await userEvent.type(screen.getByLabelText('Webhook URL'), 'http://hooks.kinetic.sg/carelink')
    await userEvent.click(screen.getByRole('button', { name: 'Submit application' }))

    const summary = screen.getByRole('alert')
    expect(summary).toHaveTextContent('Fix 8 fields before submitting')
    expect(within(summary).getByRole('link', { name: 'Webhook URL: must be HTTPS' })).toBeInTheDocument()
    expect(screen.getByText('Must be HTTPS. Change http:// to https://.')).toBeInTheDocument()
    expect(screen.getByLabelText('Webhook URL')).toHaveAttribute('aria-invalid', 'true')

    await userEvent.click(within(summary).getByRole('link', { name: 'Email: enter a valid email address' }))
    expect(screen.getByLabelText('Email')).toHaveFocus()

    // Errors follow the fields once shown.
    await userEvent.type(screen.getByLabelText('Organisation name'), 'Kinetic')
    expect(screen.getByRole('alert')).toHaveTextContent('Fix 7 fields before submitting')
  })

  it('submits, shows the application ID and checks its status', async () => {
    const id = await apply()
    expect(screen.getByText('SUBMITTED')).toBeInTheDocument()
    expect(screen.getByText(/We've emailed a copy to rachel@kinetic.sg/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Check application status' }))
    const card = await screen.findByRole('region', { name: `Application ${id}` })
    expect(within(card).getByText('PENDING REVIEW')).toBeInTheDocument()
    expect(within(card).getByText(/usually reviewed within 2 working days/)).toBeInTheDocument()
    expect(within(card).getByText('https://hooks.kinetic.sg/carelink')).toBeInTheDocument()
    expect(listApplicationsForReview().some((a) => a.id === id)).toBe(true)
  })
})

describe('Status and signing in', () => {
  it('refuses a wrong email or password', async () => {
    renderWorkbench('/provider/status')
    expect(screen.getByRole('link', { name: 'Back to the CareLink home page' })).toHaveAttribute('href', '/')
    await userEvent.type(screen.getByLabelText('Email'), 'partners@brighthome.sg')
    await userEvent.type(screen.getByLabelText('Password'), 'not-the-password')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is wrong')
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true')
  })

  it("signs in and shows CareLink's reason for a rejection", async () => {
    renderWorkbench('/provider/status')
    await userEvent.type(screen.getByLabelText('Email'), 'alvin@quickclean.sg')
    await userEvent.type(screen.getByLabelText('Password'), 'quickclean-demo')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))

    const card = await screen.findByRole('region', { name: 'Application APP-2026-00410' })
    expect(within(card).getByText('REJECTED')).toBeInTheDocument()
    expect(within(card).getByText(/returned 404 during our verification check/)).toBeInTheDocument()
    expect(within(card).getByRole('link', { name: 'Start a new application →' })).toHaveAttribute('href', '/provider/apply')
    expect(screen.getByText('PROVIDER').parentElement).toHaveTextContent('QuickClean Home Care')
    // Rejected: still locked.
    expect(within(screen.getByRole('navigation', { name: 'Partner Portal' })).queryByRole('link', { name: 'API credentials' })).not.toBeInTheDocument()
  })

  it('signs out, locking the portal again', async () => {
    signInAs()
    renderWorkbench('/provider/credentials')
    await screen.findByRole('heading', { name: 'API credentials' })

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/provider/status')
    expect(within(screen.getByRole('navigation', { name: 'Partner Portal' })).queryByRole('link', { name: 'Webhooks' })).not.toBeInTheDocument()
    expect(sessionStorage.getItem('carelink.workbench.session')).toBeNull()
  })

  it('sends a returning provider to sign in instead of applying again', async () => {
    renderWorkbench('/provider/apply')
    await userEvent.type(screen.getByLabelText('Organisation name'), 'BrightHome Services')
    await userEvent.type(screen.getByLabelText('Contact name'), 'Ravi Kumar')
    await userEvent.type(screen.getByLabelText('Email'), 'partners@brighthome.sg')
    await userEvent.type(screen.getByLabelText('Phone'), '+65 6789 2210')
    await userEvent.click(screen.getByLabelText('Companionship'))
    await userEvent.type(screen.getByLabelText('Webhook URL'), 'https://brighthome.sg/hooks')
    await userEvent.type(screen.getByLabelText('Password'), 'another-password')
    await userEvent.type(screen.getByLabelText('Confirm password'), 'another-password')
    await userEvent.click(screen.getByRole('checkbox', { name: /I agree to the/ }))
    await userEvent.click(screen.getByRole('button', { name: 'Submit application' }))

    const banner = await screen.findByRole('alert')
    expect(banner).toHaveTextContent('This email already has a provider account')
    expect(within(banner).getByRole('link', { name: 'Sign in →' })).toHaveAttribute('href', '/provider/status')
  })
})

describe('API credentials', () => {
  it('goes from approval to managed credentials, showing the secrets once', async () => {
    const id = await apply()
    expect(screen.getByText(/you're signed in to the portal/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Check application status' }))
    const pending = await screen.findByRole('region', { name: `Application ${id}` })
    expect(within(pending).getByText('PENDING REVIEW')).toBeInTheDocument()

    // CareLink approves; checking again unlocks the nav, and the header shows the webhooks.
    approve(id)
    await userEvent.click(screen.getByRole('button', { name: 'Check again' }))
    await screen.findByText("You're approved")
    const nav = () => screen.getByRole('navigation', { name: 'Partner Portal' })
    expect(within(nav()).getByRole('link', { name: 'Sandbox' })).toBeInTheDocument()
    expect(await screen.findByText('WEBHOOKS OK')).toBeInTheDocument()
    expect(screen.getByText('PROVIDER').parentElement).toHaveTextContent('Kinetic Care Pte Ltd')
    await userEvent.click(screen.getByRole('button', { name: 'View credentials →' }))

    await screen.findByRole('heading', { name: 'Your credentials' })
    expect(screen.getByText('This is shown only once. Store it securely now.')).toBeInTheDocument()
    const clientId = screen.getByRole('status', { name: 'Client ID' }).textContent!
    const secret = screen.getByRole('status', { name: 'Client secret' }).textContent!
    expect(clientId).toMatch(/^cl_prov_/)
    expect(secret).toMatch(/^cs_live_/)
    expect(screen.getByRole('status', { name: 'Webhook signing secret' })).toHaveTextContent(/^whsec_/)
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })
    await userEvent.click(screen.getByRole('button', { name: 'Copy client secret' }))
    expect(writeText).toHaveBeenCalledWith(secret)
    expect(screen.getByRole('button', { name: 'Copy client secret' })).toHaveTextContent('Copied')

    // Leaving before confirming asks first.
    await userEvent.click(within(nav()).getByRole('link', { name: 'Status' }))
    const dialog = screen.getByRole('dialog', { name: 'Leave this page?' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Stay' }))
    await userEvent.click(screen.getByRole('link', { name: 'Back to the CareLink home page' }))
    await userEvent.click(within(screen.getByRole('dialog', { name: 'Leave this page?' })).getByRole('button', { name: 'Stay' }))
    expect(screen.getByRole('heading', { name: 'Your credentials' })).toBeInTheDocument()

    const proceed = screen.getByRole('button', { name: 'Continue' })
    expect(proceed).toBeDisabled()
    await userEvent.click(screen.getByRole('checkbox', { name: "I've stored all three values somewhere safe." }))
    await userEvent.click(proceed)

    // Afterwards only a hint of the secret, and how a server uses it.
    await screen.findByRole('heading', { name: 'API credentials' })
    expect(await screen.findByText(`cs_live_••••••••••••${secret.slice(-4)}`)).toBeInTheDocument()
    expect(screen.queryByText(secret)).not.toBeInTheDocument()
    expect(screen.getByRole('status', { name: 'Client ID' })).toHaveTextContent(clientId)
    expect(screen.getByText(/grant_type=client_credentials/)).toBeInTheDocument()

    // The Sandbox starts filled in from the credentials just shown.
    await userEvent.click(within(nav()).getByRole('link', { name: 'Sandbox' }))
    expect(await screen.findByLabelText('Client ID')).toHaveValue(clientId)
    expect(screen.getByLabelText('Client secret')).toHaveAttribute('type', 'password')
    await userEvent.click(screen.getByRole('button', { name: 'Get access token' }))
    const bar = await screen.findByRole('region', { name: 'Sandbox connection' })
    expect(bar).toHaveTextContent('Connected')
    expect(bar).toHaveTextContent(`${clientId} · jobs:read jobs:write · expires in 60 min`)
    expect(await screen.findByRole('table')).toBeInTheDocument()
  })

  it('rotates the client secret, showing the new one once', async () => {
    signInAs()
    renderWorkbench('/provider/status')
    await userEvent.click(await screen.findByRole('button', { name: 'View credentials →' }))

    // Already collected: straight to the managed view.
    await screen.findByRole('heading', { name: 'API credentials' })
    expect(await screen.findByText('cs_live_••••••••••••home')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Rotate secret' }))
    const dialog = screen.getByRole('dialog', { name: 'Rotate client secret?' })
    expect(dialog).toHaveTextContent('Your current secret stops working immediately')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Rotate secret' }))

    const reveal = await screen.findByRole('region', { name: 'New client secret' })
    const fresh = within(reveal).getByRole('status', { name: 'Client secret' }).textContent!
    expect(fresh).toMatch(/^cs_live_/)
    expect(await screen.findByText(`cs_live_••••••••••••${fresh.slice(-4)}`)).toBeInTheDocument()

    const done = within(reveal).getByRole('button', { name: 'Done' })
    expect(done).toBeDisabled()
    await userEvent.click(within(reveal).getByRole('checkbox'))
    await userEvent.click(done)
    expect(screen.queryByRole('region', { name: 'New client secret' })).not.toBeInTheDocument()
  })

  it('shows invalid credentials in the Sandbox', async () => {
    signInAs()
    renderWorkbench('/provider/status')
    await screen.findByText("You're approved")
    await userEvent.click(within(screen.getByRole('navigation', { name: 'Partner Portal' })).getByRole('link', { name: 'Sandbox' }))

    await userEvent.type(await screen.findByLabelText('Client ID'), 'cl_prov_BrightHome9')
    await userEvent.type(screen.getByLabelText('Client secret'), 'wrong-secret')
    await userEvent.click(screen.getByRole('button', { name: 'Show' }))
    expect(screen.getByLabelText('Client secret')).toHaveAttribute('type', 'text')
    await userEvent.click(screen.getByRole('button', { name: 'Get access token' }))

    const error = await screen.findByRole('alert')
    expect(error).toHaveTextContent('Error: invalid credentials')
    expect(error).toHaveTextContent('401 invalid_client')
    expect(screen.getByLabelText('Client secret')).toHaveAttribute('aria-invalid', 'true')
  })

  it('says so when CareLink has suspended the provider', async () => {
    const seq = listApprovedProviders().find((p) => p.id === 'APP-2026-00409')!.seq
    suspendProvider(seq)
    signInAs()
    renderWorkbench('/provider/status')

    expect(await screen.findByText('CareLink has suspended your provider account')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'View credentials →' })).not.toBeInTheDocument()
    expect(await screen.findByText('SUSPENDED')).toBeInTheDocument()
  })
})
