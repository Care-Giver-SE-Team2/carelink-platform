import { cleanup, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { connectSeeded, renderWorkbench, resetWorkbench } from '../testing'

vi.mock('../../../features/provider-platform/api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../../features/provider-platform/api')>(),
  LIVE: true,
  listJobs: vi.fn(),
  getJob: vi.fn(),
}))

beforeEach(resetWorkbench)
afterEach(cleanup)

it.each(['/provider/sandbox', '/provider/sandbox/jobs/JOB-old'])('shows the live capability limit at %s, even with an old token', async (path) => {
  connectSeeded()
  const { listJobs, getJob } = await import('../../../features/provider-platform/api')
  renderWorkbench(path)

  expect(await screen.findByRole('status')).toHaveTextContent('Job integration is not available yet')
  expect(screen.getByRole('link', { name: 'View integration documentation' })).toHaveAttribute('href', '/provider/docs')
  expect(screen.queryByRole('table')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Get access token|Accept job|Decline|Mark complete/ })).not.toBeInTheDocument()
  expect(listJobs).not.toHaveBeenCalled()
  expect(getJob).not.toHaveBeenCalled()
})
