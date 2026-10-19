/**
 * Small outline icons for the sign-in page's feature list and proof strip.
 * Hand-written rather than pulled from an icon library — this page has no
 * other icon usage yet, so a dependency for five glyphs isn't worth it.
 */
const common = {
  width: 20,
  height: 20,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.8,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
}

export function IconCalendar() {
  return (
    <svg {...common} aria-hidden="true">
      <rect x="3" y="4.5" width="18" height="16" rx="2.5" />
      <line x1="3" y1="9.5" x2="21" y2="9.5" />
      <line x1="8" y1="2.5" x2="8" y2="6.5" />
      <line x1="16" y1="2.5" x2="16" y2="6.5" />
    </svg>
  )
}

export function IconUsers() {
  return (
    <svg {...common} aria-hidden="true">
      <circle cx="9.5" cy="8" r="3.5" />
      <path d="M3 20v-1.5A4.5 4.5 0 0 1 7.5 14h4A4.5 4.5 0 0 1 16 18.5V20" />
      <path d="M16.5 4.6a3.5 3.5 0 0 1 0 6.8" />
      <path d="M19.5 20v-1.5a4.5 4.5 0 0 0-2.8-4.16" />
    </svg>
  )
}

export function IconHeart() {
  return (
    <svg {...common} aria-hidden="true">
      <path d="M12 20.5s-7.2-4.6-9.8-8.9C.5 8.6 1.4 5 4.7 3.9c2.3-.8 4.6.1 7.3 3 2.7-2.9 5-3.8 7.3-3 3.3 1.1 4.2 4.7 2.5 7.7-2.6 4.3-9.8 8.9-9.8 8.9z" />
    </svg>
  )
}
