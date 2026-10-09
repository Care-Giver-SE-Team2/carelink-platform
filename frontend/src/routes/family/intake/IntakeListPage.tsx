import { Link, useSearchParams } from 'react-router-dom'
import { intakeDate, intakeStatus, statusLabels } from '../../../features/intake/presentation'
import type { IntakeStatus } from '../../../features/intake/types'
import { useIntakeApplications } from '../../../features/intake/useIntakeQueries'
import { IntakeIcon, IntakeLoading, StatusBadge } from './IntakeLayout'
import { IntakeFeedback } from './IntakeFeedback'
import styles from './FamilyIntake.module.css'
import { useIsDesktop } from '../components/useIsDesktop'

/**
 * Lists only the signed-in family's applications, with status filters and pagination.
 * @author Wang Zhili
 */
export function IntakeListPage() {
  const [params, setParams] = useSearchParams()
  const candidate = Number(params.get('page') ?? 0)
  const page =
    Number.isInteger(candidate) && candidate >= 0 && candidate <= 2147483647 ? candidate : 0
  const status = intakeStatus(params.get('status'))
  const { resource, refresh } = useIntakeApplications({ page, size: 20, status })
  const changeQuery = (nextPage: number, nextStatus = status) => {
    const next = new URLSearchParams()
    if (nextStatus) next.set('status', nextStatus)
    if (nextPage > 0) next.set('page', String(nextPage))
    setParams(next)
  }
  const backSearch = params.toString() ? '?' + params.toString() : ''
  const desktop = useIsDesktop()
  const list = (
    <>
      <div className={styles.toolbar}>
        <span aria-live="polite">
          {resource.status === 'success'
            ? resource.data.totalElements +
              (resource.data.totalElements === 1 ? ' application' : ' applications')
            : 'Your applications'}
        </span>
        <label className={styles.srOnly} htmlFor="intake-status">Filter by status</label>
        <select
          id="intake-status"
          className={styles.statusFilter}
          value={status ?? ''}
          onChange={(event) => {
            const next = intakeStatus(event.target.value)
            if (next) changeQuery(0, next)
            else setParams({})
          }}
        >
          <option value="">All applications</option>
          {(Object.keys(statusLabels) as IntakeStatus[]).map((value) => (
            <option key={value} value={value}>{statusLabels[value]}</option>
          ))}
        </select>
        <button
          className={styles.refreshButton}
          onClick={refresh}
          disabled={resource.status === 'loading'}
        >
          Refresh
        </button>
      </div>
      {resource.status === 'loading' && <IntakeLoading />}
      {resource.status === 'error' && <IntakeFeedback error={resource.error} onRetry={refresh} />}
      {resource.status === 'success' && (
        <>
          {resource.data.items.length === 0 ? (
            <section className={styles.state}>
              <span className={styles.emptyIcon}>
                <IntakeIcon name="file" />
              </span>
              <h2>
                {page > 0
                  ? 'No applications on this page'
                  : status
                    ? 'No matching applications'
                    : 'No applications yet'}
              </h2>
              <p>
                {status
                  ? 'Choose another status to see your other applications.'
                  : 'Your submitted care applications will appear here.'}
              </p>
              {(status || page > 0) && (
                <button onClick={() => setParams({})}>View all applications</button>
              )}
            </section>
          ) : (
            <ul className={styles.cards}>
              {resource.data.items.map((item) => (
                <li key={item.id}>
                  <Link className={styles.card} to={'/family/intake/' + item.id + backSearch}>
                    <div className={styles.cardTop}>
                      <span className={styles.reference}>APPLICATION #{item.id}</span>
                      <StatusBadge status={item.status} />
                    </div>
                    <h2>{item.targetElderName}</h2>
                    <p className={styles.address}>{item.targetAddress}</p>
                    <div className={styles.cardBottom}>
                      <div>
                        <span className={styles.smallLabel}>Submitted</span>
                        <time dateTime={item.createdAt}>{intakeDate(item.createdAt)}</time>
                      </div>
                      <span className={styles.openCard} aria-hidden="true">
                        <IntakeIcon name="arrow" />
                      </span>
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          {resource.data.items.length > 0 && resource.data.totalElements > resource.data.size && (
            <nav className={styles.pagination} aria-label="Application pages">
              <button onClick={() => changeQuery(page - 1)} disabled={page === 0}>
                Previous
              </button>
              <span>
                Page {page + 1} of {Math.ceil(resource.data.totalElements / resource.data.size)}
              </span>
              <button
                onClick={() => changeQuery(page + 1)}
                disabled={(page + 1) * resource.data.size >= resource.data.totalElements}
              >
                Next
              </button>
            </nav>
          )}
          <p className={styles.footnote}>Application times are shown in Singapore time.</p>
        </>
      )}
    </>
  )
  const createLink = (
    <Link className={styles.createLink} to="/family/intake/new">
      New application
      <IntakeIcon name="arrow" />
    </Link>
  )

  return (
    <div className={styles.page}>
      <section className={styles.hero}>
        <div>
          <p className={styles.eyebrow}>EARLIER REGISTRATION APPLICATIONS</p>
          <h1>My applications</h1>
          <p className={styles.subtitle}>
            Earlier applications to register an elder.
            <br />
            For a linked elder, submit a new service application.
          </p>
        </div>
        {!desktop && (
          <span className={styles.heroIcon}>
            <IntakeIcon name="file" />
          </span>
        )}
      </section>
      {!desktop && createLink}
      {desktop ? (
        <div className={styles.split}>
          <div className={styles.splitMain}>{list}</div>
          {/* Desktop: starting an application lives beside the list rather than above it. */}
          <aside className={styles.createCard} aria-labelledby="create-application">
            <p className={styles.eyebrow}>New application</p>
            <h2 id="create-application">Apply for care for a linked elder</h2>
            <p>Choose an elder you are linked to and request care services. These new requests are separate from the earlier registration applications shown here.</p>
            {createLink}
          </aside>
        </div>
      ) : list}
    </div>
  )
}
