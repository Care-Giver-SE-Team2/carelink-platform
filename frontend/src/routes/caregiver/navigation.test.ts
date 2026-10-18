import { expect, it } from 'vitest'
import { historyFilters, historySearch, scheduleSearch } from './navigation'

it('preserves only valid calendar dates and rejects inverted ranges', () => {
  expect(scheduleSearch(new URLSearchParams('dateFrom=2026-10-09&dateTo=2026-10-15&privateNote=secret'))).toBe('?dateFrom=2026-10-09&dateTo=2026-10-15')
  expect(scheduleSearch(new URLSearchParams('dateFrom=2026-02-30&dateTo=garbage'))).toBe('')
  expect(scheduleSearch(new URLSearchParams('dateFrom=2026-10-15&dateTo=2026-10-09'))).toBe('')
})
it('validates history inputs and serializes only known filters', () => {
  const filters = historyFilters(new URLSearchParams('month=2026-09&status=VERIFIED&page=2&note=secret'))
  expect(historySearch(filters)).toBe('?month=2026-09&page=2&status=VERIFIED')
  const invalid = historyFilters(new URLSearchParams('month=2026-13&status=ADMIN&page=999999999999'))
  expect(invalid).toMatchObject({ page: 0, status: '' })
  expect(invalid.month).toMatch(/^\d{4}-(0[1-9]|1[0-2])$/)
})
