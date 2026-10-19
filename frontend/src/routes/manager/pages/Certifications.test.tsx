import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import Certifications from './Certifications'
import * as authApi from '../../../features/auth/api'
import * as incidentsApi from '../../../features/incidents/api'
import * as profileApi from '../../../shared/api/profile'
import type { CredentialRegisterRow } from '../../../shared/api/profile'
import * as rosteringApi from '../../../shared/api/rostering'
import { certRow } from '../lib/certRow.fixture'

let register: CredentialRegisterRow[]

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

beforeEach(() => {
  register = [
    certRow({ id: 1 }),
    certRow({
      id: 2,
      caregiverId: 120,
      caregiverName: 'Rosnah Binte Ali',
      credentialTypeName: 'Dementia care',
      renewal: false,
      watchedExpiry: null,
      daysUntilExpiry: null,
      expiring: false,
      replacesId: null,
      replacesExpiryDate: null,
    }),
    certRow({ id: 3, caregiverName: 'Nur Aisyah', credentialTypeName: 'Manual handling', state: 'REMINDED', renewal: false, daysUntilExpiry: 21, replacesId: null }),
    certRow({ id: 4, caregiverName: 'Siti Rahmah', state: 'PUBLISHED', renewal: false, daysUntilExpiry: 245, expiring: false, replacesId: null }),
  ]
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({ items: [], page: 0, size, totalElements: 0 }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchCredentialRegister').mockImplementation(async () => register)
  vi.spyOn(profileApi, 'fetchCredentialDocuments').mockResolvedValue([])
  vi.spyOn(rosteringApi, 'fetchVisitsAtRisk').mockResolvedValue([
    { credentialId: 1, visitsAtRisk: 18 },
    { credentialId: 2, visitsAtRisk: 0 },
    { credentialId: 3, visitsAtRisk: 24 },
    { credentialId: 4, visitsAtRisk: null },
  ])
})

/** The detail panel once the register has loaded and opened `title`. */
async function panelShowing(title: string) {
  await screen.findByText(title)
  return screen.getByRole('complementary', { name: 'Certification detail' })
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/manager/certifications" element={<Certifications />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('opens on the review queue with the first submission in the panel', async () => {
  renderAt('/manager/certifications')

  const table = await screen.findByRole('table', { name: 'Certifications' })
  expect(within(table).getAllByRole('row')).toHaveLength(3) // header + two submissions
  expect(within(table).getByText('First aid · renewal')).toBeInTheDocument()
  expect(within(table).getByText('Dementia care · new')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'TO REVIEW · 2' })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.getByRole('button', { name: 'EXPIRING · 2' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'ALL · 4' })).toBeInTheDocument()
  // No visits at risk shows a dash, not 0 — next to the dash for no expiry.
  expect(await within(table).findByText('18')).toBeInTheDocument()
  const newCert = within(table).getByText('Dementia care · new').closest('[role="row"]') as HTMLElement
  expect(within(newCert).getAllByText('—')).toHaveLength(2)
  expect(within(newCert).queryByText('0')).not.toBeInTheDocument()

  const panel = await panelShowing('Devi Raman — first aid renewal')
  expect(within(panel).getByText('CGV-0114 · submitted 27 Aug 21:04 from caregiver app')).toBeInTheDocument()
  expect(within(panel).getByText('first aid · expires 09 Sep 2026')).toBeInTheDocument()
  expect(await within(panel).findByText('18 visits at risk')).toBeInTheDocument()
  expect(within(panel).getByText('cleared')).toBeInTheDocument()
  expect(within(panel).getByText('retained')).toBeInTheDocument()
  expect(within(panel).getByText('28 Jul 2028')).toBeInTheDocument()
})

it('shows every uploaded file of the submission and opens one full size', async () => {
  const file = (id: number, fileName: string, contentType: string, contentAvailable = true) => ({
    id, credentialId: 1, fileName, contentType, byteSize: 1024, uploadedAt: '2026-08-27T21:04:00', contentAvailable,
    contentUrl: `/api/credentials/1/documents/${id}/content`,
  })
  const documents = vi.spyOn(profileApi, 'fetchCredentialDocuments').mockResolvedValue([
    file(7, 'front.png', 'image/png'),
    file(8, 'back.pdf', 'application/pdf'),
    file(9, 'lost.jpg', 'image/jpeg', false),
  ])
  renderAt('/manager/certifications')

  const panel = await panelShowing('Devi Raman — first aid renewal')
  await userEvent.click(await within(panel).findByRole('button', { name: 'Open scan front.png' }))
  expect(documents).toHaveBeenCalledWith(1, expect.anything())
  expect(within(panel).getByRole('button', { name: 'Open scan back.pdf' })).toBeInTheDocument()
  // A file the store can't find is shown, but can't be opened.
  expect(within(panel).getByRole('img', { name: 'No scan for lost.jpg' })).toBeInTheDocument()

  const viewer = screen.getByRole('dialog')
  expect(within(viewer).getByRole('img', { name: 'Devi Raman — first aid renewal — front.png' })).toHaveAttribute(
    'src',
    '/api/credentials/1/documents/7/content',
  )
})

it('keeps the striped stand-in when no file was uploaded', async () => {
  renderAt('/manager/certifications')

  const panel = await panelShowing('Devi Raman — first aid renewal')
  expect(within(panel).getByRole('img', { name: 'Certificate scan not available' })).toBeInTheDocument()
})

it('shows a published row read-only, without the review actions', async () => {
  renderAt('/manager/certifications?filter=all')

  const table = await screen.findByRole('table', { name: 'Certifications' })
  expect(within(table).getByText('8 months')).toBeInTheDocument()
  await userEvent.click(within(table).getByText('Siti Rahmah'))

  const panel = screen.getByRole('complementary', { name: 'Certification detail' })
  expect(within(panel).getByText('Siti Rahmah — first aid')).toBeInTheDocument()
  expect(within(panel).queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument()
  expect(within(panel).queryByText('Effect of publishing')).not.toBeInTheDocument()
})

it('publishes and moves on to the next submission', async () => {
  const publish = vi.spyOn(profileApi, 'publishCredential').mockImplementation(async () => {
    register = register.map((row) => (row.id === 1 ? { ...row, state: 'PUBLISHED' } : row))
  })
  renderAt('/manager/certifications')

  const panel = await panelShowing('Devi Raman — first aid renewal')
  await userEvent.click(within(panel).getByRole('button', { name: 'Publish' }))

  expect(publish).toHaveBeenCalledWith(1)
  await waitFor(() =>
    expect(screen.getByRole('complementary', { name: 'Certification detail' })).toHaveTextContent(
      'Rosnah Binte Ali — dementia care certificate',
    ),
  )
  expect(screen.getByRole('button', { name: 'TO REVIEW · 1' })).toBeInTheDocument()
})

it('needs a reason to reject', async () => {
  const reject = vi.spyOn(profileApi, 'rejectCredential').mockResolvedValue(undefined)
  renderAt('/manager/certifications')

  const panel = await panelShowing('Devi Raman — first aid renewal')
  await userEvent.click(within(panel).getByRole('button', { name: 'Reject' }))
  const dialog = screen.getByRole('dialog')
  const confirm = within(dialog).getByRole('button', { name: 'Reject' })
  expect(confirm).toBeDisabled()

  await userEvent.type(within(dialog).getByLabelText('Reason (required)'), 'Issuer is not accredited')
  await userEvent.click(confirm)

  expect(reject).toHaveBeenCalledWith(1, 'Issuer is not accredited')
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
})

it('shows why a publish failed in the panel', async () => {
  vi.spyOn(profileApi, 'publishCredential').mockRejectedValue(new Error('Credential 1 is PUBLISHED and no longer waiting for review'))
  renderAt('/manager/certifications')

  const panel = await panelShowing('Devi Raman — first aid renewal')
  await userEvent.click(within(panel).getByRole('button', { name: 'Publish' }))

  expect(await within(panel).findByRole('alert')).toHaveTextContent('no longer waiting for review')
})
