import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import IncidentsPage, { IncidentDetailPage } from './IncidentsPage'
import ReportIncidentPage from './ReportIncidentPage'
import { ApiError } from '../../shared/api/client'
import { getReport, listReports, reportIncident } from '../../features/caregiver-incidents/api'
import { getWorkPack } from '../../features/caregiver/api'
vi.mock('../../features/caregiver-incidents/api', () => ({ getReport: vi.fn(), listReports: vi.fn(), reportIncident: vi.fn() }))
vi.mock('../../features/caregiver/api', () => ({ getWorkPack: vi.fn() }))
const report = { id: 7, visitId: 3, category: 'SERVICE', severity: 'MEDIUM', description: '<script>Observed facts</script>', status: 'OPEN', reportedAt: '2026-10-07T12:00:00', respondBy: '2026-10-07T14:00:00', resolvedAt: null }
const view = { report, clientRequestId: 'key' }
function setup(path = '/caregiver/incidents') { return render(<MemoryRouter initialEntries={[path]}><Routes><Route path="/caregiver/incidents" element={<IncidentsPage />} /><Route path="/caregiver/incidents/:incidentId" element={<IncidentDetailPage />} /><Route path="/caregiver/visits/:visitId/report-incident" element={<ReportIncidentPage />} /></Routes></MemoryRouter>) }
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(listReports).mockResolvedValue({ items: [view], page: 0, size: 20, totalElements: 1 })
  vi.mocked(getReport).mockResolvedValue(view)
  vi.mocked(getWorkPack).mockResolvedValue({ visit: { id: 3, version: 0, status: 'IN_PROGRESS' } } as never)
})
describe('caregiver incident workspace', () => {
  it('lists own reports and refreshes without exposing a manager link', async () => {
    setup(); expect(await screen.findByRole('link', { name: 'Open report #7' })).toHaveAttribute('href', '/caregiver/incidents/7')
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' })); await waitFor(() => expect(listReports).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })
  it('renders care text literally and never claims the visit is resumed', async () => {
    setup('/caregiver/incidents/7'); expect(await screen.findByText(report.description)).toBeInTheDocument()
    expect(screen.getByText(/does not automatically resume/)).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
  })
  it('clears prior detail on a failed return refresh', async () => {
    setup('/caregiver/incidents/7'); await screen.findByText(report.description)
    vi.mocked(getReport).mockRejectedValue(new ApiError('Not found',404,null))
    fireEvent.click(screen.getByRole('button',{name:'Refresh'})); await screen.findByText(/Report unavailable or could not/)
    expect(screen.queryByText(report.description)).toBeNull()
  })
  it('submits trusted visit/version fields and navigates to its safe receipt', async () => {
    vi.mocked(reportIncident).mockResolvedValue({ ...view, visitVersion: 1, replayed: false })
    setup('/caregiver/visits/3/report-incident?dateFrom=2026-10-07&dateTo=2026-10-07')
    await waitFor(() => expect(screen.getByLabelText('Category')).toBeEnabled())
    expect(screen.getByRole('link',{name:/Assigned work pack/})).toHaveAttribute('href','/caregiver/visits/3?dateFrom=2026-10-07&dateTo=2026-10-07')
    fireEvent.change(screen.getByLabelText('Category'),{target:{value:'SERVICE'}})
    fireEvent.change(screen.getByLabelText('Severity'),{target:{value:'MEDIUM'}})
    fireEvent.change(screen.getByLabelText('Observed facts and actions'),{target:{value:' Facts '}})
    fireEvent.click(screen.getByRole('button',{name:'Submit report'}))
    await screen.findByRole('heading',{name:'Submitted report'})
    expect(reportIncident).toHaveBeenCalledWith(expect.objectContaining({visitId:3,expectedVersion:0,description:'Facts',clientRequestId:expect.any(String)}),expect.any(AbortSignal))
  })
  it('does not retry automatically and freezes the original command after an unknown result', async () => {
    vi.mocked(reportIncident).mockRejectedValue(new TypeError('Network'))
    setup('/caregiver/visits/3/report-incident'); await waitFor(() => expect(screen.getByLabelText('Category')).toBeEnabled())
    fireEvent.change(screen.getByLabelText('Category'),{target:{value:'SERVICE'}});fireEvent.change(screen.getByLabelText('Severity'),{target:{value:'LOW'}})
    fireEvent.change(screen.getByLabelText('Observed facts and actions'),{target:{value:'Facts'}});fireEvent.click(screen.getByRole('button',{name:'Submit report'}))
    await screen.findByText(/Result unknown/);expect(reportIncident).toHaveBeenCalledTimes(1);expect(screen.getByLabelText('Observed facts and actions')).toBeDisabled()
    expect(screen.getByRole('link', { name: /Open my reports in a new tab/ })).toHaveAttribute('target', '_blank')
    fireEvent.click(screen.getByRole('button',{name:'Retry same request'}));await waitFor(() => expect(reportIncident).toHaveBeenCalledTimes(2))
    expect(vi.mocked(reportIncident).mock.calls[0][0]).toEqual(vi.mocked(reportIncident).mock.calls[1][0])
  })
})
