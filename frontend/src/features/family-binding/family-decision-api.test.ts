import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => vi.fn())
vi.mock('../../shared/api/client', () => ({ api: request }))
import { getIncomingFamilyBindings, decideIncomingFamilyBinding } from './api'

describe('family binding decision API', () => {
  beforeEach(() => request.mockReset())
  it('loads incoming requests', async () => {
    request.mockResolvedValueOnce([])
    await expect(getIncomingFamilyBindings()).resolves.toEqual([])
    expect(request).toHaveBeenCalledWith('/family/family-bindings')
  })
  it('posts confirmation and rejection for the selected request', async () => {
    request.mockResolvedValue({})
    await decideIncomingFamilyBinding(42, true)
    await decideIncomingFamilyBinding(42, false)
    expect(request).toHaveBeenNthCalledWith(1, '/family/family-bindings/42/decision', { method: 'POST', body: JSON.stringify({ approve: true }) })
    expect(request).toHaveBeenNthCalledWith(2, '/family/family-bindings/42/decision', { method: 'POST', body: JSON.stringify({ approve: false }) })
  })
})
