import { render, screen, waitFor, fireEvent, within } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import WorkPackPage from './WorkPackPage'
import { getWorkPack, type WorkPack } from '../../features/caregiver/api'
import { checkIn, checkOut, completeTask } from '../../features/caregiver-execution/api'
import { ApiError } from '../../shared/api/client'
vi.mock('../../features/caregiver/api',()=>({getWorkPack:vi.fn()}))
vi.mock('../../features/caregiver-execution/api',()=>({checkIn:vi.fn(),checkOut:vi.fn(),completeTask:vi.fn()}))
const execution={allowedActions:['TASK_RESULT','REPORT_INCIDENT','CHECK_OUT'],blockedReason:null,serverNow:'2026-10-07T12:00:00',checkInOpensAt:'2026-10-07T11:30:00',checkInClosesAt:'2026-10-07T13:00:00',checkedInAt:'2026-10-07T12:00:00',checkedOutAt:null,lateArrival:false,locationSource:'MANUAL_LOCATION_NOTE'}
const pack:WorkPack={visit:{id:3,elderId:4,elderName:'Mei',serviceType:'Care',scheduledStart:'2026-10-07T12:00:00',scheduledEnd:'2026-10-07T13:00:00',status:'IN_PROGRESS',version:1},elder:{elderId:4,preferredName:'Mei',serviceAddress:'Private address',postalSector:'North',languageNeeds:[],accessNotes:null,emergencyNotes:null},carePlanId:7,carePlanVersion:1,serviceInstructions:['Hygiene'],tasks:[{id:9,name:'Hygiene',status:'PENDING',outcome:null,caregiverNote:null}],requiredEvidenceKinds:['CHECKLIST'],execution}
function mount() {render(<MemoryRouter initialEntries={['/caregiver/visits/3']}><Routes><Route path="/caregiver/visits/:visitId" element={<WorkPackPage/>}/></Routes></MemoryRouter>)}
beforeEach(()=>{
  vi.clearAllMocks();vi.mocked(getWorkPack).mockResolvedValue(structuredClone(pack))
  vi.mocked(completeTask).mockResolvedValue({visitId:3,visitVersion:2,savedState:'IN_PROGRESS',taskId:9,replayed:false})
  vi.mocked(checkIn).mockResolvedValue({visitId:3,visitVersion:1,savedState:'IN_PROGRESS',taskId:null,replayed:false})
  vi.mocked(checkOut).mockResolvedValue({visitId:3,visitVersion:2,savedState:'COMPLETED',checkedInAt:execution.checkedInAt,checkedOutAt:'2026-10-07T14:00:00',replayed:false})
})
it('preserves a task draft across a manual refresh and saves once with the displayed parent version',async()=>{
  mount();await screen.findByText('Private address')
  let group=within(screen.getByRole('group',{name:'Record result · Hygiene'}))
  fireEvent.change(group.getByLabelText('Outcome (optional)'),{target:{value:'Observed result'}})
  fireEvent.click(screen.getByRole('button',{name:'Refresh'}));await waitFor(()=>expect(getWorkPack).toHaveBeenCalledTimes(2))
  group=within(await screen.findByRole('group',{name:'Record result · Hygiene'}));expect(group.getByLabelText('Outcome (optional)')).toHaveValue('Observed result')
  vi.mocked(getWorkPack).mockResolvedValue({...pack,visit:{...pack.visit,version:2},tasks:[{...pack.tasks[0],status:'DONE',outcome:'Observed result',completedAt:'2026-10-07T12:05:00'}]})
  fireEvent.click(group.getByRole('button',{name:'Save task result'}));await screen.findByText('Outcome: Observed result')
  expect(completeTask).toHaveBeenCalledWith(3,9,expect.objectContaining({expectedVersion:1,status:'DONE',outcome:'Observed result',clientRequestId:expect.any(String)}),expect.any(AbortSignal))
  expect(screen.queryByRole('button',{name:'Save task result'})).toBeNull();expect(screen.getByText(/Task results are not check-out/)).toBeInTheDocument()
})
it('keeps the original command after an unknown result and does not retry on refresh',async()=>{
  vi.mocked(completeTask).mockRejectedValue(new TypeError('Network'));mount();await screen.findByRole('button',{name:'Save task result'})
  fireEvent.click(screen.getByRole('button',{name:'Save task result'}));await screen.findByText(/Result unknown/)
  fireEvent.click(screen.getByRole('button',{name:'Refresh'}));await waitFor(()=>expect(getWorkPack).toHaveBeenCalledTimes(2));await screen.findByText(/Result unknown/)
  expect(completeTask).toHaveBeenCalledTimes(1);fireEvent.click(screen.getByRole('button',{name:'Retry same request'}));await waitFor(()=>expect(completeTask).toHaveBeenCalledTimes(2))
  expect(vi.mocked(completeTask).mock.calls[0][2]).toEqual(vi.mocked(completeTask).mock.calls[1][2])
})
it('hides old protected data and drafts immediately when a task write loses access',async()=>{
  vi.mocked(completeTask).mockRejectedValue(new ApiError('Denied',403,null));mount();await screen.findByText('Private address')
  fireEvent.change(screen.getByLabelText('Caregiver note (optional)'),{target:{value:'Private draft'}})
  fireEvent.click(screen.getByRole('button',{name:'Save task result'}));await screen.findByText('Access not permitted')
  expect(screen.queryByText('Private address')).toBeNull();expect(screen.queryByDisplayValue('Private draft')).toBeNull()
})
it('marks a manual location explicitly and persists a real check-in before execution',async()=>{
  vi.mocked(getWorkPack).mockResolvedValue({...pack,visit:{...pack.visit,status:'SCHEDULED',version:0},tasks:[],execution:{...execution,allowedActions:['CHECK_IN'],checkedInAt:null,locationSource:null}})
  mount();await screen.findByRole('button',{name:'Check in and start service'})
  fireEvent.change(screen.getByLabelText('Location source'),{target:{value:'MANUAL_LOCATION_NOTE'}})
  fireEvent.change(screen.getByLabelText('Manual location note'),{target:{value:' At doorway '}})
  vi.mocked(getWorkPack).mockResolvedValue(pack)
  fireEvent.click(screen.getByRole('button',{name:'Check in and start service'}));await screen.findByText('Location record: Manual location note — not GPS verified')
  expect(checkIn).toHaveBeenCalledWith(3,expect.objectContaining({expectedVersion:0,locationSource:'MANUAL_LOCATION_NOTE',locationNote:'At doorway'}),expect.any(AbortSignal))
  expect(vi.mocked(checkIn).mock.calls[0][1]).not.toHaveProperty('latitude')
})
it('an exception pauses all task controls without pretending service is complete',async()=>{
  vi.mocked(getWorkPack).mockResolvedValue({...pack,visit:{...pack.visit,status:'EXCEPTION'},execution:{...execution,allowedActions:['REPORT_INCIDENT']}})
  mount();await screen.findByText(/Execution paused/);expect(screen.queryByRole('button',{name:'Save task result'})).toBeNull()
  expect(screen.getByRole('link',{name:'Report incident'})).toHaveAttribute('href','/caregiver/visits/3/report-incident')
})
it('shows an extra service as such, with check-in offered and no care plan',async()=>{
  vi.mocked(getWorkPack).mockResolvedValue({...structuredClone(pack),carePlanId:null,carePlanVersion:null,serviceInstructions:['Hospital escort','Bring the wheelchair'],tasks:[],requiredEvidenceKinds:[],
    visit:{...pack.visit,serviceType:'Hospital escort',status:'SCHEDULED'},execution:{...execution,allowedActions:['CHECK_IN'],checkedInAt:null,locationSource:null}})
  mount()
  const card=within(await screen.findByRole('region',{name:'Extra service'}))
  expect(card.getByRole('heading',{name:'Hospital escort'})).toBeInTheDocument()
  expect(card.getByText('Bring the wheelchair')).toBeInTheDocument()
  expect(screen.queryByRole('region',{name:'Assigned care plan'})).toBeNull()
  expect(screen.getByText('Assigned plan tasks will be initialized when you check in.')).toBeInTheDocument()
})
it('checks out with pending tasks and missing evidence without approval, and becomes read-only',async()=>{
  mount();await screen.findByRole('button',{name:'Check out'})
  expect(screen.getByText(/Some tasks are still pending/)).toBeInTheDocument()
  expect(screen.getByText(/Missing evidence does not prevent/)).toBeInTheDocument()
  vi.mocked(getWorkPack).mockResolvedValue({...pack,visit:{...pack.visit,status:'COMPLETED',version:2},execution:{...execution,allowedActions:['REPORT_INCIDENT'],checkedOutAt:'2026-10-07T14:00:00'}})
  fireEvent.click(screen.getByRole('button',{name:'Check out'}));await screen.findByText(/Checked out:/)
  expect(checkOut).toHaveBeenCalledWith(3,expect.objectContaining({expectedVersion:1,clientRequestId:expect.any(String)}),expect.any(AbortSignal))
  expect(screen.queryByRole('button',{name:'Check out'})).toBeNull()
  expect(screen.queryByRole('button',{name:'Save task result'})).toBeNull()
  expect(screen.getByText('Pending')).toBeInTheDocument()
  expect(screen.getByRole('link',{name:'Report incident'})).toBeInTheDocument()
})
it('keeps checkout identity and version across unknown-result refresh and explicit retry',async()=>{
  vi.mocked(checkOut).mockRejectedValue(new TypeError('Network'));mount();await screen.findByRole('button',{name:'Check out'})
  fireEvent.click(screen.getByRole('button',{name:'Check out'}));await screen.findByText(/Result unknown/)
  expect(screen.getByRole('button',{name:'Save task result'})).toBeDisabled()
  fireEvent.click(screen.getByRole('button',{name:'Refresh'}));await waitFor(()=>expect(getWorkPack).toHaveBeenCalledTimes(2))
  expect(checkOut).toHaveBeenCalledTimes(1)
  fireEvent.click(await screen.findByRole('button',{name:'Retry same request'}));await waitFor(()=>expect(checkOut).toHaveBeenCalledTimes(2))
  expect(vi.mocked(checkOut).mock.calls[0][1]).toEqual(vi.mocked(checkOut).mock.calls[1][1])
})
it('allows only a server-proven historic missed-check-in exception to show check-in',async()=>{
  vi.mocked(getWorkPack).mockResolvedValue({...pack,visit:{...pack.visit,status:'EXCEPTION'},execution:{...execution,checkedInAt:null,allowedActions:['CHECK_IN']}})
  mount();await screen.findByRole('button',{name:'Check in and start service'})
  expect(screen.queryByText(/Execution paused/)).toBeNull()
  expect(screen.getByText(/previous missed-check-in alert/)).toBeInTheDocument()
  expect(screen.queryByRole('button',{name:'Check out'})).toBeNull()
})
