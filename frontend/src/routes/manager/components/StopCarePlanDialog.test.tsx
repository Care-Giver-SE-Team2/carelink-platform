import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { afterEach, expect, it, vi } from 'vitest'

import { StopCarePlanDialog } from './StopCarePlanDialog'
import type { ElderRow } from '../data/elders'
import * as carePlanApi from '../../../shared/api/careplan'

afterEach(cleanup)

function renderModal(props: Parameters<typeof StopCarePlanDialog>[0]) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <StopCarePlanDialog {...props} />
    </QueryClientProvider>,
  )
}

const elder: ElderRow = {
  id: '1',
  name: 'Mdm Lim',
  age: 82,
  street: 'Blk 1',
  sector: 'north',
  planStatus: 'published',
  planVersion: 2,
  hasPublishedPlan: true, hasCareRequest: true,
  primaryCaregiver: null,
  primaryCaregiverId: null,
  primaryCaregiverSince: null,
  nextVisitAt: null,
}

function stopButton() {
  return screen.getByRole('button', { name: /^stop care plan$/i })
}

it('disables the stop button until the reason is filled in, then enables it', async () => {
  vi.spyOn(carePlanApi, 'fetchLatestCarePlan').mockResolvedValue({
    id: 42,
    elderId: 1,
    createdByUserId: null,
    supersedesPlanId: null,
    version: 2,
    status: 'PUBLISHED',
    totalHours: 10,
    publishedAt: '2026-09-01T00:00:00',
    createdAt: '2026-09-01T00:00:00',
    updatedAt: '2026-09-01T00:00:00',
    startDate: '2026-09-01',
    stopEffectiveDate: null,
    stopReason: null,
    stoppedByUserId: null,
    stoppedAt: null,
  })
  const stopSpy = vi.spyOn(carePlanApi, 'stopCarePlan').mockResolvedValue({
    id: 42,
    elderId: 1,
    createdByUserId: null,
    supersedesPlanId: null,
    version: 2,
    status: 'STOPPED',
    totalHours: 10,
    publishedAt: '2026-09-01T00:00:00',
    createdAt: '2026-09-01T00:00:00',
    updatedAt: '2026-09-01T00:00:00',
    startDate: '2026-09-01',
    stopEffectiveDate: '2026-09-22',
    stopReason: 'No longer needed',
    stoppedByUserId: 1,
    stoppedAt: '2026-09-22T00:00:00',
  })

  const user = userEvent.setup()
  renderModal({ elder, onClose: vi.fn(), onStopped: vi.fn() })

  await waitFor(() => expect(screen.getByPlaceholderText('Why is this plan stopping?')).not.toBeDisabled())
  expect(stopButton()).toBeDisabled()

  await user.type(screen.getByPlaceholderText('Why is this plan stopping?'), 'No longer needed')
  expect(stopButton()).not.toBeDisabled()

  await user.click(stopButton())
  await waitFor(() => expect(stopSpy).toHaveBeenCalledWith(42, expect.any(String), 'No longer needed'))
})

it('keeps the stop button disabled while the care plan is still loading', () => {
  vi.spyOn(carePlanApi, 'fetchLatestCarePlan').mockReturnValue(new Promise(() => {}))

  renderModal({ elder, onClose: vi.fn(), onStopped: vi.fn() })

  expect(stopButton()).toBeDisabled()
})
