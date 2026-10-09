import { Link, useSearchParams } from 'react-router-dom'
import { useServiceApplications } from '../../../features/service-applications/queries'
import { intakeDate } from '../../../features/intake/presentation'
import { IntakeLoading, StatusBadge } from '../intake/IntakeLayout'
import styles from '../intake/FamilyIntake.module.css'

export function ServiceApplicationListPage() {
  const [params, setParams] = useSearchParams()
  const candidate = Number(params.get('page') ?? 0)
  const page = Number.isInteger(candidate) && candidate >= 0 && candidate <= 2147483647 ? candidate : 0
  const query = useServiceApplications(page)
  const data = query.data
  return <div className={styles.page}>
    <header className={styles.detailHeading}>
      <p className={styles.eyebrow}>CARE SERVICES</p><h1>My service applications</h1>
      <p className={styles.subtitle}>Request care for an elder you are linked to.</p>
    </header>
    <div className={styles.toolbar}>
      <Link className={styles.createLink} to="/family/service-applications/new">New service application</Link>
      <button onClick={() => void query.refetch()} disabled={query.isFetching}>Refresh</button>
      <Link to="/family/intake">Earlier registration applications</Link>
    </div>
    {query.isPending && <IntakeLoading />}
    {query.isError && <p role="alert" className={styles.state}>Unable to load service applications. Check your access or try Refresh.</p>}
    {!query.isError && data && <>
      <p aria-live="polite">{data.totalElements} service {data.totalElements === 1 ? 'application' : 'applications'}</p>
      {data.items.length === 0 ? <section className={styles.state}>
        <h2>{page > 0 ? 'No applications on this page' : 'No service applications yet'}</h2>
        <p>Applications you submit for your currently linked elders will appear here.</p>
        {page > 0 && <button onClick={() => setParams({})}>Back to first page</button>}
      </section> : <ul className={styles.cards}>{data.items.map((item) => <li key={item.id}>
        <Link className={styles.card} to={`/family/service-applications/${item.id}?page=${page}`}>
          <div className={styles.cardTop}><span className={styles.reference}>SERVICE APPLICATION #{item.id}</span><StatusBadge status={item.status} /></div>
          <h2>{item.elderSnapshot.fullName}</h2><p className={styles.address}>{item.elderSnapshot.address}</p>
          <div className={styles.cardBottom}><span>Submitted <time dateTime={item.createdAt}>{intakeDate(item.createdAt)}</time></span></div>
        </Link>
      </li>)}</ul>}
      {data.totalElements > data.size && <nav className={styles.pagination} aria-label="Service application pages">
        <button disabled={page === 0} onClick={() => setParams({ page: String(page - 1) })}>Previous</button>
        <span>Page {page + 1} of {Math.ceil(data.totalElements / data.size)}</span>
        <button disabled={(page + 1) * data.size >= data.totalElements} onClick={() => setParams({ page: String(page + 1) })}>Next</button>
      </nav>}
      <p className={styles.footnote}>Application times are shown in Singapore time.</p>
    </>}
  </div>
}
