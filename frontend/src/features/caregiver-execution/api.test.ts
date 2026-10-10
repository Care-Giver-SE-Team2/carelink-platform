import { expect,it,vi } from 'vitest'
import { api } from '../../shared/api/client'
import { checkIn,checkOut,completeTask,getHealthRecords,saveHealthRecord } from './api'
vi.mock('../../shared/api/client',()=>({api:vi.fn()}))
it('checks out once with CSRF and only the immutable command identity',async()=>{
  vi.clearAllMocks();const signal=new AbortController().signal
  const input={expectedVersion:7,clientRequestId:'checkout-key'}
  await checkOut(3,input,signal)
  expect(api).toHaveBeenNthCalledWith(1,'/auth/csrf',{signal})
  expect(api).toHaveBeenNthCalledWith(2,'/visits/3/check-out',{method:'POST',body:JSON.stringify(input),signal})
  expect(api).toHaveBeenCalledTimes(2)
  vi.clearAllMocks()
})
it('sends caregiver commands once with CSRF bootstrap and the exact visit/task route',async()=>{
  const signal=new AbortController().signal
  const input={expectedVersion:0,clientRequestId:'uuid',locationSource:'MANUAL_LOCATION_NOTE' as const,locationNote:'doorway'}
  await checkIn(3,input,signal);expect(api).toHaveBeenNthCalledWith(1,'/auth/csrf',{signal});expect(api).toHaveBeenNthCalledWith(2,'/visits/3/check-in',{method:'POST',body:JSON.stringify(input),signal})
  const task={expectedVersion:1,clientRequestId:'uuid-2',status:'REFUSED' as const,outcome:'',caregiverNote:'Factual reason'}
  await completeTask(3,8,task,signal);expect(api).toHaveBeenNthCalledWith(4,'/visits/3/tasks/8/complete',{method:'POST',body:JSON.stringify(task),signal})
})
it('records a measurement atomically with CSRF and reads a bounded history page',async()=>{
  vi.clearAllMocks()
  const signal=new AbortController().signal
  const input={expectedVersion:1,clientRequestId:'health-key',systolic:123,diastolic:81,pulse:73,temperature:36.7,healthFlag:'ATTENTION' as const,healthNote:'Factual observation'}
  await saveHealthRecord(3,input,signal)
  expect(api).toHaveBeenNthCalledWith(1,'/auth/csrf',{signal})
  expect(api).toHaveBeenNthCalledWith(2,'/visits/3/health-records',{method:'POST',body:JSON.stringify(input),signal})
  await getHealthRecords(3,2,signal)
  expect(api).toHaveBeenLastCalledWith('/visits/3/health-records?page=2&size=10',{signal})
})
