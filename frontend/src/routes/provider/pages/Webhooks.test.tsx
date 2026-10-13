import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it } from 'vitest'
import { getWebhookSettings, signIn } from '../../../features/provider-platform/mockPlatform'
import { renderWorkbench, resetWorkbench, SEEDED, signInAs } from '../testing'

beforeEach(() => {
  resetWorkbench()
  signInAs()
})
afterEach(cleanup)

async function openWebhooks() {
  renderWorkbench('/provider/webhooks')
  return screen.findByRole('table')
}

function rows(table: HTMLElement) {
  return within(table).getAllByRole('row').slice(1)
}

it('lists what CareLink sent, with each delivery’s attempts', async () => {
  const table = await openWebhooks()
  expect(screen.getByText(/retried after 1 min, 5 min, 30 min, 2 h, 6 h: 6 attempts in all/)).toBeInTheDocument()
  expect(rows(table).length).toBeGreaterThan(10)
  expect(rows(table)[0]).toHaveAttribute('aria-selected', 'true')
  expect(within(table).getAllByText('DELIVERED').length).toBeGreaterThan(5)
  expect(within(table).getByText('RETRYING')).toBeInTheDocument()
  expect(within(table).getByText('FAILED')).toBeInTheDocument()
  expect(await screen.findByText('1 WEBHOOK FAILED')).toBeInTheDocument()

  // The one still retrying: its attempts so far and when it tries next.
  const retrying = within(table).getByText('RETRYING').closest('tr')!
  const id = within(retrying).getAllByRole('cell')[0].textContent!
  await userEvent.click(retrying)
  const panel = screen.getByRole('region', { name: `Delivery ${id}` })
  expect(within(panel).getAllByRole('listitem')).toHaveLength(3)
  expect(within(panel).getByText(/^Next attempt .* \(4 of 6\)\.$/)).toBeInTheDocument()
  expect(within(panel).getByText(`X-CareLink-Delivery: ${id}`, { exact: false })).toBeInTheDocument()
  expect(within(panel).getByText(new RegExp(`"delivery_id": "${id}"`))).toBeInTheDocument()
})

it('resends a failed delivery with the same ID', async () => {
  const table = await openWebhooks()
  const failed = within(table).getByText('FAILED').closest('tr')!
  const id = within(failed).getAllByRole('cell')[0].textContent!
  await userEvent.click(failed)
  const panel = screen.getByRole('region', { name: `Delivery ${id}` })
  expect(within(panel).getByText('CareLink gave up after 6 attempts. Fix your endpoint, then Resend.')).toBeInTheDocument()

  await userEvent.click(within(panel).getByRole('button', { name: 'Resend' }))
  const after = await screen.findByRole('region', { name: `Delivery ${id}` })
  expect(await within(after).findByText('DELIVERED')).toBeInTheDocument()
  expect(within(after).getAllByRole('listitem')).toHaveLength(7)
  expect(within(screen.getByRole('table')).queryByText('FAILED')).not.toBeInTheDocument()
})

it('sends a test event and opens it', async () => {
  await openWebhooks()
  await userEvent.click(screen.getByRole('button', { name: 'Send test event' }))

  const table = screen.getByRole('table')
  expect(await within(table).findByText('ping')).toBeInTheDocument()
  const ping = within(table).getByText('ping').closest('tr')!
  expect(ping).toHaveAttribute('aria-selected', 'true')
})

it('changes the endpoint and its events, refusing anything but HTTPS', async () => {
  await openWebhooks()
  const url = screen.getByLabelText('Endpoint URL')
  const save = screen.getByRole('button', { name: 'Save changes' })
  expect(url).toHaveValue('https://brighthome.sg/api/carelink-jobs')
  expect(save).toBeDisabled()

  await userEvent.clear(url)
  await userEvent.type(url, 'http://brighthome.sg/hooks')
  await userEvent.click(save)
  expect(screen.getByText(/Must be a full HTTPS URL/)).toBeInTheDocument()

  await userEvent.clear(url)
  await userEvent.type(url, 'https://hooks.brighthome.sg/v2')
  await userEvent.click(screen.getByRole('checkbox', { name: /job\.cancelled/ }))
  await userEvent.click(save)

  expect(await screen.findByText('Saved')).toBeInTheDocument()
  expect(getWebhookSettings(signIn(SEEDED.email, SEEDED.password).session)).toEqual(
    expect.objectContaining({ url: 'https://hooks.brighthome.sg/v2', events: ['job.dispatched'] }),
  )
})

it('rotates the signing secret, showing the new one once', async () => {
  await openWebhooks()
  expect(screen.getByText('whsec_••••••••••••home')).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Rotate secret' }))
  const dialog = screen.getByRole('dialog', { name: 'Rotate signing secret?' })
  await userEvent.click(within(dialog).getByRole('button', { name: 'Rotate secret' }))

  const reveal = await screen.findByRole('region', { name: 'New signing secret' })
  const fresh = within(reveal).getByRole('status', { name: 'Signing secret' }).textContent!
  expect(fresh).toMatch(/^whsec_/)
  expect(await screen.findByText(`whsec_••••••••••••${fresh.slice(-4)}`)).toBeInTheDocument()
})
