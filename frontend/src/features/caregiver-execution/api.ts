import { api } from '../../shared/api/client'
export type CommandResult = { visitId: number; visitVersion: number; savedState: string | null; taskId: number | null; replayed: boolean }
export type CommandIdentity = { expectedVersion: number; clientRequestId: string }
export type CheckInInput = CommandIdentity & { locationSource: 'GPS' | 'MANUAL_LOCATION_NOTE'; latitude?: number; longitude?: number; accuracy?: number; locationNote?: string; clientCapturedAt?: string }
export type TaskInput = CommandIdentity & { status: 'DONE' | 'SKIPPED' | 'REFUSED'; outcome: string; caregiverNote: string }
async function write(path: string, input: unknown, signal?: AbortSignal) {
  await api('/auth/csrf', { signal })
  return api<CommandResult>(path, { method: 'POST', body: JSON.stringify(input), signal })
}
export function checkIn(id: number, input: CheckInInput, signal?: AbortSignal) { return write(`/visits/${id}/check-in`, input, signal) }
export function completeTask(id: number, task: number, input: TaskInput, signal?: AbortSignal) { return write(`/visits/${id}/tasks/${task}/complete`, input, signal) }

export type HealthFlag = 'NO_CONCERN' | 'ATTENTION' | 'MEDICAL_REVIEW'
export type HealthInput = CommandIdentity & { systolic: number | null; diastolic: number | null; pulse: number | null; temperature: number | null; healthFlag: HealthFlag; healthNote: string | null }
export type HealthRecord = { id: number; visitId: number; healthFlag: HealthFlag; healthNote: string | null; recordedAt: string; readings: { metric: string; value: number; unit: string }[] }
export type HealthPage = { items: HealthRecord[]; page: number; size: number; total: number }
export type HealthResult = { record: HealthRecord; visitVersion: number; replayed: boolean }
export async function saveHealthRecord(id: number, input: HealthInput, signal?: AbortSignal) {
  await api('/auth/csrf', { signal })
  return api<HealthResult>(`/visits/${id}/health-records`, { method: 'POST', body: JSON.stringify(input), signal })
}
export function getHealthRecords(id: number, page = 0, signal?: AbortSignal) {
  return api<HealthPage>(`/visits/${id}/health-records?page=${page}&size=10`, { signal })
}
