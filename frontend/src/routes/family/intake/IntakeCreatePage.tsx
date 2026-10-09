import { useRef, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useIntakeSubmission } from '../../../features/intake/useIntakeSubmission'
import {
  emptyIntakeForm,
  intakeFormRequest,
  validateIntakeForm,
} from '../../../features/intake/intakeForm'
import type { IntakeFormErrors, IntakeFormValues } from '../../../features/intake/intakeForm'
import type { MobilityLevel } from '../../../features/intake/types'
import { IntakeIcon } from './IntakeLayout'
import { IntakeSubmissionFeedback } from './IntakeSubmissionFeedback'
import styles from './FamilyIntake.module.css'
import formStyles from './IntakeForm.module.css'

/**
 * Collects elder information and submits a family intake application.
 * @author Wang Zhili
 */
export function IntakeCreatePage() {
  const navigate = useNavigate()
  const [values, setValues] = useState<IntakeFormValues>(emptyIntakeForm)
  const [errors, setErrors] = useState<IntakeFormErrors>({})
  const validationSummary = useRef<HTMLDivElement>(null)
  const { state, submit } = useIntakeSubmission()
  const busy = state.status === 'submitting'

  function update<K extends keyof IntakeFormValues>(field: K, value: IntakeFormValues[K]) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function validationProps(field: keyof IntakeFormValues, id: string) {
    return {
      'aria-invalid': !!errors[field],
      'aria-describedby': errors[field] ? id + '-error' : undefined,
    }
  }

  function toggleCareNeed(need: string, checked: boolean) {
    update(
      'careNeeds',
      checked ? [...values.careNeeds, need] : values.careNeeds.filter((value) => value !== need),
    )
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const nextErrors = validateIntakeForm(values)
    setErrors(nextErrors)
    if (Object.values(nextErrors).some(Boolean)) {
      requestAnimationFrame(() => validationSummary.current?.focus())
      return
    }
    const application = await submit(intakeFormRequest(values))
    if (application)
      navigate('/family/intake/' + application.id, {
        replace: true,
        state: { submittedApplicationId: application.id },
      })
  }

  return (
    <div className={styles.page}>
      <div className={styles.detailNav}>
        <Link className={styles.backLink} to="/family/intake">
          <IntakeIcon name="back" />
          Back to applications
        </Link>
      </div>
      <div className={styles.detailHeading}>
        <p className={styles.eyebrow}>GETTING STARTED</p>
        <h1>New care application</h1>
        <p className={styles.subtitle}>Tell us about your loved one and the care they need.</p>
      </div>
      {state.status === 'error' && (
        <IntakeSubmissionFeedback failure={state} />
      )}
      <form
        className={formStyles.form}
        onSubmit={onSubmit}
        autoComplete="off"
        aria-busy={busy}
        noValidate
      >
        {Object.values(errors).some(Boolean) && (
          <div
            ref={validationSummary}
            tabIndex={-1}
            className={formStyles.errorSummary}
            role="alert"
          >
            Please check the highlighted fields. Your entries have been kept.
          </div>
        )}
        <fieldset disabled={busy} className={formStyles.controls}>
          <legend className={formStyles.srOnly}>Care application</legend>
          <section className={formStyles.section}>
            <div className={formStyles.sectionHeading}>
              <span aria-hidden="true">01</span>
              <h2>Elder information</h2>
            </div>
            <Field id="elder-name" label="Elder full name" error={errors.targetElderName}>
              <input
                id="elder-name"
                {...validationProps('targetElderName', 'elder-name')}
                required
                value={values.targetElderName}
                onChange={(event) => update('targetElderName', event.target.value)}
              />
            </Field>
            <div className={formStyles.row}>
              <Field id="elder-age" label="Age" optional error={errors.targetElderAge}>
                <input
                  id="elder-age"
                  {...validationProps('targetElderAge', 'elder-age')}
                  inputMode="numeric"
                  value={values.targetElderAge}
                  onChange={(event) => update('targetElderAge', event.target.value)}
                />
              </Field>
              <Field id="elder-postal-code" label="Postal code" error={errors.postalCode}>
                <input
                  id="elder-postal-code"
                  {...validationProps('postalCode', 'elder-postal-code')}
                  required
                  value={values.postalCode}
                  onChange={(event) => update('postalCode', event.target.value)}
                />
              </Field>
            </div>
            <Field id="elder-address" label="Home address" error={errors.targetAddress}>
              <textarea
                id="elder-address"
                {...validationProps('targetAddress', 'elder-address')}
                rows={2}
                required
                value={values.targetAddress}
                onChange={(event) => update('targetAddress', event.target.value)}
              />
            </Field>
            <Field
              id="elder-dialects"
              label="Preferred dialects"
              optional
              error={errors.preferredDialects}
            >
              <input
                id="elder-dialects"
                {...validationProps('preferredDialects', 'elder-dialects')}
                placeholder="e.g. Hokkien, Mandarin"
                value={values.preferredDialects}
                onChange={(event) => update('preferredDialects', event.target.value)}
              />
            </Field>
          </section>
          <section className={formStyles.section}>
            <div className={formStyles.sectionHeading}>
              <span aria-hidden="true">02</span>
              <h2>Care and daily needs</h2>
            </div>
            <Field id="elder-mobility" label="Mobility">
              <select
                id="elder-mobility"
                value={values.mobilityLevel}
                onChange={(event) => update('mobilityLevel', event.target.value as MobilityLevel)}
              >
                <option value="INDEPENDENT">Moves independently</option>
                <option value="ASSISTIVE_CANE">Uses a walking aid</option>
                <option value="WHEELCHAIR_BEDBOUND">Uses a wheelchair / stays in bed</option>
              </select>
            </Field>
            <fieldset className={formStyles.choices}>
              <legend>
                Care needs <span>Optional</span>
              </legend>
              <label>
                <input
                  type="checkbox"
                  checked={values.careNeeds.includes('BATHING')}
                  onChange={(event) => toggleCareNeed('BATHING', event.target.checked)}
                />
                Bathing assistance
              </label>
              <label>
                <input
                  type="checkbox"
                  checked={values.careNeeds.includes('VITALS')}
                  onChange={(event) => toggleCareNeed('VITALS', event.target.checked)}
                />
                Vital signs monitoring
              </label>
            </fieldset>
            <Field id="elder-other-care" label="Other care needs" optional>
              <textarea
                id="elder-other-care"
                rows={3}
                aria-describedby="other-care-hint"
                placeholder="e.g. Help with meal preparation"
                value={values.otherCareNeeds}
                onChange={(event) => update('otherCareNeeds', event.target.value)}
              />
              <p id="other-care-hint" className={formStyles.hint}>
                Enter one need per line. Repeated items are included once.
              </p>
            </Field>
          </section>
          <section className={formStyles.section}>
            <div className={formStyles.sectionHeading}>
              <span aria-hidden="true">03</span>
              <h2>Additional information</h2>
            </div>
            <Field id="elder-notes" label="Medical notes" optional>
              <textarea
                id="elder-notes"
                rows={4}
                placeholder="Anything the care team should know"
                value={values.medicalNotes}
                onChange={(event) => update('medicalNotes', event.target.value)}
              />
            </Field>
          </section>
        </fieldset>
        {/* Desktop: the right-hand column, kept in view beside the form. On a phone its parts fall
            back into one column: the intro first, next steps and submit after the sections. */}
        <div className={formStyles.side}>
          <p className={formStyles.intro}>
            This application helps the care team assess your loved one's needs. Fields marked optional
            can be left blank.
          </p>
          <section className={formStyles.next} aria-labelledby="intake-next">
            <h2 id="intake-next">What happens next</h2>
            <ol>
              <li>A care manager reviews your application.</li>
              <li>You hear back within two working days. The decision, and any message, appears on your applications page.</li>
              <li>If it is approved, the care manager sets up your loved one's CareLink account and gets in touch.</li>
            </ol>
          </section>
          <div className={formStyles.submitArea}>
            <p>Your application will be sent to the care team for review.</p>
            <button className={styles.primaryButton} type="submit" disabled={busy}>
              {busy ? 'Submitting…' : 'Submit application'}
            </button>
            <Link to="/family/intake">Cancel and return to applications</Link>
          </div>
        </div>
      </form>
    </div>
  )
}

function Field({
  id,
  label,
  optional,
  error,
  children,
}: {
  id: string
  label: string
  optional?: boolean
  error?: string
  children: ReactNode
}) {
  return (
    <div className={formStyles.field}>
      <div className={formStyles.labelRow}>
        <label htmlFor={id}>{label}</label>
        {optional && <span>Optional</span>}
      </div>
      {children}
      {error && (
        <p id={id + '-error'} className={formStyles.fieldError}>
          {error}
        </p>
      )}
    </div>
  )
}
