import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { initialiseCsrf } from '../../features/auth/api'
import { registerCaregiver } from '../../features/admission/api'
import type { ApplicationDetails } from '../../features/admission/api'
import { ApiError } from '../../shared/api/client'
import ApplicationFields from '../caregiver/ApplicationFields'
import { normalizedDetails, validateApplication } from '../../features/admission/validation'
import styles from '../caregiver/Caregiver.module.css'

export default function CaregiverSignUp() {
  const [details, setDetails] = useState<ApplicationDetails>({ fullName: '', phone: '', declaredDialects: null, requestedSector: null })
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [submitted, setSubmitted] = useState(false)
  async function submit(event: FormEvent) {
    event.preventDefault()
    if (busy) return
    const invalid = validateApplication(details)
    if (invalid) { setError(invalid); return }
    if (!/^[a-z0-9][a-z0-9._-]{2,63}$/.test(username)) { setError('Use 3 to 64 lower-case letters, digits, dots, dashes or underscores for your username.'); return }
    if (password.length < 8 || new TextEncoder().encode(password).length > 72) { setError('Use at least 8 characters and at most 72 UTF-8 bytes for your password.'); return }
    if (password !== confirm) { setError('The passwords do not match.'); return }
    setBusy(true); setError('')
    try {
      await initialiseCsrf()
      await registerCaregiver({ ...normalizedDetails(details), username, password })
      setSubmitted(true); setPassword(''); setConfirm('')
    } catch (failure) {
      setError(failure instanceof ApiError && failure.status < 500 ? failure.message : 'The result could not be confirmed. Try signing in with your chosen account before submitting again.')
    } finally { setBusy(false) }
  }
  return <main className={styles.page} style={{ maxWidth: 640, margin: 'auto', padding: 24 }}>
    <Link to="/">← Sign in</Link><h1>Apply as a caregiver</h1>
    <p>Admission approval and certificate review are separate. Certificates are optional and may be uploaded after signing in.</p>
    {submitted ? <div role="status" className={styles.readOnly}><h2>Application submitted</h2><p>Sign in as {username} to check your manager's decision or supplement your application. Work access is restricted until admission approval.</p><Link to="/">Continue to sign in</Link></div>
      : <form className={styles.formPanel} onSubmit={submit} noValidate><fieldset disabled={busy}>
        <ApplicationFields values={details} onChange={setDetails} />
        <label>Choose a username<input autoComplete="username" autoCapitalize="none" spellCheck={false} value={username} onChange={e => setUsername(e.target.value.trim().toLowerCase())} /></label>
        <label>Choose a password<input type="password" autoComplete="new-password" value={password} onChange={e => setPassword(e.target.value)} /></label>
        <label>Confirm password<input type="password" autoComplete="new-password" value={confirm} onChange={e => setConfirm(e.target.value)} /></label>
        {error && <p role="alert">{error}</p>}<button className={styles.button} type="submit">{busy ? 'Submitting…' : 'Submit application'}</button>
      </fieldset></form>}
  </main>
}
