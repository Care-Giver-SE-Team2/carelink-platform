import { phoneError, normalizeName, normalizeSgPhone, personNameError } from '../../shared/validation/sg'
import type { ApplicationDetails } from './api'

export function validateApplication(values: ApplicationDetails) {
  const name = personNameError(values.fullName, 'Enter your full name.')
  const phone = values.phone.trim() ? phoneError(values.phone, { mobile: true }) : 'Enter your mobile number.'
  if (name || phone) return name || phone || ''
  if (values.requestedSector && (!/^[A-Za-z0-9 -]+$/.test(values.requestedSector) || values.requestedSector.length > 50)) return 'Use at most 50 letters, digits, spaces or dashes for your preferred area.'
  return ''
}
export function normalizedDetails(values: ApplicationDetails): ApplicationDetails {
  return { ...values, fullName: normalizeName(values.fullName), phone: normalizeSgPhone(values.phone) ?? values.phone,
    requestedSector: values.requestedSector?.trim() || null }
}
