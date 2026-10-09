import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { initialiseCsrf, signInWithSession } from '../../features/auth/api'
import { ApiError } from '../../shared/api/client'
import { registerElder } from '../../shared/api/elder-registration'
import styles from './Landing.module.css'

export default function ElderSignUp() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return
    if (!/^[a-z0-9][a-z0-9._-]{2,63}$/.test(username)) {
      setError('Username must be 3–64 lower-case letters, numbers, dots, dashes or underscores.')
      return
    }
    if (password.length < 8 || new TextEncoder().encode(password).length > 72) {
      setError('Password must be at least 8 characters and at most 72 bytes.')
      return
    }
    if (password !== confirm) { setError('Passwords do not match.'); return }
    setBusy(true)
    setError('')
    try {
      await initialiseCsrf()
      await registerElder({ username, password })
    } catch (failure) {
      setError(failure instanceof ApiError && failure.status === 409
        ? 'Username is already taken.' : 'Could not create account. Please try again.')
      setBusy(false)
      return
    }
    try {
      await signInWithSession({ username, password })
      navigate('/elder', { replace: true })
    } catch {
      setError('Account created. Please return to the home page and sign in.')
      setBusy(false)
    }
  }

  return <div className={styles.page}>
    <div className={styles.canvas}>
      <div className={styles.leftCol}>
        <div className={styles.header}>
          <Link to="/" className={styles.wordmark}>CareLink</Link>
          <span className={styles.eyebrow}>Home care coordination</span>
        </div>
        <div className={styles.pitch}>
          <h1 className={styles.headline}>Create your <span className={styles.headlineAccent}>elder account.</span></h1>
          <p className={styles.dek}>Only a username and password are needed. Your basic account is ready immediately; your family can add personal details later.</p>
        </div>
      </div>
      <form className={styles.formColumn} onSubmit={submit}>
        <h2 className={styles.formTitle}>Register as an elder</h2>
        <p className={styles.formSubtitle}>Create a login to access CareLink.</p>
        <div className={styles.fields}>
          <div><label className={styles.fieldLabel} htmlFor="elder-username">Username</label>
            <input id="elder-username" className={styles.textInput} value={username} onChange={e=>setUsername(e.target.value)} autoComplete="username" required disabled={busy}/></div>
          <div><label className={styles.fieldLabel} htmlFor="elder-password">Password</label>
            <input id="elder-password" className={styles.textInput} type="password" value={password} onChange={e=>setPassword(e.target.value)} autoComplete="new-password" required disabled={busy}/></div>
          <div><label className={styles.fieldLabel} htmlFor="elder-confirm">Confirm password</label>
            <input id="elder-confirm" className={styles.textInput} type="password" value={confirm} onChange={e=>setConfirm(e.target.value)} autoComplete="new-password" required disabled={busy}/></div>
        </div>
        {error && <p role="alert">{error}</p>}
        <button type="submit" className={styles.submit} disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
        <Link to="/">Back to sign in</Link>
      </form>
    </div>
  </div>
}
