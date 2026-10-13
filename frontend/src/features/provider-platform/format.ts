/**
 * How the provider platform shows its values: the postal district a job is in, with its usual
 * name, and times in Singapore time whatever the browser's zone.
 */

const DISTRICT_NAMES: Record<string, string> = {
  '01': 'Raffles Place',
  '02': 'Tanjong Pagar',
  '03': 'Queenstown',
  '04': 'Telok Blangah',
  '05': 'Clementi',
  '06': 'City Hall',
  '07': 'Bugis',
  '08': 'Little India',
  '09': 'Orchard',
  '10': 'Bukit Timah',
  '11': 'Novena',
  '12': 'Toa Payoh',
  '13': 'MacPherson',
  '14': 'Geylang',
  '15': 'Marine Parade',
  '16': 'Bedok',
  '17': 'Changi',
  '18': 'Tampines',
  '19': 'Hougang',
  '20': 'Ang Mo Kio',
  '21': 'Upper Bukit Timah',
  '22': 'Jurong',
  '23': 'Bukit Batok',
  '24': 'Lim Chu Kang',
  '25': 'Woodlands',
  '26': 'Mandai',
  '27': 'Sembawang',
  '28': 'Seletar',
}

/** "16" → "Bedok"; the district itself for anything unknown. */
export function districtName(district: string): string {
  return DISTRICT_NAMES[district] ?? district
}

/** "16" → "D16 · Bedok": the short form in lists. */
export function areaShort(district: string): string {
  return `D${district} · ${districtName(district)}`
}

/** "16" → "District 16 · Bedok". */
export function areaLong(district: string): string {
  return `District ${district} · ${districtName(district)}`
}

function parts(at: string | number, options: Intl.DateTimeFormatOptions): Record<string, string> {
  return Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', { ...options, hourCycle: 'h23', timeZone: 'Asia/Singapore' })
      .formatToParts(new Date(at))
      .map((p) => [p.type, p.value]),
  )
}

/** "Fri 16 Oct, 10:00". */
export function formatDayTime(at: string): string {
  const p = parts(at, { weekday: 'short', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
  return `${p.weekday} ${p.day} ${p.month}, ${p.hour}:${p.minute}`
}

/** "12 Oct 09:41". */
export function formatShort(at: string | number): string {
  const p = parts(at, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
  return `${p.day} ${p.month} ${p.hour}:${p.minute}`
}

/** "12 Oct 09:41:03". */
export function formatShortSeconds(at: string | number): string {
  const p = parts(at, { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', second: '2-digit' })
  return `${p.day} ${p.month} ${p.hour}:${p.minute}:${p.second}`
}

/** "12 Oct 2026, 09:20". */
export function formatLong(at: string): string {
  const p = parts(at, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  return `${p.day} ${p.month} ${p.year}, ${p.hour}:${p.minute}`
}

/** "09:41". */
export function formatTime(at: string | number): string {
  const p = parts(at, { hour: '2-digit', minute: '2-digit' })
  return `${p.hour}:${p.minute}`
}

/** "09:41:12". */
export function formatClock(at: number): string {
  const p = parts(at, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  return `${p.hour}:${p.minute}:${p.second}`
}

/** "Fri 16 Oct 2026, 10:00–10:45": the start's date and both times. */
export function formatSchedule(start: string, durationMin: number): string {
  const p = parts(start, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
  const end = formatTime(Date.parse(start) + durationMin * 60_000)
  return `${p.weekday} ${p.day} ${p.month} ${p.year}, ${p.hour}:${p.minute}–${end}`
}

/** Minutes left until `expiresAt`, never below zero. */
export function minutesLeft(expiresAt: number, now: number): number {
  return Math.max(0, Math.ceil((expiresAt - now) / 60_000))
}

/** "2026-10-16T02:00:00Z" → "2026-10-16T10:00", the Singapore wall time a datetime input shows. */
export function toSgtInput(iso: string): string {
  return new Date(Date.parse(iso) + 8 * 60 * 60 * 1000).toISOString().slice(0, 16)
}

/** The inverse of toSgtInput; null for an empty or unreadable value. */
export function fromSgtInput(value: string): string | null {
  if (!value) return null
  const at = Date.parse(`${value}:00+08:00`)
  return Number.isNaN(at) ? null : new Date(at).toISOString()
}
