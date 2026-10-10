import { api } from './client'

export type ElderRegistrationRequest = { fullName: string; username: string; password: string }
export type ElderRegistrationResponse = { userId: number; username: string }

export function registerElder(request: ElderRegistrationRequest): Promise<ElderRegistrationResponse> {
  return api<ElderRegistrationResponse>('/elder-registrations', {
    method: 'POST',
    body: JSON.stringify(request),
  })
}
