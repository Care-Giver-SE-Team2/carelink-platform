import { KpiStrip, SectionHeader } from '../../../shared/components/ui'
import type { KpiItem } from '../../../shared/components/ui'
import { ManagerShell } from '../components/ManagerShell'
import { VisitRosterTable } from '../components/VisitRosterTable'
import type { Kpis } from '../data/today'
import { useTodayKpis, useTodayRoster } from '../lib/useTodayBoard'

const EXCEPTIONS_TAB = '/manager/exceptions'

function kpiItems(kpis: Kpis | undefined): KpiItem[] {
  const value = (n: number | undefined) => n ?? '—'
  return [
    { label: 'Visits scheduled', value: value(kpis?.scheduled) },
    { label: 'Completed', value: value(kpis?.completed) },
    { label: 'Unassigned', value: value(kpis?.unassigned), tone: 'info' },
    { label: 'Open exceptions', value: value(kpis?.openExceptions), tone: 'danger', href: EXCEPTIONS_TAB },
  ]
}

/**
 * Today board — MG03/MG04: headline figures above today's visit roster. Exceptions are
 * worked in the Exceptions tab; the exception figures here link there.
 */
export default function Today() {
  const roster = useTodayRoster()
  const kpis = useTodayKpis()
  const rosterData = roster.data

  return (
    <ManagerShell>
      <KpiStrip items={kpiItems(kpis.data)} label="Today at a glance" />

      <section aria-labelledby="visit-roster-title">
        <SectionHeader id="visit-roster-title" title="Visit roster" />
        <VisitRosterTable
          visits={rosterData?.visits ?? []}
          empty={roster.isError ? 'Could not load today’s roster.' : roster.isPending ? 'Loading today’s roster…' : 'No visits today.'}
          footer={
            rosterData &&
            rosterData.visits.length > 0 &&
            `${rosterData.visits.length} visits today`
          }
        />
      </section>
    </ManagerShell>
  )
}
