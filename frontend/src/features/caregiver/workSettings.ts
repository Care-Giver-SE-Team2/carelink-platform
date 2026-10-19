import { api } from '../../shared/api/client'

export type AvailabilityWindow = { start: string; end: string; status: 'AVAILABLE' | 'UNAVAILABLE' }
export type WeeklyWindow = { weekday: string; start: string; end: string }
export type WorkPreferences = { serviceCodes: string[]; sectors: string[]; weeklyWindows: WeeklyWindow[]; maxVisitsPerDay: number | null; maxHoursPerDay: number | null }
export type WorkSettings = {
  caregiverId: number; version: number; updatedAt: string | null; dateFrom: string; dateTo: string
  preferences: WorkPreferences; availability: { date: string; windows: AvailabilityWindow[] }[]
  dataIssues: string[]; timeZone: string; uncoveredTimeMeaning: string; consumptionStatus: string
}
export type WorkSettingsView = {
  settings: WorkSettings; catalog: { serviceCodes: string[]; sectors: string[]; maxVisitsPerDay: number; maxHoursPerDay: number; serviceCatalogConfigured: boolean }
  today: string; editableUntil: string; canEdit: boolean
  bookings: { visitId: number; start: string; end: string }[]
  leave: { id: number; startDate: string; endDate: string; status: string }[]
}
export const getWorkSettings = (signal?: AbortSignal) => api<WorkSettingsView>('/caregivers/me/work-settings', { signal })
export const getAvailability = (date: string, signal?: AbortSignal) => api<WorkSettingsView>(`/caregivers/me/availability?dateFrom=${encodeURIComponent(date)}&dateTo=${encodeURIComponent(date)}`, { signal })
export const saveAvailability = (date: string, windows: AvailabilityWindow[], expectedSettingsVersion: number, clientRequestId: string, signal?: AbortSignal) =>
  api<WorkSettings>(`/caregivers/me/availability/${encodeURIComponent(date)}`, { method: 'PUT', body: JSON.stringify({ windows, expectedSettingsVersion, clientRequestId }), signal })
export const savePreferences = (preferences: WorkPreferences, expectedSettingsVersion: number, clientRequestId: string, signal?: AbortSignal) =>
  api<WorkSettings>('/caregivers/me/work-preferences', { method: 'PUT', body: JSON.stringify({ preferences, expectedSettingsVersion, clientRequestId }), signal })
