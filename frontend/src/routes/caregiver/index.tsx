import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { getMyProfile } from '../../features/caregiver/api'
import { useCaregiverQuery } from '../../features/caregiver/useCaregiverQuery'
import { QueryError } from './components'
import SchedulePage from './SchedulePage'
import WorkPackPage from './WorkPackPage'
import AbsencesPage from './AbsencesPage'
import CaregiverLayout from './CaregiverLayout'
import SpotChecksPage from './SpotChecksPage'
import IncidentsPage, { IncidentDetailPage } from './IncidentsPage'
import ReportIncidentPage from './ReportIncidentPage'
import CredentialsPage from './CredentialsPage'
import ApplicationPage from './ApplicationPage'
import WorkSettingsPage from './WorkSettingsPage'
import SystemAlertsPage, { SystemAlertDetailPage } from './SystemAlertsPage'
import EvidencePage from './EvidencePage'
import HistoryPage, { HistoryDetailPage } from './HistoryPage'
import styles from './Caregiver.module.css'

export default function CaregiverHome() {
  const { result, reload, retainedData } = useCaregiverQuery('caregiver-profile', getMyProfile, true)
  const profile = result.status === 'success' ? result.data : result.status === 'loading' ? retainedData : undefined
  const workAllowed = profile !== undefined && ['AVAILABLE', 'BUSY'].includes(profile.status)
  return (
    profile ? <CaregiverLayout key={profile.userId ?? profile.id} workAllowed={workAllowed} name={profile.fullName}><Routes>
        <Route index element={workAllowed ? <SchedulePage /> : <Navigate to="/caregiver/application" replace />} />
        <Route path="application" element={<ApplicationPage profile={profile} refreshProfile={reload} />} />
        <Route path="availability" element={<WorkSettingsPage key="availability" mode="availability" />} />
        <Route path="work-preferences" element={<WorkSettingsPage key="preferences" mode="preferences" />} />
        <Route path="absences" element={workAllowed ? <AbsencesPage /> : <AdmissionRequired />} />
        <Route path="credentials" element={<CredentialsPage />} />
        <Route path="credentials/:credentialId" element={<CredentialsPage />} />
        <Route path="spot-checks" element={workAllowed ? <SpotChecksPage /> : <AdmissionRequired />} />
        <Route path="incidents" element={workAllowed ? <IncidentsPage /> : <AdmissionRequired />} />
        <Route path="system-alerts" element={workAllowed ? <SystemAlertsPage /> : <AdmissionRequired />} />
        <Route path="system-alerts/:incidentId" element={workAllowed ? <SystemAlertDetailPage /> : <AdmissionRequired />} />
        <Route path="history" element={workAllowed ? <HistoryPage /> : <AdmissionRequired />} />
        <Route path="history/:visitId" element={workAllowed ? <HistoryDetailPage /> : <AdmissionRequired />} />
        <Route path="incidents/:incidentId" element={workAllowed ? <IncidentDetailPage /> : <AdmissionRequired />} />
        <Route path="visits/:visitId/report-incident" element={workAllowed ? <ReportIncidentPage /> : <AdmissionRequired />} />
        <Route path="visits/:visitId" element={workAllowed ? <WorkPackPage /> : <AdmissionRequired />} />
        <Route path="visits/:visitId/evidence" element={workAllowed ? <EvidencePage /> : <AdmissionRequired />} />
        <Route path="*" element={<p>Page not found. <Link to="/caregiver">My schedule</Link></p>} />
      </Routes></CaregiverLayout>
      : <div className={styles.shell}><main className={styles.bare}>
        {result.status === 'loading' && <p role="status">Checking caregiver access…</p>}
        {result.status === 'error' && <QueryError error={result.error} retry={reload} profile />}
      </main></div>
  )
}

function AdmissionRequired() { return <p role="alert">Work access requires active admission. <Link to="/caregiver/application">Check my application</Link></p> }
