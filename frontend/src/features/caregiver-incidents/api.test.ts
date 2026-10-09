import { beforeEach, expect, it, vi } from 'vitest'
import { api } from '../../shared/api/client'
import { getReport, listReports, reportIncident } from './api'
vi.mock('../../shared/api/client', () => ({ api: vi.fn() }))
beforeEach(() => vi.clearAllMocks())
it('uses only caregiver safe reads and bootstraps CSRF before a single write', async () => {
  const signal = new AbortController().signal
  await listReports(2,signal);expect(api).toHaveBeenCalledWith('/caregivers/me/incidents?page=2&size=20',{signal})
  await getReport('7',signal);expect(api).toHaveBeenCalledWith('/caregivers/me/incidents/7',{signal})
  const input={visitId:3,category:'SERVICE',severity:'LOW',description:'facts',expectedVersion:0,clientRequestId:'uuid'}
  await reportIncident(input,signal)
  expect(api).toHaveBeenNthCalledWith(3,'/auth/csrf',{signal})
  expect(api).toHaveBeenNthCalledWith(4,'/incidents',{method:'POST',body:JSON.stringify(input),signal})
})
