import type { ValueAddedService } from '../../../features/value-added-services/types'

/** The longest a service may last, as report allows: one visit, within a caregiver's day. */
export const MAX_SERVICE_MINUTES = 720
/** As long as value_added_service.name. */
export const MAX_SERVICE_NAME_LENGTH = 100
export const MAX_SERVICE_DESCRIPTION_LENGTH = 1000

/** "45 min", "2 h", "1 h 30". */
export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} h` : `${hours} h ${String(rest).padStart(2, '0')}`
}

export type ServiceForm = { name: string; description: string; durationMinutes: number | null }
export type ServiceFormErrors = Partial<Record<keyof ServiceForm, string>>

/**
 * What is wrong with the form, field by field; empty when it can be sent. `catalogue` is checked for the name
 * when adding, ignoring case and spaces as report does, withdrawn services included.
 */
export function validateService(form: ServiceForm, catalogue: ValueAddedService[] | null): ServiceFormErrors {
  const errors: ServiceFormErrors = {}
  if (catalogue !== null) {
    const name = form.name.trim()
    if (!name) errors.name = 'Enter a name.'
    else if (name.length > MAX_SERVICE_NAME_LENGTH) errors.name = `Use ${MAX_SERVICE_NAME_LENGTH} characters or fewer.`
    else {
      const taken = catalogue.find((s) => s.name.trim().toLowerCase() === name.toLowerCase())
      if (taken) {
        errors.name =
          taken.status === 'AVAILABLE'
            ? `There is already a service called "${taken.name}".`
            : `"${taken.name}" is in the catalogue but unavailable. Make it available again instead.`
      }
    }
  }
  if (form.description.length > MAX_SERVICE_DESCRIPTION_LENGTH) {
    errors.description = `Use ${MAX_SERVICE_DESCRIPTION_LENGTH} characters or fewer.`
  }
  if (form.durationMinutes === null || form.durationMinutes < 1) errors.durationMinutes = 'Enter how many minutes it lasts.'
  else if (form.durationMinutes > MAX_SERVICE_MINUTES) errors.durationMinutes = 'It can last 12 hours (720 minutes) at most.'
  return errors
}
