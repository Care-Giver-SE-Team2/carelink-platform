import type { ElderFamilyContact, ElderResponse } from '../../../shared/api/profile'

const NOT_ON_FILE = 'not on file'

const MOBILITY: Record<NonNullable<ElderResponse['mobilityLevel']>, string> = {
  INDEPENDENT: 'independent',
  ASSISTIVE_CANE: 'cane or walker',
  WHEELCHAIR_BEDBOUND: 'wheelchair or bed-bound',
}

const CONTINUITY: Record<NonNullable<ElderResponse['continuityPreference']>, string> = {
  PREFERRED: 'preferred',
  REQUIRED: 'required',
  NONE: 'no preference',
}

/** "Malay,Hokkien" as stored → "Malay, Hokkien". */
export function dialectsLabel(preferredDialects: string | null): string {
  const dialects = (preferredDialects ?? '')
    .split(',')
    .map((d) => d.trim())
    .filter(Boolean)
  return dialects.length > 0 ? dialects.join(', ') : NOT_ON_FILE
}

export function livesLabel(livesAlone: boolean | null): string {
  if (livesAlone === null) return NOT_ON_FILE
  return livesAlone ? 'alone' : 'with family'
}

export function mobilityLabel(level: ElderResponse['mobilityLevel']): string {
  return level ? MOBILITY[level] : NOT_ON_FILE
}

export function continuityLabel(preference: ElderResponse['continuityPreference']): string {
  return preference ? CONTINUITY[preference] : NOT_ON_FILE
}

/** "Wei Ling (daughter), Ah Kow (son)" — primary contact first, as the backend orders them. */
export function familyLabel(family: ElderFamilyContact[]): string {
  if (family.length === 0) return 'none linked'
  return family.map((f) => `${f.fullName} (${f.relationship.toLowerCase()})`).join(', ')
}
