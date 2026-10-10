/**
 * Singapore formats checked before a form is sent. The server checks the same rules in
 * `shared/validation/SingaporeFormats.java`; keep the two in step.
 */

/** The languages an elder may prefer; rostering matches them against what caregivers speak. */
export const DIALECTS = [
  'English', 'Mandarin', 'Malay', 'Tamil', 'Hokkien', 'Teochew', 'Cantonese', 'Hakka', 'Hainanese',
] as const

const PHONE = /^\+65[3689]\d{7}$/
const MOBILE = /^\+65[89]\d{7}$/
/** Six digits whose first two are a postal district: 01 to 82, where 74 is not used. */
const POSTAL_CODE = /^(0[1-9]|[1-6]\d|7[0-35-9]|8[0-2])\d{4}$/
/** Letters in any script, with the spaces, apostrophes, hyphens, dots, "@" and "/" names use. */
const PERSON_NAME = /^(?=.*\p{L})[\p{L}\p{M} .'’@/-]+$/u

/**
 * "+6591234567" from "9123 4567", "+65 9123-4567" or "6591234567"; null when it is not eight
 * digits after the country code, or blank.
 */
export function normalizeSgPhone(raw: string): string | null {
  let digits = raw.replace(/[\s()-]/g, '')
  if (digits.startsWith('+65')) digits = digits.slice(3)
  else if (digits.length === 10 && digits.startsWith('65')) digits = digits.slice(2)
  return /^\d{8}$/.test(digits) ? `+65${digits}` : null
}

/** "9123 4567" from a saved "+6591234567", for an input to start from; anything else as it is. */
export function formatSgPhone(saved: string | null): string {
  if (!saved) return ''
  const match = /^\+65(\d{4})(\d{4})$/.exec(saved)
  return match ? `${match[1]} ${match[2]}` : saved
}

/** Why a phone number is not a Singapore one, or undefined when it is (or is left blank). */
export function phoneError(raw: string, { mobile = false } = {}): string | undefined {
  if (!raw.trim()) return undefined
  const phone = normalizeSgPhone(raw)
  if (mobile) return phone && MOBILE.test(phone) ? undefined : 'Enter an 8-digit mobile number starting with 8 or 9.'
  return phone && PHONE.test(phone) ? undefined : 'Enter an 8-digit Singapore number starting with 3, 6, 8 or 9.'
}

/** Why a postal code is not a Singapore one, or undefined when it is (or is left blank). */
export function postalCodeError(raw: string): string | undefined {
  const code = raw.trim()
  if (!code) return undefined
  return POSTAL_CODE.test(code) ? undefined : 'Enter a 6-digit Singapore postal code.'
}

/** Trims a name and collapses runs of spaces inside it. */
export function normalizeName(raw: string): string {
  return raw.trim().replace(/\s+/g, ' ')
}

/** Why a full name cannot be saved, or undefined when it can. */
export function personNameError(raw: string, blank = 'Enter a full name.'): string | undefined {
  const name = normalizeName(raw)
  if (!name) return blank
  if ([...name].length > 100) return 'Use 100 characters or fewer.'
  return PERSON_NAME.test(name) ? undefined : "Use letters, spaces and ' - . @ / only."
}

/** Whole years from an ISO date of birth to `today` (both "yyyy-mm-dd"). */
function ageOn(dateOfBirth: string, today: string): number {
  const [by, bm, bd] = dateOfBirth.split('-').map(Number)
  const [ty, tm, td] = today.split('-').map(Number)
  return ty - by - (tm < bm || (tm === bm && td < bd) ? 1 : 0)
}

/** Why a date of birth cannot be saved, or undefined when it can (or is left blank). */
export function dateOfBirthError(value: string, today: string): string | undefined {
  if (!value) return undefined
  if (value < '1900-01-01') return 'Enter a date from 1900 onwards.'
  if (value > today) return 'Enter a date that is not in the future.'
  return undefined
}

/** A note, not a block, when the date suggests someone other than the elder was entered. */
export function dateOfBirthWarning(value: string, today: string): string | undefined {
  if (!value || dateOfBirthError(value, today)) return undefined
  return ageOn(value, today) < 50
    ? `That makes them ${ageOn(value, today)}. Check this is the elder's date of birth, not yours.`
    : undefined
}

/** The listed dialects in a saved "Hokkien,Mandarin" list; entries not on the list are left out. */
export function parseDialects(saved: string | null): string[] {
  const entries = (saved ?? '').split(',').map((entry) => entry.trim().toLowerCase())
  return DIALECTS.filter((dialect) => entries.includes(dialect.toLowerCase()))
}

/** "Hokkien,Mandarin" in the list's order, or null when none is chosen. */
export function joinDialects(chosen: readonly string[]): string | null {
  const list = DIALECTS.filter((dialect) => chosen.includes(dialect))
  return list.length ? list.join(',') : null
}
