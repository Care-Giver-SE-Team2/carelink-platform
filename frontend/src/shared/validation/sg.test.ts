import { describe, expect, it } from 'vitest'
import { ApiError } from '../api/client'
import { problemCode, serverFieldErrors } from './serverErrors'
import {
  dateOfBirthError, dateOfBirthWarning, formatSgPhone, joinDialects, normalizeName, normalizeSgPhone, parseDialects,
  personNameError, phoneError, postalCodeError,
} from './sg'

describe('Singapore phone numbers', () => {
  it.each([
    ['91234567', '+6591234567'],
    [' 9123 4567 ', '+6591234567'],
    ['+65 9123-4567', '+6591234567'],
    ['6591234567', '+6591234567'],
    ['(+65) 6123 4567', '+6561234567'],
  ])('saves %j as %s', (typed, saved) => {
    expect(normalizeSgPhone(typed)).toBe(saved)
  })

  it('accepts landlines, mobiles and internet numbers, and nothing else', () => {
    for (const ok of ['6123 4567', '8123 4567', '9123 4567', '3123 4567']) expect(phoneError(ok)).toBeUndefined()
    for (const bad of ['1234 5678', '7123 4567', '9123 456', 'call me']) expect(phoneError(bad)).toMatch(/8-digit/)
    expect(phoneError('  ')).toBeUndefined()
  })

  it('asks for a mobile where one is needed', () => {
    expect(phoneError('9123 4567', { mobile: true })).toBeUndefined()
    expect(phoneError('6123 4567', { mobile: true })).toBe('Enter an 8-digit mobile number starting with 8 or 9.')
  })

  it('shows a saved number the way people write it', () => {
    expect(formatSgPhone('+6591234567')).toBe('9123 4567')
    expect(formatSgPhone(null)).toBe('')
    expect(formatSgPhone('legacy 123')).toBe('legacy 123')
  })
})

describe('Singapore postal codes', () => {
  it.each(['018956', '560123', '730001', '750001', '828761'])('accepts %s', (code) => {
    expect(postalCodeError(code)).toBeUndefined()
  })

  it.each(['000000', '741234', '831234', '991234', '56012', 'ABCDEF'])('rejects %s', (code) => {
    expect(postalCodeError(code)).toBe('Enter a 6-digit Singapore postal code.')
  })
})

describe('names', () => {
  it.each(['Tan Ah Kow', 'Ravi s/o Krishnan', 'Siti Nurhaliza binte Ahmad', 'Tan Ah Kow @ Chen Ah Kow', "O'Brien-Lim", '陈亚九'])(
    'accepts %s', (name) => { expect(personNameError(name)).toBeUndefined() })

  it.each(['12345', 'Tan 2', '---', 'tan_ah_kow'])('rejects %s', (name) => {
    expect(personNameError(name)).toBe("Use letters, spaces and ' - . @ / only.")
  })

  it('asks for a name in the words the form gives, and collapses spaces', () => {
    expect(personNameError('  ', 'Enter your full name.')).toBe('Enter your full name.')
    expect(normalizeName('  Tan   Ah  Kow ')).toBe('Tan Ah Kow')
  })
})

describe('date of birth', () => {
  const today = '2026-10-10'

  it('refuses a date before 1900 or in the future', () => {
    expect(dateOfBirthError('1899-12-31', today)).toBe('Enter a date from 1900 onwards.')
    expect(dateOfBirthError('2026-10-11', today)).toBe('Enter a date that is not in the future.')
    expect(dateOfBirthError('1948-02-03', today)).toBeUndefined()
  })

  it('warns, without refusing, when the date makes them younger than 50', () => {
    expect(dateOfBirthWarning('1990-05-01', today)).toBe("That makes them 36. Check this is the elder's date of birth, not yours.")
    expect(dateOfBirthWarning('1948-02-03', today)).toBeUndefined()
    expect(dateOfBirthWarning('1976-10-11', today)).toMatch(/That makes them 49/)
  })
})

describe('dialects', () => {
  it('reads a saved list in any case and keeps only listed languages', () => {
    expect(parseDialects(' hokkien,Mandarin , Klingon')).toEqual(['Mandarin', 'Hokkien'])
    expect(parseDialects(null)).toEqual([])
  })

  it('writes the list in its own order, or nothing', () => {
    expect(joinDialects(['Hokkien', 'English'])).toBe('English,Hokkien')
    expect(joinDialects([])).toBeNull()
  })
})

describe('server errors', () => {
  it('puts each field message from a 400 under its input, renamed where asked', () => {
    const error = new ApiError('Request validation failed', 400, {
      fields: { phone: 'Enter an 8-digit Singapore number starting with 3, 6, 8 or 9', periodInOrder: 'must be true' },
    })
    expect(serverFieldErrors(error, { periodInOrder: 'periodEnd' })).toEqual({
      phone: 'Enter an 8-digit Singapore number starting with 3, 6, 8 or 9.',
      periodEnd: 'Must be true.',
    })
  })

  it('has nothing to place for other errors', () => {
    expect(serverFieldErrors(new ApiError('Conflict', 409, { code: 'X' }))).toEqual({})
    expect(serverFieldErrors(new Error('offline'))).toEqual({})
    expect(problemCode(new ApiError('Conflict', 409, { code: 'VALUE_ADDED_SERVICE_TOO_SOON' }))).toBe('VALUE_ADDED_SERVICE_TOO_SOON')
  })
})
