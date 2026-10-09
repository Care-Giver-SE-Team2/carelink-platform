import { Link, Route, Routes } from 'react-router-dom'
import { RoleShell } from '../../shared/components/RoleShell'
import { getMyProfile } from '../../features/caregiver/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { QueryError } from './components'
import SchedulePage from './SchedulePage'
import WorkPackPage from './WorkPackPage'
import AbsencesPage from './AbsencesPage'
import CaregiverNavigation from './CaregiverNavigation'
import SpotChecksPage from './SpotChecksPage'
import IncidentsPage, { IncidentDetailPage } from './IncidentsPage'
import ReportIncidentPage from './ReportIncidentPage'
import styles from './Caregiver.module.css'

export default function CaregiverHome() {
  const { result, reload } = useCaregiverQuery('caregiver-profile', getMyProfile)
  return (
    <div className={styles.shell}><RoleShell title="Caregiver" theme="standard">
      {result.status === 'loading' && <p role="status">Checking caregiver access…</p>}
      {result.status === 'error' && <QueryError error={result.error} retry={reload} profile />}
      {result.status === 'success' && <div key={result.data.userId ?? result.data.id}><CaregiverNavigation /><Routes>
        <Route index element={<SchedulePage />} />
        <Route path="absences" element={<AbsencesPage />} />
        <Route path="spot-checks" element={<SpotChecksPage />} />
        <Route path="incidents" element={<IncidentsPage />} />
        <Route path="incidents/:incidentId" element={<IncidentDetailPage />} />
        <Route path="visits/:visitId/report-incident" element={<ReportIncidentPage />} />
        <Route path="visits/:visitId" element={<WorkPackPage />} />
        <Route path="*" element={<p>Page not found. <Link to="/caregiver">My schedule</Link></p>} />
      </Routes></div>}
    </RoleShell></div>
  )
}
