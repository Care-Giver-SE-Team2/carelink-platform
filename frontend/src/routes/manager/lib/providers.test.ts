import { describe, expect, it } from 'vitest'
import { healthTag, listLabel, providerRef } from './providers'

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

describe('healthTag', () => {
  const tag = (health: { delivered: number; retrying: number; failed: number }, suspended = false) =>
    healthTag({ suspended, health }).label

  it('says nothing has been sent rather than calling a new provider healthy', () => {
    expect(tag({ delivered: 0, retrying: 0, failed: 0 })).toBe('NO DELIVERIES YET')
  })

  it('is healthy once deliveries arrive without trouble', () => {
    expect(tag({ delivered: 3, retrying: 0, failed: 0 })).toBe('HEALTHY')
  })

  it('puts suspension, then failures, then retries first', () => {
    expect(tag({ delivered: 0, retrying: 1, failed: 2 }, true)).toBe('SUSPENDED')
    expect(tag({ delivered: 0, retrying: 1, failed: 2 })).toBe('2 FAILED')
    expect(tag({ delivered: 0, retrying: 1, failed: 0 })).toBe('1 RETRYING')
  })
})
