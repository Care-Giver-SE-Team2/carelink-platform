import { describe, expect, it } from 'vitest'
import type { ValueAddedService } from '../../../features/value-added-services/types'
import { formatDuration, validateService } from './serviceCatalogue'

const catalogue: ValueAddedService[] = [
  { id: 1, name: 'Hospital escort', description: null, durationMinutes: 180, status: 'AVAILABLE' },
  { id: 2, name: 'Laundry', description: null, durationMinutes: 60, status: 'UNAVAILABLE' },
]

describe('formatDuration', () => {
  it('reads as minutes under an hour, and hours and minutes after', () => {
    expect(formatDuration(45)).toBe('45 min')
    expect(formatDuration(120)).toBe('2 h')
    expect(formatDuration(90)).toBe('1 h 30')
    expect(formatDuration(65)).toBe('1 h 05')
  })
})

describe('validateService', () => {
  it('accepts a new name with a length', () => {
    expect(validateService({ name: 'Medication collection', description: '', durationMinutes: 60 }, catalogue)).toEqual({})
  })

  it('needs a name and a length within twelve hours', () => {
    const errors = validateService({ name: '  ', description: '', durationMinutes: null }, catalogue)
    expect(errors.name).toBe('Enter a name.')
    expect(errors.durationMinutes).toBe('Enter how many minutes it lasts.')
    expect(validateService({ name: 'X', description: '', durationMinutes: 721 }, catalogue).durationMinutes).toMatch(/12 hours/)
  })

  it('refuses a name already in the catalogue, whatever its case', () => {
    expect(validateService({ name: ' hospital ESCORT ', description: '', durationMinutes: 60 }, catalogue).name).toBe(
      'There is already a service called "Hospital escort".',
    )
  })

  it('points to an unavailable service of that name instead of adding it again', () => {
    expect(validateService({ name: 'laundry', description: '', durationMinutes: 60 }, catalogue).name).toMatch(
      /unavailable. Make it available again/,
    )
  })

  it('does not check the name when editing', () => {
    expect(validateService({ name: 'Hospital escort', description: '', durationMinutes: 240 }, null)).toEqual({})
  })
})
