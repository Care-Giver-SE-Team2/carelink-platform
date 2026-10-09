import { useSyncExternalStore } from 'react'

/** Width at which the family app switches from the phone tab bar to the desktop side rail. */
const desktop = '(min-width: 900px)'

function subscribe(onChange: () => void) {
  if (typeof window.matchMedia !== 'function') return () => {}
  const query = window.matchMedia(desktop)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

/**
 * Whether the family app is showing its desktop layout (≥ 900px). Used only where the desktop
 * markup differs from the phone's; spacing differences stay in CSS media queries.
 */
export function useIsDesktop() {
  return useSyncExternalStore(subscribe, () => typeof window.matchMedia === 'function' && window.matchMedia(desktop).matches)
}
