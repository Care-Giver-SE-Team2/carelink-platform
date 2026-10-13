import { useLocation } from 'react-router-dom'

/** For tests: shows where the router is, as data-testid="location". */
export function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname + location.search}</div>
}
