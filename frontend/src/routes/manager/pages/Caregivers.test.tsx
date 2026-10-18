import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import Caregivers from './Caregivers'
import * as authApi from '../../../features/auth/api'
import * as incidentsApi from '../../../features/incidents/api'
import * as profileApi from '../../../shared/api/profile'
import type { CaregiverApplication, CaregiverOption, ElderListItem } from '../../../shared/api/profile'
import { certRow } from '../lib/certRow.fixture'

let pending: CaregiverApplication[]
let caregivers: CaregiverOption[]

function application(overrides: Partial<CaregiverApplication>): CaregiverApplication {
  return {
    id: 7,
    caregiverId: 301,
    fullName: 'Lim Hui Min',
    username: 'lim.huimin',
    phone: '+65 9234 1180',
    requestedSector: 'AMK',
    roundNumber: 1,
    version: 3,
    dialects: 'Hokkien,Mandarin',
    submittedAt: '2026-10-09T02:12:00Z',
    certificates: [
      {
        id: 71,
        credentialTypeName: 'First aid',
        state: 'SUBMITTED',
        certificateNo: 'SRC-FA-73310',
        issuingBody: 'Singapore Red Cross',
        validFrom: null,
        expiryDate: '2028-06-30',
        fileName: 'first-aid-certificate.pdf',
        scanUrl: '/scans/first-aid-certificate.pdf',
        scanContentType: 'application/pdf',
      },
      {
        id: 72,
        credentialTypeName: 'Dementia care',
        state: 'SUBMITTED',
        certificateNo: 'DSG-24418',
        issuingBody: 'Dementia Singapore',
        validFrom: null,
        expiryDate: null,
        fileName: 'dementia-care.jpg',
        scanUrl: null,
        scanContentType: null,
      },
    ],
    ...overrides,
  }
}

const elder: ElderListItem = {
  id: 3,
  fullName: 'Tan Bee Choo',
  dateOfBirth: null,
  address: null,
  sector: 'AMK',
  planStatus: 'published',
  planVersion: 1,
  nextVisitDate: null,
  primaryCaregiverId: 114,
  primaryCaregiverName: 'Devi Raman',
  primaryCaregiverAssignedAt: '2026-09-01T00:00:00Z',
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

beforeEach(() => {
  pending = [
    application({}),
    application({
      id: 6,
      caregiverId: 306,
      fullName: 'Mohamed Faizal bin Rahman',
      username: 'faizal.rahman',
      phone: null,
      requestedSector: 'TPY',
      dialects: 'Malay',
      submittedAt: '2026-10-08T01:00:00Z',
      certificates: [],
    }),
  ]
  caregivers = [
    { id: 114, fullName: 'Devi Raman', sector: 'AMK', dialects: 'Tamil,English', status: 'AVAILABLE', assignable: true },
    { id: 120, fullName: 'Ong Wei Jie', sector: 'TPY', dialects: null, status: 'ONBOARDING', assignable: false },
  ]
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({ items: [], page: 0, size, totalElements: 0 }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchElderList').mockResolvedValue([elder])
  vi.spyOn(profileApi, 'fetchCaregivers').mockImplementation(async () => [...caregivers])
  vi.spyOn(profileApi, 'fetchCredentialRegister').mockResolvedValue([
    certRow({ id: 1, caregiverId: 114, state: 'PUBLISHED', expiryDate: '2028-08-27', daysUntilExpiry: 688 }),
    certRow({ id: 2, caregiverId: 114, state: 'REMINDED', credentialTypeName: 'Manual handling', expiryDate: '2026-10-21', daysUntilExpiry: 12 }),
    certRow({ id: 3, caregiverId: 120, state: 'SUBMITTED', credentialTypeName: 'Dementia care' }),
  ])
  vi.spyOn(profileApi, 'fetchCaregiverApplications').mockImplementation(async () => ({
    items: [...pending],
    page: 0,
    size: 100,
    total: pending.length,
  }))
})

const APPLICATIONS = '/manager/caregivers?view=applications'

/** What the server does on approval: the application leaves the queue and its caregiver can work. */
function admit(id: number) {
  const [answered] = pending.filter((a) => a.id === id)
  pending = pending.filter((a) => a.id !== id)
  caregivers = [
    ...caregivers,
    { id: answered.caregiverId, fullName: answered.fullName, sector: null, dialects: answered.dialects, status: 'AVAILABLE', assignable: true },
  ]
  return { applicationId: id, caregiverId: answered.caregiverId, status: 'APPROVED' as const }
}

function renderPage(entry = '/manager/caregivers') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path="/manager/caregivers" element={<Caregivers />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

it('opens on every caregiver even while applications wait, with the first one open', async () => {
  renderPage()

  const caregivers = await screen.findByRole('table', { name: 'Caregivers' })
  const cgRows = within(caregivers).getAllByRole('row').slice(1)
  expect(cgRows[0]).toHaveTextContent('Devi Raman')
  expect(cgRows[0]).toHaveTextContent('CGV-0114')
  expect(cgRows[0]).toHaveTextContent('2 valid · 1 expiring')
  expect(cgRows[0]).toHaveTextContent('AVAILABLE')
  expect(cgRows[1]).toHaveTextContent('1 to review')
  expect(cgRows[1]).toHaveTextContent('ONBOARDING')
  expect(cgRows[0]).toHaveAttribute('aria-selected', 'true')
  expect(screen.getByRole('button', { name: 'Caregivers · 2' })).toHaveAttribute('aria-pressed', 'true')
  expect(await screen.findByRole('button', { name: 'Applications · 2' })).toHaveAttribute('aria-pressed', 'false')
  expect(screen.queryByRole('table', { name: 'Caregiver applications' })).not.toBeInTheDocument()

  const nav = screen.getByRole('link', { name: /Caregivers/ })
  expect(nav).toHaveAttribute('aria-current', 'page')
  expect(within(nav).getByText('2')).toBeInTheDocument()

  const panel = screen.getByRole('complementary', { name: 'Caregiver detail' })
  expect(within(panel).getByRole('heading', { name: 'Devi Raman' })).toBeInTheDocument()
})

it('switches to the applications, opening the newest one, and back', async () => {
  renderPage()
  await screen.findByRole('table', { name: 'Caregivers' })

  await userEvent.click(await screen.findByRole('button', { name: 'Applications · 2' }))

  const applications = await screen.findByRole('table', { name: 'Caregiver applications' })
  const appRows = within(applications).getAllByRole('row').slice(1)
  expect(appRows).toHaveLength(2)
  expect(appRows[0]).toHaveTextContent('Lim Hui Min')
  expect(appRows[0]).toHaveTextContent('lim.huimin')
  expect(appRows[0]).toHaveTextContent('Hokkien, Mandarin')
  expect(appRows[1]).toHaveTextContent('NONE')
  expect(appRows[0]).toHaveAttribute('aria-selected', 'true')
  expect(screen.queryByRole('table', { name: 'Caregivers' })).not.toBeInTheDocument()

  const detail = screen.getByRole('complementary', { name: 'Application detail' })
  expect(within(detail).getByRole('heading', { name: 'Lim Hui Min' })).toBeInTheDocument()
  expect(within(detail).getByText('Caregiver application · #7')).toBeInTheDocument()
  expect(within(detail).getByText('Certificates · 2')).toBeInTheDocument()
  expect(within(detail).getByRole('button', { name: 'Open scan first-aid-certificate.pdf' })).toBeInTheDocument()
  expect(within(detail).getByRole('img', { name: 'No scan for dementia-care.jpg' })).toBeInTheDocument()
  expect(within(detail).getByText('SRC-FA-73310')).toBeInTheDocument()
  expect(within(detail).getByText('No expiry')).toBeInTheDocument()
  expect(within(detail).getByRole('link', { name: 'First aid' })).toHaveAttribute(
    'href',
    '/manager/certifications?filter=all&id=71',
  )
  expect(within(detail).getByText(/doesn't publish their certificates: review the 2 waiting in Certifications/)).toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: 'Caregivers · 2' }))
  expect(screen.getByRole('table', { name: 'Caregivers' })).toBeInTheDocument()
  expect(screen.getByRole('complementary', { name: 'Caregiver detail' })).toHaveTextContent('Devi Raman')
})

it('searches the caregivers by name or id, opening the first match', async () => {
  renderPage()
  await screen.findByRole('table', { name: 'Caregivers' })

  await userEvent.type(screen.getByRole('searchbox', { name: 'Search caregivers by name or ID' }), 'ong')

  const rows = within(screen.getByRole('table', { name: 'Caregivers' })).getAllByRole('row').slice(1)
  expect(rows).toHaveLength(1)
  expect(rows[0]).toHaveTextContent('Ong Wei Jie')
  expect(screen.getByRole('complementary', { name: 'Caregiver detail' })).toHaveTextContent('Ong Wei Jie')
  // The chip still counts everyone on record.
  expect(screen.getByRole('button', { name: 'Caregivers · 2' })).toBeInTheDocument()

  await userEvent.clear(screen.getByRole('searchbox'))
  await userEvent.type(screen.getByRole('searchbox'), 'CGV-0114')
  const byId = within(screen.getByRole('table', { name: 'Caregivers' })).getAllByRole('row').slice(1)
  expect(byId).toHaveLength(1)
  expect(byId[0]).toHaveTextContent('Devi Raman')
})

it('says when no caregiver matches the search, and offers no search on applications', async () => {
  renderPage('/manager/caregivers?q=zzz')

  expect(await screen.findByText('No caregivers match “zzz”.')).toBeInTheDocument()
  expect(screen.getByRole('searchbox')).toHaveValue('zzz')

  await userEvent.click(screen.getByRole('button', { name: 'Applications · 2' }))
  expect(await screen.findByRole('table', { name: 'Caregiver applications' })).toBeInTheDocument()
  expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
})

it('pages a long list of applications on its own', async () => {
  pending = Array.from({ length: 12 }, (_, i) => application({ id: 200 + i, fullName: `Applicant ${i + 1}` }))
  renderPage(APPLICATIONS)

  const applications = await screen.findByRole('table', { name: 'Caregiver applications' })
  expect(within(applications).getAllByRole('row').slice(1)).toHaveLength(10)
  await userEvent.click(screen.getByRole('button', { name: /next/i }))

  const page2 = within(screen.getByRole('table', { name: 'Caregiver applications' })).getAllByRole('row').slice(1)
  expect(page2).toHaveLength(2)
  expect(page2[0]).toHaveTextContent('Applicant 11')
  expect(screen.getByRole('complementary', { name: 'Application detail' })).toHaveTextContent('Applicant 11')
})

it('shows the page holding an application opened by id', async () => {
  pending = Array.from({ length: 12 }, (_, i) => application({ id: 200 + i, fullName: `Applicant ${i + 1}` }))
  renderPage('/manager/caregivers?application=211')

  const applications = await screen.findByRole('table', { name: 'Caregiver applications' })
  const selected = within(applications).getByText('Applicant 12').closest('[role="row"]')
  expect(selected).toHaveAttribute('aria-selected', 'true')
})

it('says when an applicant has uploaded no certificates', async () => {
  renderPage(APPLICATIONS)
  await userEvent.click(await screen.findByText('Mohamed Faizal bin Rahman'))

  const detail = screen.getByRole('complementary', { name: 'Application detail' })
  expect(within(detail).getByText('None uploaded.')).toBeInTheDocument()
  expect(within(detail).getByText('not given')).toBeInTheDocument() // no mobile
  expect(within(detail).getByText(/haven't uploaded any certificates yet/)).toBeInTheDocument()
})

it('approves an application, then opens the next one and confirms what changed', async () => {
  const approve = vi.spyOn(profileApi, 'approveCaregiverApplication').mockImplementation(async (id) => admit(id))
  renderPage(APPLICATIONS)

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.type(within(detail).getByRole('textbox'), 'Welcome aboard')
  await userEvent.click(within(detail).getByRole('button', { name: 'Approve' }))

  expect(approve).toHaveBeenCalledWith(7, 3, 'Welcome aboard')
  expect(await screen.findByRole('status')).toHaveTextContent('Lim Hui Min approved and available for rostering.')
  // The answered application stays open until the URL moves on to the next one.
  const next = await screen.findByRole('heading', { name: 'Mohamed Faizal bin Rahman' })
  expect(next.closest('aside')).toHaveAccessibleName('Application detail')

  await userEvent.click(screen.getByRole('button', { name: /^Caregivers · / }))
  const caregivers = await screen.findByRole('table', { name: 'Caregivers' })
  const row = await within(caregivers).findByText('Lim Hui Min')
  expect(row.closest('[role="row"]')).toHaveTextContent('AVAILABLE')
})

it('opens the new caregiver once the last application is approved', async () => {
  pending = [pending[0]]
  vi.spyOn(profileApi, 'approveCaregiverApplication').mockImplementation(async (id) => admit(id))
  renderPage(APPLICATIONS)

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.click(within(detail).getByRole('button', { name: 'Approve' }))

  const panel = await screen.findByRole('complementary', { name: 'Caregiver detail' })
  expect(await within(panel).findByRole('heading', { name: 'Lim Hui Min' })).toBeInTheDocument()
  expect(within(panel).getByText('Caregiver · CGV-0301')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Applications · 0' })).toHaveAttribute('aria-pressed', 'false')
  expect(screen.getByRole('button', { name: 'Caregivers · 3' })).toHaveAttribute('aria-pressed', 'true')
})

it('stays on the emptied applications once the last one is declined', async () => {
  pending = [pending[0]]
  vi.spyOn(profileApi, 'rejectCaregiverApplication').mockImplementation(async (id) => {
    pending = []
    return { applicationId: id, caregiverId: 301, status: 'REJECTED' }
  })
  renderPage(APPLICATIONS)

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.type(within(detail).getByRole('textbox'), 'Incomplete')
  await userEvent.click(within(detail).getByRole('button', { name: 'Decline' }))

  expect(
    await screen.findByText('No pending applications.'),
  ).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Applications · 0' })).toHaveAttribute('aria-pressed', 'true')
})

it('needs a message to decline', async () => {
  const decline = vi.spyOn(profileApi, 'rejectCaregiverApplication').mockImplementation(async (id) => {
    pending = pending.filter((a) => a.id !== id)
    return { applicationId: id, caregiverId: 301, status: 'REJECTED' }
  })
  renderPage(APPLICATIONS)

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.click(within(detail).getByRole('button', { name: 'Decline' }))
  expect(decline).not.toHaveBeenCalled()
  expect(within(detail).getByText('Tell Lim Hui Min why')).toBeInTheDocument()

  await userEvent.type(within(detail).getByRole('textbox'), 'First aid scan is unreadable')
  await userEvent.click(within(detail).getByRole('button', { name: 'Decline' }))

  expect(decline).toHaveBeenCalledWith(7, 3, 'First aid scan is unreadable')
  expect(await screen.findByRole('status')).toHaveTextContent("Lim Hui Min's application declined.")
})

it('shows a caregiver with their elders and certificates, linking each to Certifications', async () => {
  renderPage('/manager/caregivers?caregiver=114')

  await screen.findByRole('heading', { name: 'Devi Raman' })
  const panel = screen.getByRole('complementary', { name: 'Caregiver detail' })
  expect(within(panel).getByText('can be rostered')).toBeInTheDocument()
  expect(within(panel).getByText('Tamil, English')).toBeInTheDocument()
  expect(await within(panel).findByText('Tan Bee Choo')).toBeInTheDocument()
  expect(within(panel).getByRole('link', { name: 'Manual handling' })).toHaveAttribute(
    'href',
    '/manager/certifications?filter=all&id=2',
  )
  expect(within(panel).getByText(/· in 12 days/)).toBeInTheDocument()

  // A caregiver opened by id shows the caregivers view, not the waiting applications.
  expect(screen.getByRole('button', { name: /^Caregivers · / })).toHaveAttribute('aria-pressed', 'true')
  expect(screen.queryByRole('table', { name: 'Caregiver applications' })).not.toBeInTheDocument()
})

it('opens a scan full size, with a link to open it in a new tab', async () => {
  pending = [
    application({
      certificates: [
        { ...application({}).certificates[0] },
        {
          ...application({}).certificates[1],
          scanUrl: '/scans/dementia-care.jpg',
          scanContentType: 'image/jpeg',
        },
      ],
    }),
  ]
  renderPage(APPLICATIONS)

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.click(within(detail).getByRole('button', { name: 'Open scan dementia-care.jpg' }))

  let viewer = screen.getByRole('dialog', { name: 'Lim Hui Min — dementia care' })
  expect(within(viewer).getByRole('img', { name: 'Lim Hui Min — dementia care — dementia-care.jpg' })).toHaveAttribute(
    'src',
    '/scans/dementia-care.jpg',
  )
  const newTab = within(viewer).getByRole('link', { name: 'Open in new tab' })
  expect(newTab).toHaveAttribute('href', '/scans/dementia-care.jpg')
  expect(newTab).toHaveAttribute('target', '_blank')

  await userEvent.keyboard('{Escape}')
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

  // A PDF opens in the browser's own viewer.
  await userEvent.click(within(detail).getByRole('button', { name: 'Open scan first-aid-certificate.pdf' }))
  viewer = screen.getByRole('dialog', { name: 'Lim Hui Min — first aid' })
  expect(within(viewer).getByTitle('Lim Hui Min — first aid — first-aid-certificate.pdf')).toHaveAttribute(
    'src',
    '/scans/first-aid-certificate.pdf',
  )
  await userEvent.click(within(viewer).getByRole('button', { name: 'Close' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})
