import { api } from '../../shared/api/client'

export type CredentialType = { id: number; name: string }
export type CredentialDocument = { id: number; credentialId: number; fileName: string; contentType: string; byteSize: number; uploadedAt: string; contentAvailable: boolean; contentUrl: string }
export type Credential = {
  id: number; credentialTypeId: number; credentialTypeName: string; certificateNo: string | null
  issuingBody: string | null; validFrom: string | null; expiryDate: string | null; status: string
  submittedAt: string | null; reviewNote: string | null; reviewedAt: string | null
  renewsCredentialId: number | null; resubmitsCredentialId: number | null; historical: boolean; documents: CredentialDocument[]
}
export type CredentialPage = { items: Credential[]; page: number; size: number; total: number }
export type Submission = {
  credentialTypeId: number; certificateNo: string; issuingBody: string; validFrom: string
  expiryDate: string; noExpiry: boolean; clientRequestId: string; files: File[]
}
export type Receipt = { credentialId: number; status: string; submittedAt: string; documentIds: number[] }
export const getCredentialTypes = (signal?: AbortSignal) => api<CredentialType[]>('/caregivers/me/credential-types', { signal })
export const getCredential = (id: string, signal?: AbortSignal) => api<Credential>(`/caregivers/me/credentials/${encodeURIComponent(id)}`, { signal })
export const getCredentials = (page: number, signal?: AbortSignal) => api<CredentialPage>(`/caregivers/me/credentials?page=${page}&size=20`, { signal })
export function submitCredential(action: 'NEW' | 'RENEWAL' | 'RESUBMIT', sourceId: number | null, values: Submission, signal?: AbortSignal) {
  const data = new FormData()
  data.set('credentialTypeId', String(values.credentialTypeId))
  data.set('certificateNo', values.certificateNo)
  data.set('issuingBody', values.issuingBody)
  data.set('validFrom', values.validFrom)
  data.set('noExpiry', String(values.noExpiry))
  if (!values.noExpiry) data.set('expiryDate', values.expiryDate)
  data.set('clientRequestId', values.clientRequestId)
  values.files.forEach(file => data.append('files', file))
  const suffix = action === 'NEW' ? '' : `/${sourceId}/${action === 'RENEWAL' ? 'renewals' : 'resubmissions'}`
  return api<Receipt>(`/caregivers/me/credentials${suffix}`, { method: 'POST', body: data, signal })
}
