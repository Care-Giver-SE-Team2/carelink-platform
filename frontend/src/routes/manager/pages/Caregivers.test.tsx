import { cleanup, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'

import Caregivers from './Caregivers'
import * as authApi from '../../../features/auth/api'
import * as incidentsApi from '../../../features/incidents/api'
import * as profileApi from '../../../shared/api/profile'
import type { ElderListItem } from '../../../shared/api/profile'
import * as store from '../data/caregiverApplications'
import type { ApprovedApplicant, CaregiverApplication } from '../data/caregiverApplications'
import { certRow } from '../lib/certRow.fixture'

let pending: CaregiverApplication[]
let approved: ApprovedApplicant[]

function application(overrides: Partial<CaregiverApplication>): CaregiverApplication {
  return {
    id: 7,
    fullName: 'Lim Hui Min',
    username: 'lim.huimin',
    phone: '+65 9234 1180',
    sector: 'AMK',
    dialects: 'Hokkien,Mandarin',
    submittedAt: '2026-10-09T02:12:00Z',
    certificates: [
      {
        id: 71,
        credentialTypeName: 'First aid',
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
      fullName: 'Mohamed Faizal bin Rahman',
      username: 'faizal.rahman',
      phone: null,
      sector: 'TPY',
      dialects: 'Malay',
      submittedAt: '2026-10-08T01:00:00Z',
      certificates: [],
    }),
  ]
  approved = []
  vi.spyOn(incidentsApi, 'listIncidentQueue').mockImplementation(async ({ size }) => ({ items: [], page: 0, size, totalElements: 0 }))
  vi.spyOn(authApi, 'getCurrentUser').mockResolvedValue({ id: 1, username: 'tml', displayName: 'Tan Mei Ling', roles: ['MANAGER'] })
  vi.spyOn(profileApi, 'fetchElderList').mockResolvedValue([elder])
  vi.spyOn(profileApi, 'fetchCaregivers').mockResolvedValue([
    { id: 114, fullName: 'Devi Raman', sector: 'AMK', dialects: 'Tamil,English', status: 'AVAILABLE', assignable: true },
    { id: 120, fullName: 'Ong Wei Jie', sector: 'TPY', dialects: null, status: 'ONBOARDING', assignable: false },
  ])
  vi.spyOn(profileApi, 'fetchCredentialRegister').mockResolvedValue([
    certRow({ id: 1, caregiverId: 114, state: 'PUBLISHED', expiryDate: '2028-08-27', daysUntilExpiry: 688 }),
    certRow({ id: 2, caregiverId: 114, state: 'REMINDED', credentialTypeName: 'Manual handling', expiryDate: '2026-10-21', daysUntilExpiry: 12 }),
    certRow({ id: 3, caregiverId: 120, state: 'SUBMITTED', credentialTypeName: 'Dementia care' }),
  ])
  vi.spyOn(store, 'fetchCaregiverApplications').mockImplementation(async () => [...pending])
  vi.spyOn(store, 'fetchApprovedApplicants').mockImplementation(async () => [...approved])
})

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

it('lists pending applications above every caregiver and opens the newest application', async () => {
  renderPage()

  const applications = await screen.findByRole('table', { name: 'Caregiver applications' })
  const appRows = within(applications).getAllByRole('row').slice(1)
  expect(appRows).toHaveLength(2)
  expect(appRows[0]).toHaveTextContent('Lim Hui Min')
  expect(appRows[0]).toHaveTextContent('lim.huimin')
  expect(appRows[0]).toHaveTextContent('Hokkien, Mandarin')
  expect(appRows[1]).toHaveTextContent('NONE')
  expect(appRows[0]).toHaveAttribute('aria-selected', 'true')
  expect(screen.getByText('2 waiting')).toBeInTheDocument()

  const caregivers = await screen.findByRole('table', { name: 'Caregivers' })
  const cgRows = within(caregivers).getAllByRole('row').slice(1)
  expect(cgRows[0]).toHaveTextContent('Devi Raman')
  expect(cgRows[0]).toHaveTextContent('CGV-0114')
  expect(cgRows[0]).toHaveTextContent('2 valid · 1 expiring')
  expect(cgRows[0]).toHaveTextContent('AVAILABLE')
  expect(cgRows[1]).toHaveTextContent('1 to review')
  expect(cgRows[1]).toHaveTextContent('ONBOARDING')

  const nav = screen.getByRole('link', { name: /Caregivers/ })
  expect(nav).toHaveAttribute('aria-current', 'page')
  expect(within(nav).getByText('2')).toBeInTheDocument()

  const detail = screen.getByRole('complementary', { name: 'Application detail' })
  expect(within(detail).getByRole('heading', { name: 'Lim Hui Min' })).toBeInTheDocument()
  expect(within(detail).getByText('Caregiver application · #7')).toBeInTheDocument()
  expect(within(detail).getByText('Certificates · 2')).toBeInTheDocument()
  expect(within(detail).getByRole('button', { name: 'Open scan first-aid-certificate.pdf' })).toBeInTheDocument()
  expect(within(detail).getByRole('img', { name: 'No scan for dementia-care.jpg' })).toBeInTheDocument()
  expect(within(detail).getByText('SRC-FA-73310')).toBeInTheDocument()
  expect(within(detail).getByText('No expiry')).toBeInTheDocument()
  expect(within(detail).getByText(/publishes the 2 certificates above/)).toBeInTheDocument()
})

it('warns that an application without certificates leaves the caregiver onboarding', async () => {
  renderPage()
  await userEvent.click(await screen.findByText('Mohamed Faizal bin Rahman'))

  const detail = screen.getByRole('complementary', { name: 'Application detail' })
  expect(within(detail).getByText('None uploaded.')).toBeInTheDocument()
  expect(within(detail).getByText('not given')).toBeInTheDocument() // no mobile
  expect(within(detail).getByText(/stay onboarding, and can't be rostered/)).toBeInTheDocument()
})

it('approves an application, then opens the next one and confirms what changed', async () => {
  const approve = vi.spyOn(store, 'approveCaregiverApplication').mockImplementation(async (id) => {
    const [answered] = pending.filter((a) => a.id === id)
    pending = pending.filter((a) => a.id !== id)
    approved = [...approved, { ...answered, caregiverId: 9000 + id, approvedAt: '2026-10-09T05:00:00Z' }]
    return { caregiverId: 9000 + id }
  })
  renderPage()

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.type(within(detail).getByRole('textbox'), 'Welcome aboard')
  await userEvent.click(within(detail).getByRole('button', { name: 'Approve' }))

  expect(approve).toHaveBeenCalledWith(7, 'Welcome aboard')
  expect(await screen.findByRole('status')).toHaveTextContent(
    'Lim Hui Min approved. Their login is active and their certificates are published.',
  )
  // The answered application stays open until the URL moves on to the next one.
  const next = await screen.findByRole('heading', { name: 'Mohamed Faizal bin Rahman' })
  expect(next.closest('aside')).toHaveAccessibleName('Application detail')

  const caregivers = screen.getByRole('table', { name: 'Caregivers' })
  const row = await within(caregivers).findByText('Lim Hui Min')
  expect(row.closest('[role="row"]')).toHaveTextContent('2 valid')
  expect(row.closest('[role="row"]')).toHaveTextContent('AVAILABLE')
})

it('opens the new caregiver once the last application is approved', async () => {
  pending = [pending[0]]
  vi.spyOn(store, 'approveCaregiverApplication').mockImplementation(async (id) => {
    const [answered] = pending
    pending = []
    approved = [{ ...answered, caregiverId: 9000 + id, approvedAt: '2026-10-09T05:00:00Z' }]
    return { caregiverId: 9000 + id }
  })
  renderPage()

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.click(within(detail).getByRole('button', { name: 'Approve' }))

  const panel = await screen.findByRole('complementary', { name: 'Caregiver detail' })
  expect(await within(panel).findByRole('heading', { name: 'Lim Hui Min' })).toBeInTheDocument()
  expect(within(panel).getByText('Caregiver · CGV-9007')).toBeInTheDocument()
  expect(within(panel).getByText(/approved from an application/)).toBeInTheDocument()
  expect(screen.getByText('No applications waiting. New ones from the caregiver app appear here.')).toBeInTheDocument()
})

it('needs a message to decline', async () => {
  const decline = vi.spyOn(store, 'declineCaregiverApplication').mockImplementation(async (id) => {
    pending = pending.filter((a) => a.id !== id)
  })
  renderPage()

  const detail = await screen.findByRole('complementary', { name: 'Application detail' })
  await userEvent.click(within(detail).getByRole('button', { name: 'Decline' }))
  expect(decline).not.toHaveBeenCalled()
  expect(within(detail).getByText('Tell Lim Hui Min why')).toBeInTheDocument()

  await userEvent.type(within(detail).getByRole('textbox'), 'First aid scan is unreadable')
  await userEvent.click(within(detail).getByRole('button', { name: 'Decline' }))

  expect(decline).toHaveBeenCalledWith(7, 'First aid scan is unreadable')
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

  // The application list keeps its rows, but none is open.
  const applications = screen.getByRole('table', { name: 'Caregiver applications' })
  for (const row of within(applications).getAllByRole('row').slice(1)) {
    expect(row).not.toHaveAttribute('aria-selected', 'true')
  }
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
  renderPage()

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
