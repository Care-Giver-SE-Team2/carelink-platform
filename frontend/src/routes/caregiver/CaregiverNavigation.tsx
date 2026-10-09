import { useState } from 'react'
import { NavLink, useLocation } from 'react-router-dom'
import styles from './Caregiver.module.css'

export default function CaregiverNavigation() {
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
    <NavLink to={'/caregiver' + scheduleSearch} end>My schedule</NavLink>
    <NavLink to="/caregiver/absences">My leave</NavLink>
    <NavLink to="/caregiver/spot-checks">Spot checks</NavLink>
    <NavLink to="/caregiver/incidents">My reports</NavLink>
  </nav>
}
