import { cleanup, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as platform from '../../../features/provider-platform/mockPlatform'
import { renderWorkbench, resetWorkbench, signInAs } from '../testing'

beforeEach(resetWorkbench)
afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

describe('Services an approved provider offers', () => {
  it("lists CareLink's catalogue on the application form, a service a manager added included", async () => {
    vi.spyOn(platform, 'listOfferableServices').mockReturnValue(['Companionship', 'Medication collection'])
    renderWorkbench('/provider/apply')

    expect(await screen.findByLabelText('Medication collection')).toBeInTheDocument()
    expect(screen.queryByLabelText('Hospital escort')).not.toBeInTheDocument()
  })

  it('takes on a new service and saves it', async () => {
    vi.spyOn(platform, 'listOfferableServices').mockReturnValue([...platform.MOCK_SERVICE_CATALOGUE, 'Medication collection'])
    signInAs()
    renderWorkbench('/provider/status')

    const card = await screen.findByRole('region', { name: 'Services you offer' })
    const save = within(card).getByRole('button', { name: 'Save services' })
    expect(save).toBeDisabled()

    await userEvent.click(await within(card).findByLabelText('Medication collection'))
    await userEvent.click(save)

    expect(await within(card).findByRole('status')).toHaveTextContent('Saved.')
    const application = screen.getByRole('region', { name: /^Application APP-/ })
    expect(within(application).getByText(/Medication collection/)).toBeInTheDocument()
  })

  it('keeps a service CareLink no longer offers ticked until the provider unticks it', async () => {
    signInAs()
    const offered = platform.listApprovedProviders().find((p) => p.contactEmail === 'partners@brighthome.sg')!.services
    vi.spyOn(platform, 'listOfferableServices').mockReturnValue(
      platform.MOCK_SERVICE_CATALOGUE.filter((s) => s !== offered[0]),
    )
    renderWorkbench('/provider/status')

    const card = await screen.findByRole('region', { name: 'Services you offer' })
    expect(await within(card).findByLabelText(`${offered[0]} (no longer offered by CareLink)`)).toBeChecked()
  })

  it('refuses to save no services', async () => {
    signInAs()
    renderWorkbench('/provider/status')

    const card = await screen.findByRole('region', { name: 'Services you offer' })
    for (const box of await within(card).findAllByRole('checkbox')) {
      if ((box as HTMLInputElement).checked) await userEvent.click(box)
    }
    await userEvent.click(within(card).getByRole('button', { name: 'Save services' }))

    expect(await within(card).findByRole('alert')).toHaveTextContent('Choose at least one service.')
  })
})
