import { cleanup, fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import * as api from '../../../features/provider-platform/api'
import { ApiError } from '../../../features/provider-platform/errors'
import { listJobs } from '../../../features/provider-platform/mockPlatform'
import { connectSeeded, renderWorkbench, resetWorkbench } from '../testing'

let token: string

beforeEach(() => {
  resetWorkbench()
  token = connectSeeded().accessToken
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

/** The seeded provider's new jobs, newest first: the second is the one the family cancels. */
function newJobs() {
  return listJobs(token, 'new', 1).items
}

async function open(jobId: string) {
  renderWorkbench(`/provider/sandbox/jobs/${jobId}`)
  return screen.findByRole('heading', { level: 1 })
}

it('accepts a new job, which reveals the address', async () => {
  const [job] = newJobs()
  const title = await open(job.id)
  expect(title.parentElement).toHaveTextContent('NEW')
  expect(screen.getByText(/Full address is revealed after you accept\./)).toBeInTheDocument()
  expect(screen.getByText(`${job.id} · received`, { exact: false })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Sandbox' })).toHaveAttribute('href', '/provider/sandbox')

  await userEvent.click(screen.getByRole('button', { name: 'Accept job' }))

  expect(await screen.findByText('ACCEPTED')).toBeInTheDocument()
  expect(screen.getByText(/^Blk \d+/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Open in maps →' })).toHaveAttribute('target', '_blank')
  expect(screen.getByRole('button', { name: 'Mark complete' })).toBeInTheDocument()
  const timeline = screen.getByRole('region', { name: 'Timeline' })
  expect(within(timeline).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
    expect.stringMatching(/^Dispatched/),
    expect.stringMatching(/^Accepted\d/),
    'Completed',
  ])
})

it('declines with a reason', async () => {
  const [job] = newJobs()
  await open(job.id)
  await userEvent.click(screen.getByRole('button', { name: 'Decline' }))

  const dialog = screen.getByRole('dialog', { name: `Decline ${job.id}?` })
  const decline = within(dialog).getByRole('button', { name: 'Decline job' })
  expect(decline).toBeDisabled()
  expect(within(dialog).getByText('CareLink will offer the job to another provider.')).toBeInTheDocument()
  await userEvent.click(within(dialog).getByRole('radio', { name: 'Outside our service area' }))
  await userEvent.type(within(dialog).getByLabelText(/Note to CareLink/), 'Too far for us this week')
  await userEvent.click(decline)

  expect(await screen.findByText('DECLINED')).toBeInTheDocument()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(screen.getByText('Read-only')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Accept job' })).not.toBeInTheDocument()
})

it('closes the decline dialog with Escape', async () => {
  const [job] = newJobs()
  await open(job.id)
  await userEvent.click(screen.getByRole('button', { name: 'Decline' }))
  await userEvent.keyboard('{Escape}')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

it('marks an accepted job complete, needing a note and an end after the start', async () => {
  const [job] = listJobs(token, 'accepted', 1).items
  await open(job.id)
  await userEvent.click(screen.getByRole('button', { name: 'Mark complete' }))

  const dialog = screen.getByRole('dialog', { name: `Mark ${job.id} complete` })
  const start = within(dialog).getByLabelText('Actual start')
  const end = within(dialog).getByLabelText('Actual end')
  expect(start).toHaveValue(new Date(Date.parse(job.scheduledStart) + 8 * 3600_000).toISOString().slice(0, 16))

  fireEvent.change(end, { target: { value: (start as HTMLInputElement).value } })
  await userEvent.click(within(dialog).getByRole('button', { name: 'Mark complete' }))
  expect(within(dialog).getByText('The end must be after the start.')).toBeInTheDocument()
  expect(within(dialog).getByText('Add a note for the family.')).toBeInTheDocument()

  const later = new Date(Date.parse(job.scheduledStart) + 8 * 3600_000 + 50 * 60_000).toISOString().slice(0, 16)
  fireEvent.change(end, { target: { value: later } })
  await userEvent.type(within(dialog).getByLabelText('Completion note'), 'Kitchen and living room done.')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Mark complete' }))

  expect(await screen.findByText('COMPLETED')).toBeInTheDocument()
  const completion = screen.getByText('Completion').parentElement!
  expect(completion).toHaveTextContent('Kitchen and living room done.')
  expect(screen.getByText('Read-only')).toBeInTheDocument()
})

it('shows CareLink cancelled the job when the acceptance comes too late', async () => {
  const doomed = newJobs().find((j) => j.scheduledStart.endsWith('06:30:00.000Z'))!
  await open(doomed.id)
  await userEvent.click(screen.getByRole('button', { name: 'Accept job' }))

  const banner = await screen.findByRole('alert')
  expect(banner).toHaveTextContent('Job was cancelled by CareLink')
  expect(banner).toHaveTextContent(/family cancelled the booking/)
  expect(within(banner).getByRole('link', { name: 'Back to jobs' })).toBeInTheDocument()
  expect(await screen.findByText('CANCELLED')).toBeInTheDocument()
  expect(screen.getByText('Read-only')).toBeInTheDocument()
  expect(screen.getByText('Cancelled by CareLink')).toBeInTheDocument()
})

it('retries a dropped request with the same Idempotency-Key', async () => {
  const [job] = newJobs()
  const accept = vi
    .spyOn(api, 'acceptJob')
    .mockRejectedValueOnce(new ApiError(0, 'network_error', "Couldn't reach CareLink.", 'req_3b91f0'))
  await open(job.id)
  await userEvent.click(screen.getByRole('button', { name: 'Accept job' }))

  const banner = await screen.findByRole('alert')
  expect(banner).toHaveTextContent("Couldn't reach CareLink")
  expect(banner).toHaveTextContent("Your acceptance wasn't saved.")
  expect(banner).toHaveTextContent('req_3b91f0')

  await userEvent.click(within(banner).getByRole('button', { name: 'Retry' }))
  expect(await screen.findByText('ACCEPTED')).toBeInTheDocument()
  expect(accept).toHaveBeenCalledTimes(2)
  expect(accept.mock.calls[1][2]).toBe(accept.mock.calls[0][2])
})

it('asks to reconnect when the token is refused', async () => {
  const [job] = newJobs()
  vi.spyOn(api, 'acceptJob').mockRejectedValueOnce(new ApiError(401, 'invalid_token', 'expired', 'req_1'))
  await open(job.id)
  await userEvent.click(screen.getByRole('button', { name: 'Accept job' }))

  const banner = await screen.findByRole('alert')
  expect(banner).toHaveTextContent('Access token expired')
  await userEvent.click(within(banner).getByRole('button', { name: 'Reconnect' }))
  expect(screen.getByTestId('location')).toHaveTextContent('/provider/sandbox')
})

it('disables the actions while offline', async () => {
  const [job] = newJobs()
  await open(job.id)
  const onLine = vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false)
  fireEvent(window, new Event('offline'))

  expect(await screen.findByText(/You're offline/)).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Accept job' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Decline' })).toBeDisabled()

  onLine.mockReturnValue(true)
  fireEvent(window, new Event('online'))
  expect(await screen.findByRole('button', { name: 'Accept job' })).toBeEnabled()
})

it('says when the job is not this provider’s', async () => {
  renderWorkbench('/provider/sandbox/jobs/JOB-1')
  expect(await screen.findByRole('alert')).toHaveTextContent('No such job')
})
