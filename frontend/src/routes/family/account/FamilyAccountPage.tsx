import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { signOut } from '../../../features/auth/api'
import { useFamilyElders, useFamilyMe } from '../../../features/family-account/useFamilyAccount'
import { ageOn, singaporeToday } from '../../../features/schedule/presentation'
import { ApiError } from '../../../shared/api/client'
import styles from './FamilyAccount.module.css'
import { useIsDesktop } from '../components/useIsDesktop'

function initials(name: string) {
  return name.trim().split(/\s+/).slice(0, 2).map((part) => Array.from(part)[0]?.toUpperCase()).join('')
}

/**
 * The family member's account: who they follow, how they are notified, who to call, and the only Sign out.
 */
export function FamilyAccountPage() {
  const navigate = useNavigate()
  const desktop = useIsDesktop()
  const me = useFamilyMe()
  const elders = useFamilyElders()
  const [today] = useState(singaporeToday)
  const [signingOut, setSigningOut] = useState(false)
  const [signOutError, setSignOutError] = useState('')

  async function handleSignOut() {
    if (signingOut) return
    setSigningOut(true)
    setSignOutError('')
    try {
      await signOut()
      navigate('/', { replace: true })
    } catch (failure) {
      // An already-expired session means the family member is signed out anyway.
      if (failure instanceof ApiError && failure.status === 401) {
        navigate('/', { replace: true })
        return
      }
      setSignOutError('Unable to sign out. Please try again.')
    } finally {
      setSigningOut(false)
    }
  }

  // The landing page is the only sign-in screen.
  if ([me.error, elders.error].some((error) => error instanceof ApiError && error.status === 401)) {
    return <Navigate to="/" replace />
  }

  const name = me.data?.displayName.trim() || me.data?.username || ''
  const following = <>
    <section className={styles.section} aria-labelledby="account-following">
      <h2 id="account-following" className={styles.label}>Following</h2>
      <div className={styles.card}>
        {elders.isPending && <p className={styles.row} role="status">Loading linked elders…</p>}
        {elders.isError && <div className={styles.row} role="alert">
          <span>Unable to load linked elders.</span>
          <button className={styles.retry} onClick={() => void elders.refetch()}>Try again</button>
        </div>}
        {elders.data?.length === 0 && <p className={styles.row}>No linked elders yet.</p>}
        {elders.data?.map((elder) => {
          const age = ageOn(elder.dateOfBirth, today)
          return <div className={styles.row} key={elder.id}>
            <div>
              <Link className={styles.action} to={`/family/elders/${elder.id}`}>{elder.fullName}</Link>
              <p className={styles.rowSub}>{[age === null ? null : String(age), elder.sector].filter(Boolean).join(' · ') || 'Linked elder'}</p>
            </div>
            <span className={styles.pill}>LINKED</span>
          </div>
        })}
        <Link className={`${styles.row} ${styles.action}`} to="/family/elders">
          <span>My elders</span><span aria-hidden="true">→</span>
        </Link>
        <Link className={`${styles.row} ${styles.action}`} to="/family/family-bindings">
          <span>Review binding requests</span><span aria-hidden="true">→</span>
        </Link>
      </div>
    </section>
  </>
  const notifications = <>
    <section className={styles.section} aria-labelledby="account-notifications">
      <h2 id="account-notifications" className={styles.label}>Notifications</h2>
      <dl className={styles.card}>
        <div className={styles.row}><dt>Urgent alerts</dt><dd>App and email</dd></div>
        <div className={styles.row}><dt>Shift changes and spot checks</dt><dd>App</dd></div>
        <div className={styles.row}><dt>Weekly summary</dt><dd>Mondays, 09:00</dd></div>
      </dl>
    </section>
  </>
  const help = <>
    <section className={styles.section} aria-labelledby="account-help">
      <h2 id="account-help" className={styles.label}>Help</h2>
      <div className={styles.card}>
        <div className={styles.row}>
          <div>
            <p className={styles.helpTitle}>Call the care manager</p>
            <p className={styles.rowSub}>Your care agency's care manager</p>
          </div>
        </div>
      </div>
    </section>
  </>
  const signOutBlock = <>
    <section className={styles.section}>
      <button className={styles.signOut} onClick={handleSignOut} disabled={signingOut}>
        {signingOut ? 'Signing out…' : 'Sign out'}
      </button>
      {signOutError && <p className={styles.error} role="alert">{signOutError}</p>}
      <p className={styles.note}>This phone stops receiving alerts. An urgent alert you don't acknowledge goes to the next family member.</p>
    </section>
  </>

  return <div className={styles.account}>
    <header className={styles.header}>
      {name && <span className={styles.avatar} aria-hidden="true">{initials(name)}</span>}
      <div>
        <p className={styles.eyebrow}>Account</p>
        <h1>{me.isPending ? 'Loading…' : name || 'Your account'}</h1>
      </div>
    </header>

    {desktop ? (
      // Desktop: the same two-column split as the other tabs; what you follow and how you hear on the left.
      <div className={styles.split}>
        <div className={styles.column}>{following}{notifications}</div>
        <div className={styles.column}>{help}{signOutBlock}</div>
      </div>
    ) : <>{following}{notifications}{help}{signOutBlock}</>}
  </div>
}
