import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { FamilyBindingsPage } from './FamilyBindingsPage'

const mocks = vi.hoisted(() => ({
  load: vi.fn(),
  decide: vi.fn(),
}))
vi.mock('../../../features/family-binding/api', () => ({
  getIncomingFamilyBindings: mocks.load,
  decideIncomingFamilyBinding: mocks.decide,
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><FamilyBindingsPage /></QueryClientProvider>)
}

const pending = {
  id: 42, elderId: 1, elderName: 'Test Elder', relationship: 'SON',
  primaryContact: false, accessScope: 'FULL', status: 'PENDING_CONFIRMATION',
  confirmedAt: null, createdAt: null,
}

beforeEach(() => {
  mocks.load.mockReset()
  mocks.decide.mockReset()
  mocks.load.mockResolvedValue([pending])
  mocks.decide.mockResolvedValue({ ...pending, status: 'ACTIVE' })
})
afterEach(() => cleanup())

describe('EL04 family binding confirmation page', () => {
  it('loads and renders a pending request with the correct actions', async () => {
    renderPage()
    expect(await screen.findByText('Test Elder')).toBeInTheDocument()
    expect(screen.getByText('Relationship: SON')).toBeInTheDocument()
    expect(screen.getByText('Access: Full access')).toBeInTheDocument()
    expect(screen.getByText('Status: PENDING CONFIRMATION')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm binding' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Reject binding' })).toBeEnabled()
  })

  it('confirms a binding and reloads the updated state', async () => {
    const user = userEvent.setup()
    mocks.load.mockResolvedValueOnce([pending]).mockResolvedValueOnce([{ ...pending, status: 'ACTIVE' }])
    renderPage()
    await screen.findByText('Test Elder')
    await user.click(screen.getByRole('button', { name: 'Confirm binding' }))
    await waitFor(() => expect(mocks.decide).toHaveBeenCalledWith(42, true))
    expect(await screen.findByRole('status')).toHaveTextContent('Binding confirmed.')
    expect(await screen.findByText('Status: ACTIVE')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Confirm binding' })).not.toBeInTheDocument()
  })

  it('rejects a binding and removes decision actions', async () => {
    const user = userEvent.setup()
    mocks.load.mockResolvedValueOnce([pending]).mockResolvedValueOnce([{ ...pending, status: 'REJECTED' }])
    renderPage()
    await screen.findByText('Test Elder')
    await user.click(screen.getByRole('button', { name: 'Reject binding' }))
    await waitFor(() => expect(mocks.decide).toHaveBeenCalledWith(42, false))
    expect(await screen.findByText('Status: REJECTED')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Binding rejected.')
    expect(screen.queryByRole('button', { name: 'Reject binding' })).not.toBeInTheDocument()
  })

  it('shows the empty state when no requests exist', async () => {
    mocks.load.mockResolvedValue([])
    renderPage()
    expect(await screen.findByText('No binding requests.')).toBeInTheDocument()
  })

  it('shows a load error and supports manual retry', async () => {
    const user = userEvent.setup()
    mocks.load.mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce([pending])
    renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load family binding requests.')
    await user.click(screen.getByRole('button', { name: 'Refresh' }))
    expect(await screen.findByText('Test Elder')).toBeInTheDocument()
    expect(mocks.load).toHaveBeenCalledTimes(2)
  })

  it('shows a decision error without pretending the binding was changed', async () => {
    const user = userEvent.setup()
    mocks.decide.mockRejectedValue(new Error('conflict'))
    renderPage()
    await screen.findByText('Test Elder')
    await user.click(screen.getByRole('button', { name: 'Confirm binding' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to update binding.')
    expect(screen.getByText('Status: PENDING CONFIRMATION')).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('displays read-only access and multiple binding requests', async () => {
    mocks.load.mockResolvedValue([pending, { ...pending, id: 43, elderName: 'Another Elder', accessScope: 'READ_ONLY' }])
    renderPage()
    expect(await screen.findByText('Another Elder')).toBeInTheDocument()
    expect(screen.getByText('Access: Read-only')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: 'Confirm binding' })).toHaveLength(2)
  })

  it('disables decisions while the request is in progress', async () => {
    const user = userEvent.setup()
    let resolve!: (value: unknown) => void
    mocks.decide.mockImplementation(() => new Promise(r => { resolve = r }))
    renderPage()
    await screen.findByText('Test Elder')
    await user.click(screen.getByRole('button', { name: 'Confirm binding' }))
    expect(screen.getByRole('button', { name: 'Confirm binding' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Reject binding' })).toBeDisabled()
    resolve({})
    await waitFor(() => expect(screen.getByRole('button', { name: 'Confirm binding' })).toBeEnabled())
  })
})
