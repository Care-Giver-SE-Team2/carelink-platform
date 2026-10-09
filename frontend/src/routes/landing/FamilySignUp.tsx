import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'

import { initialiseCsrf, signInWithSession } from '../../features/auth/api'
import { ApiError } from '../../shared/api/client'
import { registerFamily } from '../../shared/api/profile'
import styles from './Landing.module.css'
import signUpStyles from './FamilySignUp.module.css'

type Values = { fullName: string; phone: string; username: string; password: string }
type Errors = Partial<Record<keyof Values, string>>

/** Mirrors the format checks on profile.controller.dto.FamilyRegistrationRequest. */
function validate(values: Values): Errors {
  const errors: Errors = {}
  const fullName = values.fullName.trim()
  if (!fullName) errors.fullName = 'Enter your full name.'
  else if ([...fullName].length > 100) errors.fullName = 'Use 100 characters or fewer.'
  if (!/^\+?[0-9 ()-]{6,20}$/.test(values.phone.trim())) errors.phone = 'Enter a phone number.'
  if (!/^[a-z0-9][a-z0-9._-]{2,63}$/.test(values.username))
    errors.username = 'Use 3 to 64 lower-case letters, digits, dots, dashes or underscores.'
  if (values.password.length < 8) errors.password = 'Use at least 8 characters.'
  else if (new TextEncoder().encode(values.password).length > 72) errors.password = 'Use 72 characters or fewer.'
  return errors
}

/**
 * Public sign-up for a family member applying for care for an elder. Reached from the landing
 * page's "No account yet?" card, so it sits outside every RequireRole. Creates a FAMILY account,
 * signs in with it, and continues to the care application form.
 */
export default function FamilySignUp() {
  const navigate = useNavigate()
  const ids = { fullName: useId(), phone: useId(), username: useId(), password: useId() }
  const [values, setValues] = useState<Values>({ fullName: '', phone: '', username: '', password: '' })
  const [errors, setErrors] = useState<Errors>({})
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  function update(field: keyof Values, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
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
      await registerFamily({
        username: values.username,
        password: values.password,
        fullName: values.fullName.trim(),
        phone: values.phone.trim(),
      })
    } catch (failure) {
      setBusy(false)
      if (failure instanceof ApiError && failure.status === 409) {
        setErrors({ username: 'That username is taken. Choose another.' })
      } else if (failure instanceof ApiError && failure.status === 400) {
        setError('Please check your details and try again.')
      } else {
        setError('Unable to create your account. Check your connection and try again.')
      }
      return
    }

    try {
      await signInWithSession({ username: values.username, password: values.password })
      navigate('/family/intake/new', { replace: true })
    } catch {
      setBusy(false)
      setError('Your account was created, but signing in failed. Sign in from the home page to continue.')
    }
  }

  function fieldProps(field: keyof Values) {
    return {
      id: ids[field],
      value: values[field],
      disabled: busy,
      'aria-invalid': !!errors[field],
      'aria-describedby': errors[field] ? `${ids[field]}-error` : undefined,
    }
  }

  function fieldError(field: keyof Values) {
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
              Apply for care for a{' '}
              <span className={styles.headlineAccent}>family member.</span>
            </h1>

            <ol className={signUpStyles.steps}>
              <li>
                <span className={signUpStyles.stepTitle}>Create your family account</span>
                <span className={signUpStyles.stepText}>
                  You use it to send the application and follow its progress.
                </span>
              </li>
              <li>
                <span className={signUpStyles.stepTitle}>Tell us about your loved one</span>
                <span className={signUpStyles.stepText}>
                  Their address, mobility and the care they need. Takes about ten minutes.
                </span>
              </li>
              <li>
                <span className={signUpStyles.stepTitle}>Hear back from a care manager</span>
                <span className={signUpStyles.stepText}>
                  A care manager replies within two working days.
                </span>
              </li>
            </ol>
          </div>
        </div>

        <form className={styles.formColumn} onSubmit={handleSubmit} aria-busy={busy} noValidate>
          <h2 className={styles.formTitle}>Create your family account</h2>
          <p className={styles.formSubtitle}>Step 1 of 2. Next: the care application.</p>

          <div className={styles.fields}>
            <div>
              <label className={styles.fieldLabel} htmlFor={ids.fullName}>
                Your full name
              </label>
              <input
                {...fieldProps('fullName')}
                name="name"
                type="text"
                autoComplete="name"
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
                placeholder="9123 4567"
                className={styles.textInput}
                onChange={(event) => update('phone', event.target.value)}
              />
              {fieldError('phone')}
            </div>

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
                placeholder="e.g. lim.weiling"
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
          </div>

          {error && (
            <div className={styles.signInError} role="alert">
              {error}
            </div>
          )}

          <button type="submit" className={styles.submit} disabled={busy}>
            {busy ? 'Creating your account…' : 'Create account and continue'}
          </button>

          <div className={styles.belowSubmit}>
            <span className={styles.forgotNote}>Already have an account?</span>
            <Link to="/" className={styles.forgotLink}>
              Sign in
            </Link>
          </div>
        </form>
      </div>
    </div>
  )
}
