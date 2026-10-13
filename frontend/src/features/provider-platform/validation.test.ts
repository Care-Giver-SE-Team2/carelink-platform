import { describe, expect, it } from 'vitest'
import { summaryLine, toDetails, validateApplication } from './validation'
import type { ApplicationForm } from './validation'

const VALID: ApplicationForm = {
  organisationName: ' Kinetic Care ',
  contactName: 'Rachel Ong',
  contactEmail: 'rachel@kinetic.sg',
  contactPhone: '+65 6123 4567',
  services: ['Companionship'],
  webhookUrl: 'https://hooks.kinetic.sg/carelink',
  password: 'kinetic-password',
  confirmPassword: 'kinetic-password',
  agreed: true,
}

describe('validateApplication', () => {
  it('accepts a complete form', () => {
    expect(validateApplication(VALID)).toEqual({})
  })

  it('lists every problem in form order', () => {
    const errors = validateApplication({
      ...VALID,
      contactEmail: 'rachel@kinetic',
      services: [],
      webhookUrl: 'http://hooks.kinetic.sg/carelink',
      agreed: false,
    })
    expect(Object.keys(errors)).toEqual(['contactEmail', 'services', 'webhookUrl', 'agreed'])
    expect(errors.webhookUrl).toBe('Must be HTTPS. Change http:// to https://.')
    expect(summaryLine('webhookUrl', errors.webhookUrl!)).toBe('Webhook URL: must be HTTPS')
    expect(summaryLine('contactEmail', errors.contactEmail!)).toBe('Email: enter a valid email address')
  })

  it('needs a full HTTPS URL', () => {
    expect(validateApplication({ ...VALID, webhookUrl: 'https://localhost' }).webhookUrl).toMatch(/full URL/)
    expect(validateApplication({ ...VALID, webhookUrl: 'hooks.kinetic.sg' }).webhookUrl).toMatch(/HTTPS/)
  })

  it('needs a long enough password, typed the same twice', () => {
    expect(validateApplication({ ...VALID, password: 'short', confirmPassword: 'short' }).password).toBe('Use at least 8 characters.')
    const mismatch = validateApplication({ ...VALID, confirmPassword: 'something-else' })
    expect(mismatch.confirmPassword).toBe("The passwords don't match.")
    expect(summaryLine('confirmPassword', mismatch.confirmPassword!)).toBe("Confirm password: the passwords don't match")
  })
})

it('turns the form into the application to submit', () => {
  expect(toDetails(VALID)).toEqual({
    organisationName: 'Kinetic Care',
    contactName: 'Rachel Ong',
    contactEmail: 'rachel@kinetic.sg',
    contactPhone: '+65 6123 4567',
    services: ['Companionship'],
    webhookUrl: 'https://hooks.kinetic.sg/carelink',
  })
})
