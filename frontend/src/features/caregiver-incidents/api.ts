import { api } from '../../shared/api/client'

export type IncidentReport = { id: number; visitId: number; category: string; severity: string; description: string; status: string; reportedAt: string; respondBy: string | null; resolvedAt: string | null }
export type ReportView = { report: IncidentReport; clientRequestId: string | null }
export type ReportResult = ReportView & { visitVersion: number; replayed: boolean }
export type ReportPage = { items: ReportView[]; page: number; size: number; totalElements: number }
export type ReportInput = { visitId: number; category: string; severity: string; description: string; expectedVersion: number; clientRequestId: string }

export function listReports(page = 0, signal?: AbortSignal) { return api<ReportPage>(`/caregivers/me/incidents?page=${page}&size=20`, { signal }) }
export function getReport(id: string, signal?: AbortSignal) { return api<ReportView>('/caregivers/me/incidents/' + encodeURIComponent(id), { signal }) }
export async function reportIncident(input: ReportInput, signal?: AbortSignal) {
  await api('/auth/csrf', { signal })
  return api<ReportResult>('/incidents', { method: 'POST', body: JSON.stringify(input), signal })
}
