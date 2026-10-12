import { describe, expect, it } from 'vitest'
import { listLabel, providerRef } from './providers'

describe('providerRef', () => {
  it('pads the id to four digits', () => {
    expect(providerRef(109)).toBe('PRV-0109')
  })
})

describe('listLabel', () => {
  it('joins the items', () => {
    expect(listLabel(['AMK', 'BSH'])).toBe('AMK, BSH')
  })

  it('is null for an empty list', () => {
    expect(listLabel([])).toBeNull()
  })
})
