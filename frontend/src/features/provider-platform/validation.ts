import { MIN_PASSWORD_LENGTH } from './types'
import type { ApplicationDetails } from './types'

/** The Apply form as typed, before it becomes an application. */
export type ApplicationForm = {
  organisationName: string
  contactName: string
  contactEmail: string
  contactPhone: string
  services: string[]
  webhookUrl: string
  /** For the portal account the application creates. */
  password: string
  confirmPassword: string
  agreed: boolean
}

export type ApplicationField = keyof ApplicationForm

/** Each field's name in the error summary, in the order the form shows them. */
export const FIELD_LABELS: Record<ApplicationField, string> = {
  organisationName: 'Organisation name',
  contactName: 'Contact name',
  contactEmail: 'Email',
  contactPhone: 'Phone',
  services: 'Services offered',
  webhookUrl: 'Webhook URL',
  password: 'Password',
  confirmPassword: 'Confirm password',
  agreed: 'Agreement',
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

/**
 * A local run against a provider on the same machine, with VITE_OPEN_PLATFORM_ALLOW_HTTP=true: an
 * http:// URL and a host without a dot (localhost) are accepted, as notification accepts them with
 * carelink.webhooks.allow-local-endpoints. Never under Vitest or in a build.
 */
export const ALLOW_HTTP_WEBHOOKS =
  import.meta.env.DEV && import.meta.env.MODE !== 'test' && import.meta.env.VITE_OPEN_PLATFORM_ALLOW_HTTP === 'true'

function webhookError(value: string): string | null {
  const url = value.trim()
  if (!url) return 'Enter the URL we should send jobs to.'
  if (ALLOW_HTTP_WEBHOOKS) {
    try {
      return /^https?:$/.test(new URL(url).protocol) ? null : 'Must start with http:// or https://.'
    } catch {
      return 'Enter a full URL, for example http://localhost:4000/carelink.'
    }
  }
  if (/^http:\/\//i.test(url)) return 'Must be HTTPS. Change http:// to https://.'
  if (!/^https:\/\//i.test(url)) return 'Must be HTTPS, starting with https://.'
  try {
    const parsed = new URL(url)
    if (!parsed.hostname.includes('.')) return 'Enter a full URL, for example https://hooks.example.sg/carelink.'
  } catch {
    return 'Enter a full URL, for example https://hooks.example.sg/carelink.'
  }
  return null
}

/** Every problem with the form, keyed by field, in form order. Empty when it can be submitted. */
export function validateApplication(form: ApplicationForm): Partial<Record<ApplicationField, string>> {
  const errors: Partial<Record<ApplicationField, string>> = {}
  if (!form.organisationName.trim()) errors.organisationName = 'Enter your organisation name.'
  if (!form.contactName.trim()) errors.contactName = 'Enter who we should contact.'
  if (!EMAIL.test(form.contactEmail.trim())) errors.contactEmail = 'Enter a valid email address, for example name@company.sg.'
  if (!form.contactPhone.trim()) errors.contactPhone = 'Enter a phone number.'
  if (form.services.length === 0) errors.services = 'Select at least one service.'
  const webhook = webhookError(form.webhookUrl)
  if (webhook) errors.webhookUrl = webhook
  if (form.password.length < MIN_PASSWORD_LENGTH) errors.password = `Use at least ${MIN_PASSWORD_LENGTH} characters.`
  else if (form.confirmPassword !== form.password) errors.confirmPassword = "The passwords don't match."
  if (!form.agreed) errors.agreed = 'Accept the terms to apply.'
  return errors
}

/** The short reason shown in the error summary, e.g. "Webhook URL: must be HTTPS". */
export function summaryLine(field: ApplicationField, message: string): string {
  const short: Partial<Record<ApplicationField, string>> = {
    contactEmail: 'enter a valid email address',
    services: 'select at least one service',
    webhookUrl: message.startsWith('Must be HTTPS') ? 'must be HTTPS' : 'enter a full HTTPS URL',
    agreed: 'accept the terms to apply',
    password: `use at least ${MIN_PASSWORD_LENGTH} characters`,
    confirmPassword: "the passwords don't match",
  }
  return `${FIELD_LABELS[field]}: ${short[field] ?? 'required'}`
}

/** The application to submit, from a form that passed validation. */
export function toDetails(form: ApplicationForm): ApplicationDetails {
  return {
    organisationName: form.organisationName.trim(),
    contactName: form.contactName.trim(),
    contactEmail: form.contactEmail.trim(),
    contactPhone: form.contactPhone.trim(),
    services: [...form.services],
    webhookUrl: form.webhookUrl.trim(),
  }
}
