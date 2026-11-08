import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import Apply from './pages/Apply'
import Credentials from './pages/Credentials'
import Docs from './pages/Docs'
import JobDetail from './pages/JobDetail'
import Jobs from './pages/Jobs'
import Status from './pages/Status'
import Webhooks from './pages/Webhooks'
import { WorkbenchProvider } from './WorkbenchProvider'
import { useWorkbench } from './workbenchContext'

/** Opens on the Job console while a token is held, API credentials once approved, Apply otherwise. */
function Start() {
  const { token, portal, restoring } = useWorkbench()
  if (token) return <Navigate to="/provider/console" replace />
  if (restoring) return null
  return <Navigate to={portal ? '/provider/credentials' : '/provider/apply'} replace />
}

/**
 * Pages for an approved provider go back to Status, which explains why, until it is.
 * `portal` pages need the approved application itself; the Job console only a token.
 */
function Approved({ portal: needsPortal = false, children }: { portal?: boolean; children: ReactNode }) {
  const { approved, portal, restoring } = useWorkbench()
  if (needsPortal ? portal : approved) return children
  return restoring ? null : <Navigate to="/provider/status" replace />
}

/** An older job link (/provider/jobs/… or /provider/sandbox/jobs/…): the same job in the Job console. */
function OldJob() {
  const { jobId } = useParams()
  return <Navigate to={`/provider/console/jobs/${jobId}`} replace />
}

/**
 * Provider developer portal (PT01 / PT04): where third-party providers join the CareLink Open
 * Platform, manage their API credentials and webhooks, read the docs, and use the job API by hand
 * in the Job console, on their real jobs. It is public — no CareLink login — and reaches CareLink only through the public
 * provider API (features/provider-platform/api.ts). See README.md in this folder.
 */
export default function ProviderWorkbench() {
  return (
    <WorkbenchProvider>
      <Routes>
        <Route index element={<Start />} />
        <Route path="apply" element={<Apply />} />
        <Route path="status" element={<Status />} />
        <Route path="docs" element={<Docs />} />
        <Route path="credentials" element={<Approved portal><Credentials /></Approved>} />
        <Route path="webhooks" element={<Approved portal><Webhooks /></Approved>} />
        <Route path="console" element={<Approved><Jobs /></Approved>} />
        <Route path="console/jobs/:jobId" element={<Approved><JobDetail /></Approved>} />
        <Route path="sandbox" element={<Navigate to="/provider/console" replace />} />
        <Route path="sandbox/jobs/:jobId" element={<OldJob />} />
        <Route path="jobs" element={<Navigate to="/provider/console" replace />} />
        <Route path="jobs/:jobId" element={<OldJob />} />
        <Route path="*" element={<Navigate to="/provider" replace />} />
      </Routes>
    </WorkbenchProvider>
  )
}
