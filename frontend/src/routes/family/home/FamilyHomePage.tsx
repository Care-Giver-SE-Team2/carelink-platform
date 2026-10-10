import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useFamilySchedule } from '../../../features/schedule/useFamilySchedule'
import { useCaregiverDetails } from '../../../features/schedule/useCaregiverDetails'
import { credentialPresentation } from '../../../features/schedule/credentialPresentation'
import { ageOn, serviceLabel, singaporeToday, visitStatusLabels, visitTime, visitWeekday, weekStart } from '../../../features/schedule/presentation'
import type { FamilyVisit } from '../../../features/schedule/types'
import { useFamilyVisitTasks } from '../../../features/visits/useFamilyVisitTasks'
import { visitTime as localTime } from '../../../features/absences/presentation'
import { ScheduleFeedback } from '../schedule/ScheduleFeedback'
import { useSelectedElder } from '../components/selectedElder'
import { useIsDesktop } from '../components/useIsDesktop'
import { usePendingDecisions } from '../components/usePendingDecisions'
import styles from './FamilyHome.module.css'

const liveStates: FamilyVisit['status'][] = ['ARRIVED', 'IN_PROGRESS']
const closedStates: FamilyVisit['status'][] = ['COMPLETED', 'VERIFIED', 'AUTO_CLOSED']

/** The visit under way, or failing that the next scheduled one this week. */
function pickVisit(visits: FamilyVisit[], now: number) {
  const live = visits.find((visit) => liveStates.includes(visit.status))
  const next = live ? undefined : visits
    .filter((visit) => visit.status === 'SCHEDULED' && new Date(visit.scheduledStart).getTime() >= now)
    .sort((a, b) => a.scheduledStart.localeCompare(b.scheduledStart))[0]
  return { live, visit: live ?? next }
}

/** "Tuesday 6 October" for a Singapore calendar date. */
function longDate(date: string) {
  const parts = new Intl.DateTimeFormat('en-SG', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })
    .formatToParts(new Date(`${date}T00:00:00Z`))
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((item) => item.type === type)?.value
  return `${part('weekday')} ${part('day')} ${part('month')}`
}

/**
 * The family member's landing screen: anything waiting on their answer, the visit under way (or the
 * next one), and how this week is going.
 */
export function FamilyHomePage() {
  const [today] = useState(singaporeToday)
  const [now] = useState(() => Date.now())
  const desktop = useIsDesktop()
  const { elderId, setElderId } = useSelectedElder()
  const { resource, refresh, invalidateAccess } = useFamilySchedule({ elderId, week: weekStart(today), page: 0 })
  const data = resource.status === 'success' ? resource.data : null
  const elder = data?.elders.find((item) => item.id === data.selectedElderId) ?? null
  const age = ageOn(elder?.dateOfBirth ?? null, today)
  const shown = data?.visits ? pickVisit(data.visits.items, now) : null

  return <div className={styles.home}>
    <header className={styles.header}>
      <div>
        <p className={styles.eyebrow}>{desktop ? 'Home' : 'Family'}</p>
        <h1>{elder?.fullName ?? 'Your family'}{desktop && elder && `, ${longDate(today)}`}</h1>
        {elder && !desktop && <p className={styles.meta}>{[age === null ? null : `${age}`, elder.sector].filter(Boolean).join(' · ') || 'Linked elder'}</p>}
      </div>
      {elder && !desktop && <span className={styles.tag}>Linked</span>}
    </header>
    {resource.status === 'loading' && <p className={styles.loading} role="status">Loading today's care…</p>}
    {resource.status === 'error' && <ScheduleFeedback error={resource.error} onRetry={refresh}
      onResetAccess={() => { setElderId(null); refresh() }} />}
    {data && data.elders.length > 1 && !desktop && <div className={styles.picker}>
      <label htmlFor="home-elder">Care for</label>
      <select id="home-elder" value={data.selectedElderId ?? ''} onChange={(event) => setElderId(Number(event.target.value))}>
        {data.elders.map((item) => <option key={item.id} value={item.id}>{item.fullName}</option>)}
      </select>
    </div>}
    <NeedsAnswer showElder={(data?.elders.length ?? 0) > 1} />
    {data && data.elders.length === 0 && <section className={styles.empty}>
      <h2>No linked elders yet</h2>
      <p>Once a care application is approved and your link is confirmed, today's care appears here.</p>
      <Link className={styles.primary} to="/family/service-applications">View my applications</Link>
    </section>}
    {data?.visits && shown && <div className={styles.columns}>
      <div className={styles.column}>
        <NowSection visits={data.visits.items} live={shown.live} visit={shown.visit} onAccessError={invalidateAccess} />
        {desktop && <LaterToday visits={data.visits.items} after={shown.visit} today={today} now={now}
          primary={elder?.primaryCaregiverId != null && elder.primaryCaregiverName
            ? { id: elder.primaryCaregiverId, name: elder.primaryCaregiverName } : null} />}
      </div>
      <section className={styles.column} aria-labelledby="home-week">
        <h2 id="home-week" className={styles.sectionLabel}>This week</h2>
        <dl className={styles.rows}>
          <div>
            <dt>Visits completed</dt>
            <dd>{data.visits.items.filter((visit) => closedStates.includes(visit.status)).length} of {data.visits.totalElements}</dd>
          </div>
          <div>
            <dt>Exceptions raised</dt>
            <dd>{data.visits.items.filter((visit) => visit.status === 'EXCEPTION').length || 'none'}</dd>
          </div>
          <div>
            <dt>Weekly schedule</dt>
            <dd><Link className={styles.linkAction} to="/family/schedule">view →</Link></dd>
          </div>
          <div>
            <dt>Weekly summary</dt>
            <dd><Link className={styles.linkAction} to={`/family/reports/weekly?elderId=${data.selectedElderId}`}>read →</Link></dd>
          </div>
        </dl>
      </section>
    </div>}
  </div>
}

/**
 * What the care team is waiting on the family to decide, soonest deadline first, each row opening the
 * page where it is answered. Hidden when nothing is waiting.
 */
function NeedsAnswer({ showElder }: { showElder: boolean }) {
  const pending = usePendingDecisions()
  if (pending.total === 0) return null
  const elder = (name: string) => showElder ? `${name} · ` : ''
  const changes = [...pending.changes].sort((a, b) => (a.respondBy ?? '~').localeCompare(b.respondBy ?? '~'))
  return <section className={styles.answer} aria-labelledby="home-answer">
    <h2 id="home-answer" className={styles.sectionLabel}>Needs your answer · {pending.total}</h2>
    <ul className={styles.answerList}>
      {changes.map((change) => <li key={`change-${change.id}`}>
        <Link to="/family/changes">
          <span className={styles.answerKind}>Visit change</span>
          <span className={styles.answerTitle}>{change.usualCaregiverName} can't come on {localTime(change.visitStart)}</span>
          <span className={styles.answerMeta}>
            {elder(change.elderName)}{change.respondBy ? <strong>Reply by {localTime(change.respondBy)}</strong> : 'Choose who comes instead'}
          </span>
        </Link>
      </li>)}
      {pending.spotChecks.map((check) => <li key={`check-${check.id}`}>
        <Link to="/family/spot-checks">
          <span className={styles.answerKind}>Spot check</span>
          <span className={styles.answerTitle}>A manager asks to join {check.caregiverName}'s visit</span>
          <span className={styles.answerMeta}>{elder(check.elderName)}{localTime(check.visitTime)}</span>
        </Link>
      </li>)}
      {pending.requests.map((request) => <li key={`request-${request.id}`}>
        <Link to="/family/extra-services">
          <span className={styles.answerKind}>Extra service</span>
          <span className={styles.answerTitle}>Approve {request.serviceName}?</span>
          <span className={styles.answerMeta}>{request.requestedSchedule ? `Requested for ${localTime(request.requestedSchedule)}` : 'No time requested'}</span>
        </Link>
      </li>)}
    </ul>
  </section>
}

/** Desktop only: the rest of today's visits after the one shown above. Hidden when there are none. */
function LaterToday({ visits, after, today, now, primary }: {
  visits: FamilyVisit[]; after?: FamilyVisit; today: string; now: number; primary: { id: number; name: string } | null
}) {
  const from = after ? after.scheduledStart : new Date(now).toISOString()
  const later = visits
    .filter((visit) => visit.id !== after?.id && visit.status !== 'CANCELLED'
      && singaporeToday(new Date(visit.scheduledStart)) === today && visit.scheduledStart > from)
    .sort((a, b) => a.scheduledStart.localeCompare(b.scheduledStart))
  if (later.length === 0) return null
  return <section aria-labelledby="home-later">
    <h2 id="home-later" className={styles.sectionLabel}>Later today</h2>
    <ul className={styles.later}>
      {later.map((visit) => <li key={visit.id}>
        <span className={styles.laterTime}>
          {visitTime(visit.scheduledStart)}{visit.scheduledEnd && ` – ${visitTime(visit.scheduledEnd)}`}
        </span>
        <span>{serviceLabel(visit.serviceType)}</span>
        <span className={styles.laterCaregiver}>{visit.caregiverId === null ? 'Awaiting caregiver'
          : visit.caregiverId === primary?.id ? primary.name : 'Caregiver assigned'}</span>
      </li>)}
    </ul>
  </section>
}

function NowSection({ visits, live, visit, onAccessError }: {
  visits: FamilyVisit[]; live?: FamilyVisit; visit?: FamilyVisit; onAccessError: (error: unknown) => void
}) {

  return <section aria-labelledby="home-now">
    <h2 id="home-now" className={styles.sectionLabel}>{live ? 'Happening now' : 'Next visit'}</h2>
    {!visit ? <p className={styles.card}>No more visits this week.</p> : <article className={styles.card} aria-label={serviceLabel(visit.serviceType)}>
      <div className={styles.liveTop}>
        <span className={styles.range}>
          {!live && `${visitWeekday(visit.scheduledStart)} `}
          {visitTime(visit.scheduledStart)}{visit.scheduledEnd && ` – ${visitTime(visit.scheduledEnd)}`}
        </span>
        <span className={styles.state} data-live={!!live}>
          <span aria-hidden="true" />{live ? visitStatusLabels[visit.status] : 'Next'}
        </span>
      </div>
      <h3 className={styles.service}>{serviceLabel(visit.serviceType)}</h3>
      {visit.caregiverId === null
        ? <p className={styles.unassigned}>Caregiver awaiting assignment</p>
        : <CaregiverSummary caregiverId={visit.caregiverId}
          visitsThisWeek={visits.filter((item) => item.caregiverId === visit.caregiverId).length}
          onAccessError={onAccessError} />}
      {live && <LiveFacts visit={visit} />}
      <Link className={styles.cardLink} to={`/family/visits/${visit.id}`}>View visit progress →</Link>
    </article>}
  </section>
}

function LiveFacts({ visit }: { visit: FamilyVisit }) {
  const tasks = useFamilyVisitTasks(visit.id)
  const done = tasks.data?.filter((task) => task.status === 'DONE').length
  return <dl className={styles.facts}>
    <div><dt>arrived</dt><dd>{visit.checkedInAt ? visitTime(visit.checkedInAt) : 'not recorded'}</dd></div>
    {tasks.data && tasks.data.length > 0 && <div><dt>tasks done</dt><dd>{done} of {tasks.data.length}</dd></div>}
    <div><dt>expected close</dt><dd>{visit.scheduledEnd ? visitTime(visit.scheduledEnd) : 'to be confirmed'}</dd></div>
  </dl>
}

function CaregiverSummary({ caregiverId, visitsThisWeek, onAccessError }: {
  caregiverId: number; visitsThisWeek: number; onAccessError: (error: unknown) => void
}) {
  const { profile, credentials } = useCaregiverDetails(caregiverId, onAccessError)
  if (profile.status !== 'success') return null
  const name = profile.data.fullName.trim()
  const valid = credentials.status === 'success'
    ? credentials.data.filter((credential) => ['valid', 'warning'].includes(credentialPresentation(credential).tone))
    : []
  return <div className={styles.caregiver}>
    <div className={styles.person}>
      <span className={styles.avatar} aria-hidden="true">
        {name.split(/\s+/).slice(0, 2).map((part) => Array.from(part)[0]).join('')}
      </span>
      <div>
        <p className={styles.name}>{name}</p>
        <p className={styles.personMeta}>{visitsThisWeek} {visitsThisWeek === 1 ? 'visit' : 'visits'} this week</p>
      </div>
    </div>
    {(valid.length > 0 || profile.data.dialects.length > 0) && <ul className={styles.tags} aria-label="Qualifications and languages">
      {valid.map((credential) => <li key={credential.id}>{credential.credentialTypeName} · valid</li>)}
      {profile.data.dialects.length > 0 && <li>{profile.data.dialects.join(' · ')}</li>}
    </ul>}
  </div>
}
