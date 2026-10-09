import { expect, it } from 'vitest'
import { continuityLabel, dialectsLabel, familyLabel, livesLabel, mobilityLabel } from './elderProfile'

it('spaces stored dialects and says when none are on file', () => {
  expect(dialectsLabel('Malay,Hokkien')).toBe('Malay, Hokkien')
  expect(dialectsLabel(' , ')).toBe('not on file')
  expect(dialectsLabel(null)).toBe('not on file')
})

it('words living arrangement, mobility and continuity, falling back when unknown', () => {
  expect(livesLabel(false)).toBe('with family')
  expect(livesLabel(null)).toBe('not on file')
  expect(mobilityLabel('WHEELCHAIR_BEDBOUND')).toBe('wheelchair or bed-bound')
  expect(mobilityLabel(null)).toBe('not on file')
  expect(continuityLabel('NONE')).toBe('no preference')
  expect(continuityLabel(null)).toBe('not on file')
})

it('names each family member with their relationship', () => {
  expect(
    familyLabel([
      { fullName: 'Wei Ling', relationship: 'DAUGHTER', primaryContact: true },
      { fullName: 'Ah Kow', relationship: 'SON', primaryContact: false },
    ]),
  ).toBe('Wei Ling (daughter), Ah Kow (son)')
  expect(familyLabel([])).toBe('none linked')
})
