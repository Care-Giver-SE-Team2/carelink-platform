import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import styles from './Caregiver.module.css'

export default function CaregiverNavigation({ workAllowed = true }: { workAllowed?: boolean }) {
  const { pathname, search } = useLocation()
  const [scheduleSearch, setScheduleSearch] = useState('')
  if (pathname === '/caregiver' || pathname.startsWith('/caregiver/visits/')) {
    const source = new URLSearchParams(search)
    const dates = new URLSearchParams()
    for (const key of ['dateFrom', 'dateTo']) if (source.has(key)) dates.set(key, source.get(key)!)
    const nextSearch = dates.size ? '?' + dates.toString() : ''
    if (nextSearch !== scheduleSearch) setScheduleSearch(nextSearch)
  }
  return <nav className={styles.navigation} aria-label="Caregiver navigation">
    {workAllowed && <NavLink to={'/caregiver' + scheduleSearch} end>My schedule</NavLink>}
    {workAllowed && <NavLink to="/caregiver/absences">My leave</NavLink>}
    <NavLink to="/caregiver/application">My application</NavLink>
    <NavLink to="/caregiver/credentials">My credentials</NavLink>
    <NavLink to="/caregiver/availability">My availability</NavLink>
    <NavLink to="/caregiver/work-preferences">Work preferences</NavLink>
    {workAllowed && <NavLink to="/caregiver/spot-checks">Spot checks</NavLink>}
    {workAllowed && <NavLink to="/caregiver/incidents">My reports</NavLink>}
    {workAllowed && <NavLink to="/caregiver/system-alerts">System alerts</NavLink>}
    {workAllowed && <NavLink to="/caregiver/history">My history</NavLink>}
  </nav>
}
