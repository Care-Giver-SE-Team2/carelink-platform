import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { registerCaregiver } from '../../features/admission/api'
import { normalizedDetails } from '../../features/admission/validation'
import { initialiseCsrf } from '../../features/auth/api'
import { ApiError } from '../../shared/api/client'
import { serverFieldErrors } from '../../shared/validation/serverErrors'
import { DIALECTS, personNameError, phoneError } from '../../shared/validation/sg'
import styles from './Landing.module.css'
import signUpStyles from './FamilySignUp.module.css'
import caregiverStyles from './CaregiverSignUp.module.css'

type Values = {
  fullName: string
  phone: string
  requestedSector: string
  dialects: string[]
  username: string
  password: string
  confirm: string
}
type TextField = Exclude<keyof Values, 'dialects'>
type Errors = Partial<Record<TextField, string>>

/** Mirrors the format checks on profile.controller.dto.CaregiverAdmissionRequests.Registration. */
function validate(values: Values): Errors {
  const errors: Errors = {}
  errors.fullName = personNameError(values.fullName, 'Enter your full name.')
  errors.phone = values.phone.trim() ? phoneError(values.phone, { mobile: true }) : 'Enter your mobile number.'
  const sector = values.requestedSector.trim()
  if (sector && (!/^[A-Za-z0-9 -]+$/.test(sector) || sector.length > 50))
    errors.requestedSector = 'Use at most 50 letters, digits, spaces or dashes.'
  if (!/^[a-z0-9][a-z0-9._-]{2,63}$/.test(values.username))
    errors.username = 'Use 3 to 64 lower-case letters, digits, dots, dashes or underscores.'
  if (values.password.length < 8) errors.password = 'Use at least 8 characters.'
  else if (new TextEncoder().encode(values.password).length > 72) errors.password = 'Use at most 72 UTF-8 bytes.'
  else if (values.confirm !== values.password) errors.confirm = 'The passwords do not match.'
  return errors
}

/**
 * Public sign-up for a caregiver applying to work, reached from the landing page's "No account
 * yet?" card. Creates a CAREGIVER login with a pending application for a care manager to review.
 * It does not sign in: the applicant signs in from the landing page to add certificates and follow
 * the decision, and work stays locked until the application is approved.
 */
export default function CaregiverSignUp() {
  const ids = {
    fullName: useId(),
    phone: useId(),
    requestedSector: useId(),
    username: useId(),
    password: useId(),
    confirm: useId(),
  }
  const [values, setValues] = useState<Values>({
    fullName: '',
    phone: '',
    requestedSector: '',
    dialects: [],
    username: '',
    password: '',
    confirm: '',
  })
  const [errors, setErrors] = useState<Errors>({})
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [submitted, setSubmitted] = useState<string | null>(null)

  function update(field: TextField, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  function toggleDialect(dialect: string, checked: boolean) {
    setValues((current) => ({
      ...current,
      dialects: checked ? [...current.dialects, dialect] : current.dialects.filter((d) => d !== dialect),
    }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    const nextErrors = validate(values)
    setErrors(nextErrors)
    setError('')
    if (Object.values(nextErrors).some(Boolean)) return

    setBusy(true)
    try {
      await initialiseCsrf()
      await registerCaregiver({
        ...normalizedDetails({
          fullName: values.fullName,
          phone: values.phone,
          declaredDialects: values.dialects.join(',') || null,
          requestedSector: values.requestedSector,
        }),
        username: values.username,
        password: values.password,
      })
      setSubmitted(values.username)
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) {
        setErrors({ username: 'That username is taken. Choose another.' })
      } else if (failure instanceof ApiError && failure.status === 400) {
        const fields = serverFieldErrors(failure)
        if (Object.keys(fields).length) setErrors(fields)
        else setError('Please check your details and try again.')
      } else {
        // The account may exist even though no answer arrived, so a retry could hit "taken".
        setError('The result could not be confirmed. Try signing in with your chosen account before submitting again.')
      }
    } finally {
      setBusy(false)
    }
  }

  function fieldProps(field: TextField) {
    return {
      id: ids[field],
      value: values[field],
      disabled: busy,
      'aria-invalid': !!errors[field],
      'aria-describedby': errors[field] ? `${ids[field]}-error` : undefined,
    }
  }

  function fieldError(field: TextField) {
    return errors[field] ? (
      <p id={`${ids[field]}-error`} className={signUpStyles.fieldError}>
        {errors[field]}
      </p>
    ) : null
  }

  return (
    <div className={styles.page}>
      <div className={styles.canvas}>
        <div className={styles.leftCol}>
          <div className={styles.header}>
            <Link to="/" className={`${styles.wordmark} ${signUpStyles.wordmarkLink}`}>
              CareLink
            </Link>
            <span className={styles.eyebrow}>Home care coordination</span>
          </div>

          <div className={signUpStyles.intro}>
            <h1 className={styles.headline}>
              Apply to work as a <span className={styles.headlineAccent}>caregiver.</span>
            </h1>

            <ol className={signUpStyles.steps}>
              <li>
                <span className={signUpStyles.stepTitle}>Create your account</span>
                <span className={signUpStyles.stepText}>
                  Your details go to a care manager as your application.
                </span>
              </li>
              <li>
                <span className={signUpStyles.stepTitle}>Add your certificates</span>
                <span className={signUpStyles.stepText}>
                  Sign in to upload them. They aren&apos;t needed to apply, and each is reviewed on its own.
                </span>
              </li>
              <li>
                <span className={signUpStyles.stepTitle}>Start work once approved</span>
                <span className={signUpStyles.stepText}>
                  When a care manager approves your application, your schedule opens.
                </span>
              </li>
            </ol>
          </div>
        </div>

        {submitted ? (
          <div className={styles.formColumn} role="status">
            <h2 className={styles.formTitle}>Application submitted</h2>
            <p className={styles.formSubtitle}>
              Sign in as {submitted} to add your certificates and see the care manager&apos;s decision. Your
              schedule stays locked until your application is approved.
            </p>
            <Link to="/" className={`${styles.submit} ${caregiverStyles.submitLink}`}>
              Continue to sign in
            </Link>
          </div>
        ) : (
          <form className={styles.formColumn} onSubmit={handleSubmit} aria-busy={busy} noValidate>
            <h2 className={styles.formTitle}>Apply as a caregiver</h2>
            <p className={styles.formSubtitle}>Step 1 of 3. Next: add your certificates.</p>

            <div className={styles.fields}>
              <div>
                <label className={styles.fieldLabel} htmlFor={ids.fullName}>
                  Full name
                </label>
                <input
                  {...fieldProps('fullName')}
                  name="name"
                  type="text"
                  autoComplete="name"
                  maxLength={100}
                  className={styles.textInput}
                  onChange={(event) => update('fullName', event.target.value)}
                />
                {fieldError('fullName')}
              </div>

              <div>
                <label className={styles.fieldLabel} htmlFor={ids.phone}>
                  Mobile number
                </label>
                <input
                  {...fieldProps('phone')}
                  name="tel"
                  type="tel"
                  autoComplete="tel"
                  inputMode="tel"
                  maxLength={15}
                  placeholder="9123 4567"
                  className={styles.textInput}
                  onChange={(event) => update('phone', event.target.value)}
                />
                {fieldError('phone')}
              </div>

              <div>
                <label className={styles.fieldLabel} htmlFor={ids.requestedSector}>
                  Preferred area · optional
                </label>
                <input
                  {...fieldProps('requestedSector')}
                  aria-describedby={
                    errors.requestedSector ? `${ids.requestedSector}-error` : `${ids.requestedSector}-hint`
                  }
                  name="area"
                  type="text"
                  maxLength={50}
                  placeholder="e.g. Ang Mo Kio"
                  className={styles.textInput}
                  onChange={(event) => update('requestedSector', event.target.value)}
                />
                {fieldError('requestedSector') ?? (
                  <p id={`${ids.requestedSector}-hint`} className={caregiverStyles.hint}>
                    Where you&apos;d like to work. A care manager assigns your actual area.
                  </p>
                )}
              </div>

              <fieldset className={caregiverStyles.languages} disabled={busy}>
                <legend className={styles.fieldLabel}>Languages spoken · optional</legend>
                <div className={caregiverStyles.languageGrid}>
                  {DIALECTS.map((dialect) => (
                    <label key={dialect} className={`${styles.checkboxRow} ${caregiverStyles.language}`}>
                      <input
                        type="checkbox"
                        className={styles.checkboxInput}
                        checked={values.dialects.includes(dialect)}
                        onChange={(event) => toggleDialect(dialect, event.target.checked)}
                      />
                      <span className={styles.checkboxBox} aria-hidden="true" />
                      <span className={styles.checkboxLabel}>{dialect}</span>
                    </label>
                  ))}
                </div>
              </fieldset>

              <div>
                <label className={styles.fieldLabel} htmlFor={ids.username}>
                  Choose a username
                </label>
                <input
                  {...fieldProps('username')}
                  name="username"
                  type="text"
                  autoComplete="username"
                  autoCapitalize="none"
                  spellCheck={false}
                  placeholder="e.g. lim.huimin"
                  className={styles.textInput}
                  onChange={(event) => update('username', event.target.value.trim().toLowerCase())}
                />
                {fieldError('username')}
              </div>

              <div>
                <label className={styles.fieldLabel} htmlFor={ids.password}>
                  Choose a password
                </label>
                <div className={styles.passwordBox}>
                  <input
                    {...fieldProps('password')}
                    name="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    placeholder="At least 8 characters"
                    className={styles.passwordInput}
                    onChange={(event) => update('password', event.target.value)}
                  />
                  <button
                    type="button"
                    className={styles.showToggle}
                    onClick={() => setShowPassword((value) => !value)}
                    aria-pressed={showPassword}
                    disabled={busy}
                  >
                    {showPassword ? 'hide' : 'show'}
                  </button>
                </div>
                {fieldError('password')}
              </div>

              <div>
                <label className={styles.fieldLabel} htmlFor={ids.confirm}>
                  Type the password again
                </label>
                <input
                  {...fieldProps('confirm')}
                  name="confirm-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  className={styles.textInput}
                  onChange={(event) => update('confirm', event.target.value)}
                />
                {fieldError('confirm')}
              </div>
            </div>

            {error && (
              <div className={styles.signInError} role="alert">
                {error}
              </div>
            )}

            <button type="submit" className={styles.submit} disabled={busy}>
              {busy ? 'Submitting your application…' : 'Submit application'}
            </button>

            <div className={styles.belowSubmit}>
              <span className={styles.forgotNote}>Already have an account?</span>
              <Link to="/" className={styles.forgotLink}>
                Sign in
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
