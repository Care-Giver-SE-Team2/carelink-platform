import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { collectCredentials } from '../../../features/provider-platform/api'
import { ApiError } from '../../../features/provider-platform/errors'
import { formatLong } from '../../../features/provider-platform/format'
import type { Application } from '../../../features/provider-platform/types'
import { Banner, Button, Facts, Field, SectionLabel, StateTag, TextInput } from '../components/ui'
import { WorkbenchShell } from '../components/WorkbenchShell'
import { useWorkbench } from '../workbenchContext'
import styles from './Status.module.css'

/**
 * Application status (PT01). Signed out, the provider signs in with the email and password it
 * chose when applying. Signed in, it sees its application: pending says how long review takes
 * and can be checked again; approved unlocks the nav and leads to the credentials, which are
 * handed over once; rejected shows CareLink's reason; suspended says so.
 */
export default function Status() {
  const { application, restoring } = useWorkbench()
  return (
    <WorkbenchShell>
      <div className={styles.page}>
        <h1 className={styles.title}>Application status</h1>
        {application ? <SignedIn application={application} /> : restoring ? null : <SignIn />}
      </div>
    </WorkbenchShell>
  )
}

function SignIn() {
  const { signIn } = useWorkbench()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [refused, setRefused] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setRefused(false)
    setFailure(null)
    try {
      await signIn(email, password)
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) setRefused(true)
      else setFailure(err instanceof Error ? err.message : "Couldn't reach CareLink.")
      setBusy(false)
    }
  }

  return (
    <>
      <p className={styles.sub}>Sign in with the email and password you chose when you applied.</p>
      <form className={styles.signIn} onSubmit={submit}>
        <Field label="Email" htmlFor="signin-email">
          <TextInput
            id="signin-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={busy}
            invalid={refused}
            autoComplete="username"
          />
        </Field>
        <Field label="Password" htmlFor="signin-password">
          <TextInput
            id="signin-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            disabled={busy}
            invalid={refused}
            autoComplete="current-password"
          />
        </Field>
        <Button type="submit" variant="primary" busy={busy && 'Signing in…'} disabled={!email.trim() || !password}>
          Sign in
        </Button>
      </form>
      {refused && (
        <Banner tone="danger" title="Email or password is wrong" role="alert">
          Check both and try again. Use the email you applied with.
        </Banner>
      )}
      {failure && (
        <Banner tone="danger" title="Couldn't reach CareLink" role="alert">
          {failure}
        </Banner>
      )}
      <p className={styles.newHere}>
        Not applied yet? <Link to="/provider/apply">Apply to join the Open Platform →</Link>
      </p>
    </>
  )
}

function SignedIn({ application }: { application: Application }) {
  const navigate = useNavigate()
  const { session, setCredentials, refreshApplication } = useWorkbench()
  const [checking, setChecking] = useState(false)
  const [collecting, setCollecting] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  async function checkAgain() {
    setChecking(true)
    setFailure(null)
    try {
      await refreshApplication()
    } catch (err) {
      setFailure(err instanceof Error ? err.message : "Couldn't reach CareLink.")
    } finally {
      setChecking(false)
    }
  }

  async function viewCredentials() {
    setCollecting(true)
    setFailure(null)
    try {
      setCredentials(await collectCredentials(session!))
      navigate('/provider/credentials')
    } catch (err) {
      if (err instanceof ApiError && err.status === 410) {
        navigate('/provider/credentials')
        return
      }
      setFailure(err instanceof Error ? err.message : "Couldn't reach CareLink.")
      setCollecting(false)
    }
  }

  return (
    <>
      {application.status === 'approved' && application.suspended && (
        <Banner tone="danger" title="CareLink has suspended your provider account" role="alert">
          Your access tokens don't work and no jobs are sent to you until CareLink reinstates you. Contact your CareLink partner manager.
        </Banner>
      )}
      {application.status === 'approved' && !application.suspended && (
        <div className={styles.approved}>
          <div className={styles.approvedText}>
            <div className={styles.approvedTitle}>You're approved</div>
            <div className={styles.approvedBody}>Your credentials are ready. They're shown once, so have a password manager open.</div>
          </div>
          <Button variant="primary" busy={collecting && 'Opening…'} onClick={() => void viewCredentials()}>
            View credentials →
          </Button>
        </div>
      )}
      {application.status === 'pending' && (
        <div>
          <Button busy={checking && 'Checking…'} onClick={() => void checkAgain()}>
            Check again
          </Button>
        </div>
      )}
      {failure && (
        <Banner tone="danger" title="Couldn't reach CareLink" role="alert">
          {failure}
        </Banner>
      )}
      <ApplicationCard application={application} />
    </>
  )
}

function ApplicationCard({ application }: { application: Application }) {
  const when =
    application.status === 'pending'
      ? `Submitted ${formatLong(application.submittedAt)} · usually reviewed within 2 working days`
      : `${application.status === 'approved' ? 'Approved' : 'Reviewed'} ${formatLong(application.reviewedAt ?? application.submittedAt)}`

  return (
    <section className={styles.card} aria-label={`Application ${application.id}`}>
      <div className={styles.cardHead}>
        <div className={styles.cardId}>
          <span className={styles.id}>{application.id}</span>
          <span className={styles.when}>{when}</span>
        </div>
        <StateTag kind={application.status} />
      </div>
      {application.status === 'rejected' && application.reason && (
        <div className={styles.reason}>
          <SectionLabel>Reason from CareLink</SectionLabel>
          <div>{application.reason}</div>
          <div className={styles.restart}>
            <Link to="/provider/apply">Start a new application →</Link>
          </div>
        </div>
      )}
      <Facts
        className={styles.details}
        rows={[
          { label: 'Organisation', value: application.organisationName },
          { label: 'Contact', value: application.contactName },
          { label: 'Email', value: application.contactEmail },
          { label: 'Phone', value: application.contactPhone },
          { label: 'Services', value: application.services.join(', ') },
          { label: 'Webhook URL', value: application.webhookUrl, mono: true },
        ]}
      />
    </section>
  )
}
