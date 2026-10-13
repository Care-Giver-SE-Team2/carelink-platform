import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { submitApplication } from '../../../features/provider-platform/api'
import { ApiError } from '../../../features/provider-platform/errors'
import { MIN_PASSWORD_LENGTH, PROVIDER_SERVICES } from '../../../features/provider-platform/types'
import { FIELD_LABELS, summaryLine, toDetails, validateApplication } from '../../../features/provider-platform/validation'
import type { ApplicationField, ApplicationForm } from '../../../features/provider-platform/validation'
import { Banner, Button, Checkbox, Field, SectionLabel, StateTag, TextInput } from '../components/ui'
import { WorkbenchShell } from '../components/WorkbenchShell'
import { useWorkbench } from '../workbenchContext'
import styles from './Apply.module.css'

const EMPTY: ApplicationForm = {
  organisationName: '',
  contactName: '',
  contactEmail: '',
  contactPhone: '',
  services: [],
  webhookUrl: '',
  password: '',
  confirmPassword: '',
  agreed: false,
}

const FIELD_IDS: Record<ApplicationField, string> = {
  organisationName: 'apply-organisation',
  contactName: 'apply-contact',
  contactEmail: 'apply-email',
  contactPhone: 'apply-phone',
  services: 'apply-services',
  webhookUrl: 'apply-webhook',
  password: 'apply-password',
  confirmPassword: 'apply-confirm-password',
  agreed: 'apply-agreed',
}

/**
 * Apply (PT01): the provider tells CareLink who they are, what they offer and where to send
 * jobs, and sets the password for its portal account. Errors show once Submit has been
 * pressed — a summary whose lines jump to their field, and a message under each field — and
 * then follow every change. On success the provider is signed in and sees its application ID.
 */
export default function Apply() {
  const navigate = useNavigate()
  const { signIn } = useWorkbench()
  const [exists, setExists] = useState(false)
  const [form, setForm] = useState<ApplicationForm>(EMPTY)
  const [attempted, setAttempted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState<{ id: string; email: string } | null>(null)

  const errors = attempted ? validateApplication(form) : {}
  const errorFields = Object.keys(errors) as ApplicationField[]

  function set<K extends ApplicationField>(field: K, value: ApplicationForm[K]) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  function toggleService(service: string, on: boolean) {
    set('services', on ? [...form.services, service] : form.services.filter((s) => s !== service))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setAttempted(true)
    const found = validateApplication(form)
    if (Object.keys(found).length > 0) {
      document.getElementById('apply-summary')?.focus()
      return
    }
    setBusy(true)
    setFailure(null)
    setExists(false)
    try {
      const { applicationId } = await submitApplication(toDetails(form), form.password)
      await signIn(form.contactEmail, form.password)
      setSubmitted({ id: applicationId, email: form.contactEmail.trim() })
    } catch (err) {
      if (err instanceof ApiError && err.code === 'account_exists') setExists(true)
      else setFailure(err instanceof Error ? err.message : 'Could not submit the application.')
    } finally {
      setBusy(false)
    }
  }

  if (submitted) {
    return (
      <WorkbenchShell>
        <div className={styles.success}>
          <StateTag kind="submitted" />
          <h1 className={styles.title}>Application received</h1>
          <p className={styles.sub}>
            We've emailed a copy to {submitted.email}, and you're signed in to the portal. Sign in with that email and your password to check
            the status.
          </p>
          <div className={styles.idBox}>
            <div className={styles.idText}>
              <SectionLabel>Application ID</SectionLabel>
              <span className={styles.id}>{submitted.id}</span>
            </div>
            <Button size="small" onClick={() => void navigator.clipboard?.writeText(submitted.id).catch(() => {})}>
              Copy
            </Button>
          </div>
          <div>
            <Button
              variant="primary"
              onClick={() => navigate('/provider/status')}
            >
              Check application status
            </Button>
          </div>
        </div>
      </WorkbenchShell>
    )
  }

  const error = (field: ApplicationField) => errors[field]
  const errorId = (field: ApplicationField) => (errors[field] ? `${FIELD_IDS[field]}-error` : undefined)

  return (
    <WorkbenchShell>
      <div className={styles.layout}>
        <form className={styles.form} onSubmit={submit} noValidate>
          <div className={styles.intro}>
            <h1 className={styles.title}>Apply to join the CareLink Open Platform</h1>
            <p className={styles.sub}>Tell us about your organisation. CareLink reviews every application and emails you when the status changes.</p>
          </div>

          {errorFields.length > 0 && (
            <div id="apply-summary" className={styles.summary} tabIndex={-1} role="alert">
              <div className={styles.summaryTitle}>
                Fix {errorFields.length} {errorFields.length === 1 ? 'field' : 'fields'} before submitting
              </div>
              <ul className={styles.summaryList}>
                {errorFields.map((field) => (
                  <li key={field}>
                    <a
                      href={`#${FIELD_IDS[field]}`}
                      onClick={(e) => {
                        e.preventDefault()
                        document.getElementById(FIELD_IDS[field])?.focus()
                      }}
                    >
                      {summaryLine(field, errors[field]!)}
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          )}

          <section className={styles.group} aria-labelledby="apply-org-heading">
            <SectionLabel id="apply-org-heading">Organisation and contact</SectionLabel>
            <Field label={FIELD_LABELS.organisationName} htmlFor={FIELD_IDS.organisationName} error={error('organisationName')} errorId={errorId('organisationName')}>
              <TextInput
                id={FIELD_IDS.organisationName}
                value={form.organisationName}
                onChange={(e) => set('organisationName', e.target.value)}
                invalid={Boolean(error('organisationName'))}
                aria-describedby={errorId('organisationName')}
                autoComplete="organization"
              />
            </Field>
            <Field label={FIELD_LABELS.contactName} htmlFor={FIELD_IDS.contactName} error={error('contactName')} errorId={errorId('contactName')}>
              <TextInput
                id={FIELD_IDS.contactName}
                value={form.contactName}
                onChange={(e) => set('contactName', e.target.value)}
                invalid={Boolean(error('contactName'))}
                aria-describedby={errorId('contactName')}
                autoComplete="name"
              />
            </Field>
            <div className={styles.pair}>
              <Field label={FIELD_LABELS.contactEmail} htmlFor={FIELD_IDS.contactEmail} error={error('contactEmail')} errorId={errorId('contactEmail')}>
                <TextInput
                  id={FIELD_IDS.contactEmail}
                  type="email"
                  value={form.contactEmail}
                  onChange={(e) => set('contactEmail', e.target.value)}
                  invalid={Boolean(error('contactEmail'))}
                  aria-describedby={errorId('contactEmail')}
                  autoComplete="email"
                />
              </Field>
              <Field label={FIELD_LABELS.contactPhone} htmlFor={FIELD_IDS.contactPhone} error={error('contactPhone')} errorId={errorId('contactPhone')}>
                <TextInput
                  id={FIELD_IDS.contactPhone}
                  type="tel"
                  value={form.contactPhone}
                  onChange={(e) => set('contactPhone', e.target.value)}
                  invalid={Boolean(error('contactPhone'))}
                  aria-describedby={errorId('contactPhone')}
                  autoComplete="tel"
                />
              </Field>
            </div>
          </section>

          <section className={styles.group} aria-labelledby="apply-services-heading">
            <SectionLabel id="apply-services-heading">Services</SectionLabel>
            <fieldset className={styles.fieldset} aria-describedby={errorId('services')}>
              <legend className={styles.legend}>{FIELD_LABELS.services}</legend>
              <div id={FIELD_IDS.services} tabIndex={-1} className={error('services') ? styles.servicesInvalid : styles.services}>
                {PROVIDER_SERVICES.map((service) => (
                  <Checkbox key={service} checked={form.services.includes(service)} onChange={(on) => toggleService(service, on)}>
                    {service}
                  </Checkbox>
                ))}
              </div>
              {error('services') && (
                <div id={errorId('services')} className={styles.error}>
                  {error('services')}
                </div>
              )}
            </fieldset>
          </section>

          <section className={styles.group} aria-labelledby="apply-account-heading">
            <SectionLabel id="apply-account-heading">Portal sign-in</SectionLabel>
            <div className={styles.pair}>
              <Field
                label={FIELD_LABELS.password}
                htmlFor={FIELD_IDS.password}
                helper={`At least ${MIN_PASSWORD_LENGTH} characters.`}
                error={error('password')}
                errorId={errorId('password')}
              >
                <TextInput
                  id={FIELD_IDS.password}
                  type="password"
                  value={form.password}
                  onChange={(e) => set('password', e.target.value)}
                  invalid={Boolean(error('password'))}
                  aria-describedby={errorId('password')}
                  autoComplete="new-password"
                />
              </Field>
              <Field
                label={FIELD_LABELS.confirmPassword}
                htmlFor={FIELD_IDS.confirmPassword}
                error={error('confirmPassword')}
                errorId={errorId('confirmPassword')}
              >
                <TextInput
                  id={FIELD_IDS.confirmPassword}
                  type="password"
                  value={form.confirmPassword}
                  onChange={(e) => set('confirmPassword', e.target.value)}
                  invalid={Boolean(error('confirmPassword'))}
                  aria-describedby={errorId('confirmPassword')}
                  autoComplete="new-password"
                />
              </Field>
            </div>
            <p className={styles.hint}>You sign in to this portal with your contact email and this password.</p>
          </section>

          <section className={styles.group} aria-labelledby="apply-integration-heading">
            <SectionLabel id="apply-integration-heading">Integration</SectionLabel>
            <Field
              label={FIELD_LABELS.webhookUrl}
              htmlFor={FIELD_IDS.webhookUrl}
              helper="We'll POST signed job notifications here. Must be HTTPS."
              error={error('webhookUrl')}
              errorId={errorId('webhookUrl')}
            >
              <TextInput
                id={FIELD_IDS.webhookUrl}
                mono
                type="url"
                value={form.webhookUrl}
                onChange={(e) => set('webhookUrl', e.target.value)}
                invalid={Boolean(error('webhookUrl'))}
                aria-describedby={errorId('webhookUrl')}
                placeholder="https://hooks.example.sg/carelink"
              />
            </Field>
          </section>

          <section className={styles.group}>
            <div className={styles.agreement}>
              <Checkbox
                id={FIELD_IDS.agreed}
                checked={form.agreed}
                onChange={(on) => set('agreed', on)}
                invalid={Boolean(error('agreed'))}
                describedBy={errorId('agreed')}
              >
                I agree to the <a href="#terms">Open Platform provider terms</a> and the <a href="#data-policy">data-handling policy</a>.
              </Checkbox>
              {error('agreed') && (
                <div id={errorId('agreed')} className={styles.agreementError}>
                  {error('agreed')}
                </div>
              )}
            </div>
            {exists && (
              <Banner
                tone="warning"
                title="This email already has a provider account"
                role="alert"
                action={<Link to="/provider/status">Sign in →</Link>}
              >
                Sign in to see your application instead.
              </Banner>
            )}
            {failure && (
              <Banner tone="danger" title="Couldn't submit your application" role="alert">
                {failure}
              </Banner>
            )}
            <div>
              <Button type="submit" variant="primary" busy={busy && 'Submitting…'}>
                Submit application
              </Button>
            </div>
          </section>
        </form>

        <aside className={styles.next} aria-label="What happens next">
          <SectionLabel>What happens next</SectionLabel>
          <ol className={styles.steps}>
            <li>You get an application ID straight away.</li>
            <li>A CareLink admin reviews it, usually within 2 working days.</li>
            <li>Once approved, you collect your credentials here and connect.</li>
          </ol>
        </aside>
      </div>
    </WorkbenchShell>
  )
}
