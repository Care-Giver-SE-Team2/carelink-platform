import { useId, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { initialiseCsrf, signInWithSession } from '../../features/auth/api'
import { ApiError } from '../../shared/api/client'
import { registerElder } from '../../shared/api/elder-registration'
import { serverFieldErrors } from '../../shared/validation/serverErrors'
import { normalizeName, personNameError } from '../../shared/validation/sg'
import styles from './Landing.module.css'
import signUpStyles from './FamilySignUp.module.css'

type Values = { fullName: string; username: string; password: string; confirm: string }
type Errors = Partial<Record<keyof Values, string>>

/** Mirrors the format checks on profile.controller.dto.ElderAccountRegistrationRequest. */
function validate(values: Values): Errors {
  const errors: Errors = {}
  errors.fullName = personNameError(values.fullName, 'Enter your full name.')
  if (!/^[a-z0-9][a-z0-9._-]{2,63}$/.test(values.username))
    errors.username = 'Use 3 to 64 lower-case letters, digits, dots, dashes or underscores.'
  if (values.password.length < 8) errors.password = 'Use at least 8 characters.'
  else if (new TextEncoder().encode(values.password).length > 72) errors.password = 'Use 72 characters or fewer.'
  else if (values.confirm !== values.password) errors.confirm = 'The passwords do not match.'
  return errors
}

/**
 * Public sign-up for an elder, reached from the landing page's "No account yet?" card. Creates an
 * ELDER account under the elder's own name, signs in with it and opens the elder home, where
 * "My family" links the family members who will arrange care.
 */
export default function ElderSignUp() {
  const navigate = useNavigate()
  const ids = { fullName: useId(), username: useId(), password: useId(), confirm: useId() }
  const [values, setValues] = useState<Values>({ fullName: '', username: '', password: '', confirm: '' })
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
      await registerElder({ fullName: normalizeName(values.fullName), username: values.username, password: values.password })
    } catch (failure) {
      setBusy(false)
      if (failure instanceof ApiError && failure.status === 409) {
        setErrors({ username: 'That username is taken. Choose another.' })
      } else if (failure instanceof ApiError && failure.status === 400) {
        const fields = serverFieldErrors(failure)
        if (Object.keys(fields).length) setErrors(fields)
        else setError('Please check your details and try again.')
      } else {
        setError('Unable to create your account. Check your connection and try again.')
      }
      return
    }

    try {
      await signInWithSession({ username: values.username, password: values.password })
      navigate('/elder', { replace: true })
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
              Create your <span className={styles.headlineAccent}>elder account.</span>
            </h1>

            <ol className={signUpStyles.steps}>
              <li>
                <span className={signUpStyles.stepTitle}>Create your account</span>
                <span className={signUpStyles.stepText}>
                  Your name is what your family and care team will see.
                </span>
              </li>
              <li>
                <span className={signUpStyles.stepTitle}>Link your family</span>
                <span className={signUpStyles.stepText}>
                  Under My family, enter their CareLink username. They accept your request.
                </span>
              </li>
              <li>
                <span className={signUpStyles.stepTitle}>Your family arranges care</span>
                <span className={signUpStyles.stepText}>
                  They add your other details and apply for the care you need.
                </span>
              </li>
            </ol>
          </div>
        </div>

        <form className={styles.formColumn} onSubmit={handleSubmit} aria-busy={busy} noValidate>
          <h2 className={styles.formTitle}>Create your elder account</h2>
          <p className={styles.formSubtitle}>Step 1 of 3. Next: link your family.</p>

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
                placeholder="e.g. tan.ahmah"
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
