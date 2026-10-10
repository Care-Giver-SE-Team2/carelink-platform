import { describe, expect, it } from 'vitest'
import { formatReceived } from './applications'

describe('formatReceived', () => {
  it('shows the submission in Singapore time', () => {
    expect(formatReceived('2026-09-05T02:12:00Z')).toBe('5 Sep 10:12')
  })
})
