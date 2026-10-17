import { Link } from 'react-router-dom'
import { WorkbenchShell } from './WorkbenchShell'
import { Banner } from './ui'

/** A live portal must not present browser-generated jobs as saved business transactions. */
export function JobsUnavailable() {
  return (
    <WorkbenchShell>
      <h1>Sandbox</h1>
      <Banner tone="warning" title="Job integration is not available yet" role="status">
        Job listing, acceptance, decline and completion are not connected to the live job API.
        No job actions are submitted from this page. Your application, API credentials and webhook settings remain available.
      </Banner>
      <Link to="/provider/docs">View integration documentation</Link>
    </WorkbenchShell>
  )
}
