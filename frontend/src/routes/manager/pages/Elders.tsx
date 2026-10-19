import { useEffect, useMemo, useState } from 'react'
import type { KeyboardEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ManagerShell } from '../components/ManagerShell'
import type { ElderRow, PlanStatus } from '../data/elders'
import {
  countTree,
  formatHoursMinutes,
  fromCarePlanNodeResponses,
  visitsPerWeekOfTree,
  weeklyHoursOfTree,
} from '../lib/planTree'
import { useElders } from '../lib/useElders'
import { ageLabel } from '../lib/age'
import { formatDate, formatNextVisit } from '../lib/nextVisit'
import { fetchCarePlanNodes, fetchLatestCarePlan } from '../../../shared/api/careplan'
import { removePrimaryCaregiver } from '../../../shared/api/profile'
import {
  Avatar,
  Badge,
  BodyText,
  Button,
  Callout,
  Card,
  Eyebrow,
  FilterChips,
  IdentityHeader,
  KeyValueList,
  ListRow,
  MetaText,
  PageHeader,
  PersonCard,
  SearchField,
  Select,
  SidePanel,
  SplitLayout,
} from '../../../shared/components/ui'
import { CaregiverPickerModal } from '../components/CaregiverPickerModal'
import { EmptyAssignSlot } from '../components/EmptyAssignSlot'
import { RemoveCaregiverDialog } from '../components/RemoveCaregiverDialog'
import { StopCarePlanButton } from '../components/StopCarePlanButton'
import { StopCarePlanDialog } from '../components/StopCarePlanDialog'
import styles from './Elders.module.css'

type SortOrder = 'name' | 'name-desc' | 'next-visit' | 'plan-status'

/**
 * Where an elder sits in the manager's onboarding work: a care plan first, then a primary
 * caregiver. Only a published plan moves an elder on; a draft revising one leaves them where they
 * were, since the published version stays in force until the revision is published.
 */
type Worklist = 'needs-plan' | 'needs-caregiver' | 'assigned' | 'stopped'
type WorklistFilter = Worklist | 'all'

const WORKLIST_TABS: { value: WorklistFilter; label: string; empty: string }[] = [
  { value: 'needs-plan', label: 'NEEDS PLAN', empty: 'Every elder has a care plan.' },
  { value: 'needs-caregiver', label: 'NEEDS CAREGIVER', empty: 'Every elder with a published plan has a primary caregiver.' },
  { value: 'assigned', label: 'ASSIGNED', empty: 'No elder has both a published plan and a primary caregiver yet.' },
  { value: 'stopped', label: 'STOPPED', empty: 'No care plans have been stopped.' },
  { value: 'all', label: 'ALL', empty: 'No elders yet.' },
]

function worklistOf(elder: ElderRow): Worklist {
  if (elder.planStatus === 'stopped') return 'stopped'
  if (!elder.hasPublishedPlan) return 'needs-plan'
  return elder.primaryCaregiver ? 'assigned' : 'needs-caregiver'
}

/**
 * An elder is the manager's to plan for once the family has applied for care. One who already has
 * a plan stays listed even without an application, so the plan is never out of reach.
 */
function isOnWorklist(elder: ElderRow): boolean {
  return elder.hasCareRequest || elder.planStatus !== 'none'
}

function isWorklistFilter(value: string | null): value is WorklistFilter {
  return WORKLIST_TABS.some((tab) => tab.value === value)
}

/**
 * The tab in the URL; with none chosen, the selected elder's tab (coming back from their care
 * plan), else the first piece of outstanding work.
 */
function defaultWorklist(
  listParam: string | null,
  selected: ElderRow | undefined,
  counts: Record<WorklistFilter, number>,
): WorklistFilter {
  if (isWorklistFilter(listParam)) return listParam
  if (selected) return worklistOf(selected)
  if (counts['needs-plan'] > 0) return 'needs-plan'
  if (counts['needs-caregiver'] > 0) return 'needs-caregiver'
  return 'all'
}

const SORT_OPTIONS: { value: SortOrder; label: string }[] = [
  { value: 'name', label: 'A–Z' },
  { value: 'name-desc', label: 'Z–A' },
  { value: 'next-visit', label: 'next visit' },
  { value: 'plan-status', label: 'plan status' },
]

const PLAN_STATUS_RANK: Record<PlanStatus, number> = { none: 0, draft: 1, published: 2, stopped: 3 }

const SEARCH_DEBOUNCE_MS = 200

/** nextVisitAt is an ISO "yyyy-MM-dd" string, so lexical order is chronological order. */
function nextVisitRank(value: string | null): string {
  return value ?? '9999-99-99'
}

function rowDomId(elderId: string): string {
  return `elder-row-${elderId}`
}

/**
 * Elders index — UC-MG01's entry point. The manager works through elders who need a plan,
 * then those who need a caregiver, previewing each in the rail and opening their plan (or
 * creating one). ↑/↓ move the selection, Enter opens the plan. The tab and the selected elder
 * live in the URL, so coming back from a care plan lands on the same elder.
 */
export default function Elders() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [sort, setSort] = useState<SortOrder>('name')
  const [searchParams, setSearchParams] = useSearchParams()
  const selectedId = searchParams.get('selected')
  const [assignTarget, setAssignTarget] = useState<ElderRow | null>(null)
  const [removeTarget, setRemoveTarget] = useState<ElderRow | null>(null)
  const [stopTarget, setStopTarget] = useState<ElderRow | null>(null)
  const [removeError, setRemoveError] = useState<string | null>(null)

  useEffect(() => {
    const id = setTimeout(() => setDebouncedQuery(query), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(id)
  }, [query])

  const { data: allElders = [], isLoading, isError, error } = useElders()
  const elders = useMemo(() => allElders.filter(isOnWorklist), [allElders])

  const q = debouncedQuery.trim()

  const worklistCounts = useMemo(() => {
    const counts: Record<WorklistFilter, number> = {
      'needs-plan': 0,
      'needs-caregiver': 0,
      assigned: 0,
      stopped: 0,
      all: elders.length,
    }
    for (const e of elders) counts[worklistOf(e)]++
    return counts
  }, [elders])

  const listParam = searchParams.get('list')
  const selectedRow = selectedId ? elders.find((e) => e.id === selectedId) : undefined
  const worklist = useMemo(
    () => defaultWorklist(listParam, selectedRow, worklistCounts),
    [listParam, selectedRow, worklistCounts],
  )

  const filtered = useMemo(() => {
    const needle = q.toLowerCase()
    const rows = elders.filter((e) => {
      if (worklist !== 'all' && worklistOf(e) !== worklist) return false
      if (needle && !e.name.toLowerCase().includes(needle) && !e.id.toLowerCase().includes(needle)) return false
      return true
    })
    const sorted = [...rows]
    if (sort === 'name') sorted.sort((a, b) => a.name.localeCompare(b.name))
    else if (sort === 'name-desc') sorted.sort((a, b) => b.name.localeCompare(a.name))
    else if (sort === 'next-visit')
      sorted.sort((a, b) => nextVisitRank(a.nextVisitAt).localeCompare(nextVisitRank(b.nextVisitAt)))
    else if (sort === 'plan-status')
      sorted.sort((a, b) => PLAN_STATUS_RANK[a.planStatus] - PLAN_STATUS_RANK[b.planStatus])
    return sorted
  }, [elders, q, worklist, sort])

  /** Updates the URL's tab/selection without adding a history entry per click. */
  function setParam(name: 'list' | 'selected', value: string | null) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value === null) next.delete(name)
        else next.set(name, value)
        return next
      },
      { replace: true },
    )
  }

  function setSelectedId(id: string) {
    setParam('selected', id)
  }

  const selected: ElderRow | undefined = selectedRow

  const { data: selectedLatestPlan } = useQuery({
    queryKey: ['carePlan', 'latest', selected?.id],
    queryFn: () => fetchLatestCarePlan(selected!.id),
    enabled: selected != null,
  })

  const { data: selectedPlanNodes } = useQuery({
    queryKey: ['carePlan', 'nodes', selectedLatestPlan?.id],
    queryFn: () => fetchCarePlanNodes(selectedLatestPlan!.id),
    enabled: selectedLatestPlan != null,
  })

  const selectedTree = useMemo(
    () => (selectedPlanNodes ? fromCarePlanNodeResponses(selectedPlanNodes) : []),
    [selectedPlanNodes],
  )

  function clearSearch() {
    setQuery('')
    setDebouncedQuery('')
  }

  function openCarePlan(elder: ElderRow) {
    navigate(`/manager/elders/${elder.id}`)
  }

  function handleListKeyDown(e: KeyboardEvent<HTMLDivElement>) {
    if (filtered.length === 0) return
    const index = filtered.findIndex((row) => row.id === selectedId)
    let next: ElderRow | undefined
    if (e.key === 'ArrowDown') next = filtered[Math.min(index + 1, filtered.length - 1)]
    else if (e.key === 'ArrowUp') next = filtered[Math.max(index - 1, 0)]
    else if (e.key === 'Enter' && selected) {
      e.preventDefault()
      openCarePlan(selected)
      return
    }
    if (!next) return
    e.preventDefault()
    setSelectedId(next.id)
    document.getElementById(rowDomId(next.id))?.scrollIntoView?.({ block: 'nearest' })
  }

  const header = (
    <PageHeader
      title="Elders"
      meta={`${elders.length} elders`}
      toolbar={
        <div className={styles.toolbar}>
          <FilterChips
            label="Elder worklist"
            options={WORKLIST_TABS.map(({ value, label }) => ({ value, label, count: worklistCounts[value] }))}
            value={worklist}
            onChange={(next) => setParam('list', next)}
          />
          <div className={styles.filters}>
            <SearchField
              className={styles.search}
              placeholder="Search elders by name or ID"
              value={query}
              onChange={setQuery}
            />
            <Select
              variant="filter"
              aria-label="Sort order"
              value={sort}
              onChange={(v) => setSort(v as SortOrder)}
              options={SORT_OPTIONS}
            />
          </div>
        </div>
      }
    />
  )

  let body
  if (isLoading) {
    body = <MetaText className={styles.empty}>Loading elders…</MetaText>
  } else if (isError) {
    body = (
      <MetaText className={styles.empty}>
        Could not load elders{error instanceof Error ? `: ${error.message}` : ''}.
      </MetaText>
    )
  } else if (filtered.length === 0) {
    body = (
      <div className={styles.empty}>
        <MetaText>
          {q ? `No elders match "${q}".` : WORKLIST_TABS.find((tab) => tab.value === worklist)!.empty}
        </MetaText>
        {q && (
          <Button variant="ghost" onClick={clearSearch}>
            Clear search
          </Button>
        )}
      </div>
    )
  } else {
    body = filtered.map((e) => (
      <ListRow
        key={e.id}
        id={rowDomId(e.id)}
        selected={e.id === selectedId}
        onClick={() => setSelectedId(e.id)}
        leading={<Avatar size={34} />}
        title={e.name}
        meta={`${ageLabel(e.age)} · ${e.street || 'no address on file'}`}
        cells={[
          <span key="plan" className={styles.badgeCell}>
            <Badge status={e.planStatus} version={e.planVersion} />
          </span>,
          <span key="caregiver" className={styles.optional}>
            {e.primaryCaregiver ? (
              <BodyText>{e.primaryCaregiver}</BodyText>
            ) : (
              <MetaText tone="faint" as="span">
                unassigned
              </MetaText>
            )}
          </span>,
          <MetaText key="next" as="span" tone={e.nextVisitAt ? 'default' : 'faint'} className={styles.nextVisit}>
            {formatNextVisit(e.nextVisitAt) ?? '—'}
          </MetaText>,
        ]}
      />
    ))
  }

  const main = (
    <>
      {header}
      <div className={styles.table}>
        <div className={styles.rows}>
          <div className={styles.headerRow} aria-hidden="true">
            <Eyebrow>Elder</Eyebrow>
            <Eyebrow>Care plan</Eyebrow>
            <Eyebrow className={styles.optional}>Primary caregiver</Eyebrow>
            <Eyebrow className={styles.nextVisit}>Next visit</Eyebrow>
          </div>
          <div
            role="listbox"
            aria-label="Elders"
            tabIndex={0}
            className={styles.listbox}
            aria-activedescendant={selected ? rowDomId(selected.id) : undefined}
            onKeyDown={handleListKeyDown}
          >
            {body}
          </div>
        </div>
      </div>
      <MetaText tone="faint" className={styles.footer}>
        {filtered.length} of {worklistCounts[worklist]} shown{q ? ` · matching "${q}"` : ''}
      </MetaText>
    </>
  )

  const rail = !selected ? (
    <SidePanel
      label="Selected elder"
      sections={[
        <div key="empty" className={styles.railGroup}>
          <Eyebrow>Selected</Eyebrow>
          <MetaText tone="faint">Select an elder to preview their care plan.</MetaText>
        </div>,
      ]}
    />
  ) : (
    <SidePanel
      label="Selected elder"
      sections={[
        <div key="identity" className={styles.railGroup}>
          <Eyebrow>Selected</Eyebrow>
          <IdentityHeader
            name={selected.name}
            meta={`${ageLabel(selected.age)} · ${selected.street || 'no address on file'}`}
          />
        </div>,
        selected.planStatus === 'none' || !selectedLatestPlan ? (
          <Card key="plan">
            <MetaText>No plan published yet.</MetaText>
          </Card>
        ) : (
          <Card
            key="plan"
            title={`Care plan v${selectedLatestPlan.version}`}
            trailing={<Badge status={selected.planStatus} />}
          >
            <KeyValueList
              items={[
                { label: 'visits per week', value: visitsPerWeekOfTree(selectedTree) },
                { label: 'effort per week', value: formatHoursMinutes(weeklyHoursOfTree(selectedTree)) },
                {
                  label: 'tasks',
                  value: `${countTree(selectedTree).tasks} in ${countTree(selectedTree).subPlans} sub-plans`,
                },
                { label: 'last edited', value: formatDate(selectedLatestPlan.updatedAt) },
              ]}
            />
          </Card>
        ),
        selected.primaryCaregiver ? (
          <PersonCard
            key="caregiver"
            name={selected.primaryCaregiver}
            role="Primary caregiver"
            meta={selected.primaryCaregiverSince ? `since ${formatDate(selected.primaryCaregiverSince)}` : undefined}
            actions={
              <>
                <Button onClick={() => setAssignTarget(selected)}>Change</Button>
                <Button variant="dangerOutline" onClick={() => setRemoveTarget(selected)}>
                  Remove
                </Button>
              </>
            }
          />
        ) : (
          selected.planStatus !== 'none' &&
          selected.planStatus !== 'stopped' && (
            <EmptyAssignSlot
              key="caregiver"
              message="No primary caregiver"
              actionLabel="Assign caregiver"
              onAction={() => setAssignTarget(selected)}
            />
          )
        ),
        removeError && (
          <Callout key="removeError" tone="danger" role="alert">
            {removeError}
          </Callout>
        ),
        <Card key="family" title="Family contacts">
          <MetaText>No family contacts on file yet.</MetaText>
        </Card>,
        <div key="actions" className={styles.railActions}>
          <Button block variant="primary" onClick={() => openCarePlan(selected)}>
            {selected.planStatus === 'none' ? 'Create care plan' : 'Open care plan'}
          </Button>
          <Button block variant="secondary">
            View visit history
          </Button>
        </div>,
      ]}
      footer={
        <>
          {selected.planStatus === 'published' && (
            <StopCarePlanButton onClick={() => setStopTarget(selected)} />
          )}
          <MetaText tone="faint">Opening a record is written to the audit log with your name and the time.</MetaText>
        </>
      }
    />
  )

  return (
    <ManagerShell>
      <SplitLayout main={main} rail={rail} />

      {assignTarget && (
        <CaregiverPickerModal
          elder={assignTarget}
          onClose={() => setAssignTarget(null)}
          onAssign={() => {
            queryClient.invalidateQueries({ queryKey: ['elders'] })
            setAssignTarget(null)
          }}
          onRemove={() => {
            queryClient.invalidateQueries({ queryKey: ['elders'] })
            setAssignTarget(null)
          }}
        />
      )}

      {removeTarget && removeTarget.primaryCaregiver && (
        <RemoveCaregiverDialog
          caregiverName={removeTarget.primaryCaregiver}
          elderName={removeTarget.name}
          onCancel={() => setRemoveTarget(null)}
          onConfirm={async () => {
            const target = removeTarget
            setRemoveTarget(null)
            setRemoveError(null)
            try {
              await removePrimaryCaregiver(target.id)
              queryClient.invalidateQueries({ queryKey: ['elders'] })
            } catch (e) {
              setRemoveError(
                `Could not remove ${target.primaryCaregiver} from ${target.name}${e instanceof Error ? `: ${e.message}` : ''}.`,
              )
            }
          }}
        />
      )}

      {stopTarget && (
        <StopCarePlanDialog
          elder={stopTarget}
          onClose={() => setStopTarget(null)}
          onStopped={() => {
            queryClient.invalidateQueries({ queryKey: ['elders'] })
            setStopTarget(null)
          }}
        />
      )}
    </ManagerShell>
  )
}
