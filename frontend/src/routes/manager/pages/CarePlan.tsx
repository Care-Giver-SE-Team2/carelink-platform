import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ManagerShell } from '../components/ManagerShell'
import { UserIdentity } from '../components/UserIdentity'
import type { PlanNode, SubPlanNode, TaskNode } from '../data/carePlans'
import { careNeedLabel, groupCareActivities, useCareActivities } from '../../../features/careplan/careActivities'
import {
  requestedNeedStatus,
  requestedNeeds,
  seedPlanFromRequests,
  unscheduledTasks,
  useElderCareRequests,
} from '../lib/familyRequests'
import type { RequestedNeedStatus } from '../lib/familyRequests'
import { useElder } from '../lib/useElder'
import { useElderFamily } from '../lib/useElderFamily'
import { useCarePlanVersions } from '../lib/useCarePlanVersions'
import { continuityLabel, dialectsLabel, familyLabel, livesLabel, mobilityLabel } from '../lib/elderProfile'
import { formatDate } from '../lib/nextVisit'
import { useCurrentUser } from '../lib/useCurrentUser'
import {
  countTree,
  dayScheduleFromVisits,
  findSubPlan,
  formatHoursFixed,
  formatHoursLoose,
  fromCarePlanNodeResponses,
  removeNode,
  toPlanTreeItems,
  updateTask,
  visitsFromDaySchedule,
  weeklyHours,
  weeklyHoursOfTree,
} from '../lib/planTree'
import { fetchCarePlanNodes, fetchLatestCarePlan, publishCarePlan } from '../../../shared/api/careplan'
import type { CarePlanNodeResponse, CarePlanResponse, PlanNodePayload } from '../../../shared/api/careplan'
import { declineServiceApplication } from '../../../shared/api/profile'
import type { ElderCareRequest } from '../../../shared/api/profile'
import { useDraftAutosave } from '../lib/useDraftAutosave'
import type { DraftSaveState } from '../lib/useDraftAutosave'
import {
  BackLink,
  Badge,
  BodyText,
  Button,
  Callout,
  ConfirmDialog,
  DateInput,
  Eyebrow,
  Field,
  IdentityHeader,
  KeyValueList,
  MetaText,
  PageHeader,
  PlanTreeView,
  SidePanel,
  SplitLayout,
  TextArea,
} from '../../../shared/components/ui'
import type { BadgeStatus, SelectGroup, TagTone } from '../../../shared/components/ui'
import { PlanTreeEditor } from '../components/PlanTreeEditor'
import { FamilyRequestsPanel } from '../components/FamilyRequestsPanel'
import { StopCarePlanButton } from '../components/StopCarePlanButton'
import { StopCarePlanDialog } from '../components/StopCarePlanDialog'
import { VersionHistoryList } from '../components/VersionHistoryList'
import type { VersionEntry } from '../components/VersionHistoryList'
import type { DayScheduleValue } from '../components/weekdays'
import styles from './CarePlan.module.css'

/** How the rail tags each care need the family asked for, against the plan being edited. */
const REQUESTED_NEED_TAG: Record<RequestedNeedStatus, { label: string; tone: TagTone }> = {
  planned: { label: 'In plan', tone: 'accent' },
  unscheduled: { label: 'Set days', tone: 'danger' },
  missing: { label: 'Not in plan', tone: 'muted' },
  uncatalogued: { label: 'Own words', tone: 'muted' },
}

/** Local-date "yyyy-MM-dd" — lexically comparable with the plan's ISO start date. */
function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

/** An issued version's badge: superseded ones are archived; a published one not yet started is scheduled. */
function versionStatus(plan: CarePlanResponse): BadgeStatus {
  if (plan.status === 'SUPERSEDED') return 'archived'
  if (plan.status === 'STOPPED') return 'stopped'
  return plan.startDate && plan.startDate > todayIso() ? 'scheduled' : 'published'
}

/** Frontend tree -> the wire shape POST /api/care-plans/{id}/publish expects: every node is
 * published as a task; a sub-plan's name becomes its children's groupName, a purely display-only
 * label with no hierarchy behind it. */
function toPlanNodePayloads(nodes: PlanNode[]): PlanNodePayload[] {
  return nodes.flatMap((node) =>
    node.type === 'task' ? [taskToPayload(node, null)] : node.children.map((child) => taskToPayload(child, node.name)),
  )
}

function taskToPayload(node: TaskNode, groupName: string | null): PlanNodePayload {
  return {
    groupName,
    activityCode: node.activityCode,
    name: node.name,
    visits: node.visits.map((v) => ({ day: v.day, startTime: v.startTime, minutes: v.minutes })),
    evidenceType: node.evidence,
  }
}

/**
 * Care plan editor — UC-MG01. Editable, flat sub-plan/task list with a live
 * weekly-effort rollup (lib/planTree.ts) and a publish flow that snapshots
 * the current tree as a new published version. Sub-plan deletion is
 * confirmed via a dialog; task deletion is immediate. See
 * design_handoff_care_plan_authoring/README.md for the full spec.
 */
export default function CarePlan() {
  const { elderId } = useParams()

  const { data: elder, isLoading: elderLoading, isError: elderError } = useElder(elderId)
  const { data: family } = useElderFamily(elderId)
  const { data: versions } = useCarePlanVersions(elderId)
  const { data: currentUser } = useCurrentUser()
  const { data: catalog } = useCareActivities()
  const { data: careRequests } = useElderCareRequests(elderId)
  const queryClient = useQueryClient()

  const [tree, setTree] = useState<PlanNode[]>([])
  const [status, setStatus] = useState<'draft' | 'published' | 'stopped'>('draft')
  /** The latest issued (published or stopped) version; a draft is always this + 1. */
  const [version, setVersion] = useState(0)
  /** Weekly effort of the version a draft replaces, so the draft can show what it changes. */
  const [priorPublishedHours, setPriorPublishedHours] = useState<number | undefined>()
  const [startDate, setStartDate] = useState('')
  const [collapsed, setCollapsed] = useState<Set<string>>(() => new Set())
  const [showPublishModal, setShowPublishModal] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<SubPlanNode | null>(null)
  const [publishing, setPublishing] = useState(false)
  const [publishError, setPublishError] = useState<string | null>(null)

  const [stopInfo, setStopInfo] = useState<{ effectiveDate: string; reason: string } | null>(null)
  const [showStopModal, setShowStopModal] = useState(false)

  // The inline panels keep their own in-progress input; the page only tracks which is open.
  const [addPanelOpen, setAddPanelOpen] = useState(false)
  const [editingTaskId, setEditingTaskId] = useState<string | null>(null)

  const totalHours = useMemo(() => weeklyHoursOfTree(tree), [tree])
  const { subPlans, tasks } = useMemo(() => countTree(tree), [tree])
  const treeItems = useMemo(() => toPlanTreeItems(tree), [tree])
  const unscheduled = useMemo(() => unscheduledTasks(tree), [tree])
  const needs = useMemo(() => requestedNeeds(careRequests ?? []), [careRequests])
  const activityOptions = useMemo<SelectGroup[]>(
    () =>
      groupCareActivities(catalog ?? []).map((group) => ({
        group: group.category,
        items: group.activities.map(({ label }) => ({ value: label, label })),
      })),
    [catalog],
  )
  /** Whether the first draft has been filled in from the family's applications (done once, only for an elder with no plan). */
  const [seeded, setSeeded] = useState(false)
  /** Bumped by every manager edit (never by loading a plan), so only edits are saved to the draft. */
  const [revision, setRevision] = useState(0)
  const scheduledRevision = useRef(0)
  const [showDiscardModal, setShowDiscardModal] = useState(false)
  const [discarding, setDiscarding] = useState(false)
  const [discardError, setDiscardError] = useState<string | null>(null)
  const [declineTarget, setDeclineTarget] = useState<ElderCareRequest | null>(null)
  const [declineReason, setDeclineReason] = useState('')
  const [declining, setDeclining] = useState(false)
  const [declineError, setDeclineError] = useState<string | null>(null)

  useEffect(() => {
    if (elder && currentUser) {
      console.info('[audit] opened care plan', {
        actor: currentUser.displayName,
        elderId: elder.id,
        at: new Date().toISOString(),
      })
    }
  }, [elder, currentUser])

  // Loads the elder's latest plan and its tasks. An elder with no plan yet keeps the empty-draft
  // defaults above. Goes through useQuery (not a plain effect) so React 18 StrictMode's dev-mode
  // double-mount doesn't fire the GETs twice.
  const { data: latestPlan } = useQuery({
    queryKey: ['carePlan', 'latest', elder?.id],
    queryFn: () => fetchLatestCarePlan(elder!.id),
    enabled: elder !== undefined,
  })

  const { data: planNodes } = useQuery({
    queryKey: ['carePlan', 'nodes', latestPlan?.id],
    queryFn: () => fetchCarePlanNodes(latestPlan!.id),
    enabled: latestPlan != null,
  })

  /** Shows a plan version as loaded from the backend, discarding any local edits. */
  function showPlan(plan: CarePlanResponse, nodes: CarePlanNodeResponse[]) {
    setTree(fromCarePlanNodeResponses(nodes))
    setVersion(plan.status === 'DRAFT' ? plan.version - 1 : plan.version)
    setStartDate(plan.startDate ?? '')
    if (plan.status === 'PUBLISHED') {
      setStatus('published')
    } else if (plan.status === 'STOPPED') {
      setStatus('stopped')
      setStopInfo({
        effectiveDate: plan.stopEffectiveDate ?? '',
        reason: plan.stopReason ?? '',
      })
    } else {
      setStatus('draft')
    }
  }

  useEffect(() => {
    if (!latestPlan || !planNodes) return
    showPlan(latestPlan, planNodes)
  }, [latestPlan, planNodes])

  // An elder with no plan yet starts from what the family applied for, so the first draft holds
  // exactly the activities they chose. latestPlan is undefined while loading and null once the
  // backend has confirmed there is no plan; a first draft saved with nothing in it is filled in
  // the same way. The filled-in draft is only saved once the manager edits it.
  const emptyFirstDraft = latestPlan?.status === 'DRAFT' && latestPlan.version === 1 && planNodes?.length === 0
  useEffect(() => {
    if (seeded || !(latestPlan === null || emptyFirstDraft) || !careRequests || !catalog) return
    setSeeded(true)
    setTree(seedPlanFromRequests(requestedNeeds(careRequests), catalog))
  }, [seeded, latestPlan, emptyFirstDraft, careRequests, catalog])

  const autosave = useDraftAutosave(elder?.id, latestPlan?.status === 'DRAFT' ? latestPlan.id : null)
  const scheduleSave = autosave.schedule

  // Every edit saves the whole draft once the manager pauses; loading a plan never does.
  useEffect(() => {
    if (revision === scheduledRevision.current) return
    scheduledRevision.current = revision
    scheduleSave({ startDate, nodes: toPlanNodePayloads(tree) })
  }, [revision, tree, startDate, scheduleSave])

  // A draft saved on the backend compares against the published version it supersedes.
  useEffect(() => {
    if (latestPlan?.status !== 'DRAFT' || !versions) return
    const superseded = versions.find((v) => v.id === latestPlan.supersedesPlanId)
    setPriorPublishedHours(superseded?.totalHours == null ? undefined : Number(superseded.totalHours))
  }, [latestPlan, versions])

  function refreshVersions() {
    void queryClient.invalidateQueries({ queryKey: ['carePlan', 'versions', elderId] })
  }

  if (elderLoading || elderError || !elder) {
    return (
      <ManagerShell>
        <MetaText className={styles.pageMessage}>{elderLoading ? 'Loading elder…' : 'Elder not found.'}</MetaText>
      </ManagerShell>
    )
  }

  // A stopped plan is history: no more sub-plans, tasks or edits — just what it looked like
  // when it was stopped.
  const locked = status === 'stopped'
  // Editing (add/edit/delete sub-plans and tasks) is only available once the manager has
  // entered draft mode via "Edit plan" — opening a published plan starts read-only.
  const editable = !locked && status === 'draft'
  /** Whether the backend holds a draft, or is about to, for Discard to throw away. */
  const hasDraft = latestPlan?.status === 'DRAFT' || autosave.state !== 'idle'
  /** Catalog activities the family applied for that this version doesn't include — named before publishing. */
  const leftOut = needs
    .filter((need) => requestedNeedStatus(need, catalog ?? [], tree) === 'missing')
    .map((need) => careNeedLabel(catalog, need))

  function toggleCollapsed(id: string) {
    setCollapsed((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  function enterDraft(baselineHours: number) {
    if (status === 'published') {
      setPriorPublishedHours(baselineHours)
      setStatus('draft')
    }
  }

  function markEdited() {
    setRevision((n) => n + 1)
  }

  function removeTask(taskId: string) {
    enterDraft(totalHours)
    setTree((prev) => removeNode(prev, taskId))
    markEdited()
  }

  function findTask(taskId: string): TaskNode | undefined {
    for (const node of tree) {
      if (node.type === 'task' && node.id === taskId) return node
      if (node.type === 'subplan') {
        const task = node.children.find((child) => child.id === taskId)
        if (task) return task
      }
    }
    return undefined
  }

  function submitEditTask(taskId: string, name: string, schedule: DayScheduleValue) {
    const task = findTask(taskId)
    if (!task) return
    enterDraft(totalHours)
    const updated: TaskNode = {
      ...task,
      name: name.trim() || task.name,
      visits: visitsFromDaySchedule(schedule),
    }
    setTree((prev) => updateTask(prev, task.id, updated))
    markEdited()
    setEditingTaskId(null)
  }

  function confirmDeleteSubPlan() {
    if (!deleteTarget) return
    enterDraft(totalHours)
    setTree((prev) => removeNode(prev, deleteTarget.id))
    markEdited()
    setDeleteTarget(null)
  }

  function submitAddSubPlan(activity: string, schedule: DayScheduleValue) {
    enterDraft(totalHours)
    const catalogActivity = catalog?.find((a) => a.label === activity)
    const task: TaskNode = {
      id: `task-${Date.now()}`,
      type: 'task',
      activityCode: catalogActivity?.code ?? null,
      name: activity,
      visits: visitsFromDaySchedule(schedule),
      evidence: 'CHECKLIST',
    }
    // Activities are filed under their catalog category, so a second activity from the same
    // category joins the existing sub-plan rather than starting a new one.
    const groupName = catalogActivity?.category ?? activity
    const existing = tree.find((n): n is SubPlanNode => n.type === 'subplan' && n.name === groupName)
    if (existing) {
      setTree((prev) =>
        prev.map((n) => (n.id === existing.id && n.type === 'subplan' ? { ...n, children: [...n.children, task] } : n)),
      )
      setCollapsed((prev) => {
        const next = new Set(prev)
        next.delete(existing.id)
        return next
      })
    } else {
      const subplan: SubPlanNode = {
        id: `subplan-${Date.now()}`,
        type: 'subplan',
        name: groupName,
        children: [task],
      }
      setTree((prev) => [...prev, subplan])
    }
    markEdited()
    setAddPanelOpen(false)
  }

  async function publish() {
    if (!startDate) return
    setPublishing(true)
    setPublishError(null)
    try {
      // Publish the draft the edits were saved to (or open one), once any pending save has landed.
      await autosave.settle()
      const planId = await autosave.openDraft()
      const published = await publishCarePlan(planId, startDate, toPlanNodePayloads(tree))
      autosave.published()
      void queryClient.invalidateQueries({ queryKey: ['carePlan', 'latest', elder!.id] })
      refreshVersions()
      setVersion(published.version)
      setStatus('published')
      setPriorPublishedHours(undefined)
      setStopInfo(null)
      setShowPublishModal(false)
    } catch (err) {
      setPublishError(err instanceof Error ? err.message : 'Could not publish this plan.')
    } finally {
      setPublishing(false)
    }
  }

  /** Throws the draft away: back to the version in force, or to what the family applied for. */
  async function discardDraft() {
    setDiscarding(true)
    setDiscardError(null)
    try {
      await autosave.discard()
      setShowDiscardModal(false)
      setPriorPublishedHours(undefined)
      if (latestPlan?.status === 'DRAFT') {
        // The draft was loaded with the page: reload what the elder has now.
        setSeeded(false)
        await queryClient.invalidateQueries({ queryKey: ['carePlan', 'latest', elder!.id] })
      } else if (latestPlan && planNodes) {
        showPlan(latestPlan, planNodes)
      } else {
        setStartDate('')
        setSeeded(false)
      }
      refreshVersions()
    } catch (err) {
      setDiscardError(err instanceof Error ? err.message : 'Could not discard this draft.')
    } finally {
      setDiscarding(false)
    }
  }

  /** Tells the family the care team won't plan their application, and why. */
  async function declineApplication() {
    if (!declineTarget || !declineReason.trim()) return
    setDeclining(true)
    setDeclineError(null)
    try {
      await declineServiceApplication(declineTarget.applicationId, declineReason.trim())
      await queryClient.invalidateQueries({ queryKey: ['elderCareRequests', elderId] })
      setDeclineTarget(null)
    } catch (err) {
      setDeclineError(err instanceof Error ? err.message : 'Could not decline this application.')
    } finally {
      setDeclining(false)
    }
  }

  // A published plan whose start date hasn't arrived yet isn't live — say so.
  const badgeStatus: BadgeStatus =
    status === 'published' && startDate && startDate > todayIso() ? 'scheduled' : status

  const history: VersionEntry[] = [
    ...(status === 'draft' ? [{ n: version + 1, date: 'editing', status: 'draft' as const }] : []),
    ...(versions ?? [])
      .filter((v) => v.status !== 'DRAFT')
      .map((v) => ({
        n: v.version,
        date: v.publishedAt ? formatDate(v.publishedAt) : '—',
        summary: [
          v.totalHours != null && `${formatHoursFixed(Number(v.totalHours))}/week`,
          v.startDate && `from ${formatDate(v.startDate)}`,
        ]
          .filter(Boolean)
          .join(' · '),
        status: versionStatus(v),
      })),
  ]

  const headerContext = (
    <span>
      <Link to="/manager/elders" className={styles.breadcrumbLink}>
        Elders
      </Link>
      {' / '}
      <Link to="/manager/elders" className={styles.breadcrumbLink}>
        {elder.name}
      </Link>
      {' / care plan'}
    </span>
  )

  const headerRight = (
    <>
      <Badge status={badgeStatus} version={status === 'draft' ? version + 1 : version} />
      <UserIdentity />
    </>
  )

  const editingTask = editingTaskId ? findTask(editingTaskId) : undefined

  const main = (
    <>
      {status === 'stopped' && stopInfo && (
        <div className={styles.banner}>
          <Callout tone="neutral">
            Stopped effective {stopInfo.effectiveDate || '—'} — {stopInfo.reason || 'no reason on file'}. This plan
            and its history are kept; create a new plan to resume care.
          </Callout>
        </div>
      )}

      <PageHeader
        back={<BackLink to="/manager/elders">Back to Elders</BackLink>}
        title="Care plan"
        meta={[
          `${subPlans} sub-plans`,
          `${tasks} tasks`,
          `${formatHoursLoose(totalHours)} / week`,
          elder.sector && `sector ${elder.sector}`,
        ]
          .filter(Boolean)
          .join(' · ')}
        actions={
          !locked && (
            <>
              {editable && <DraftSaveStatus state={autosave.state} onRetry={() => void autosave.retry()} />}
              {editable && hasDraft && (
                <Button variant="ghost" onClick={() => setShowDiscardModal(true)}>
                  Discard draft
                </Button>
              )}
              {editable && <Button onClick={() => setAddPanelOpen(true)}>Add sub-plan</Button>}
              <Button
                variant="primary"
                disabled={editable && (tasks === 0 || !startDate || unscheduled.length > 0)}
                onClick={() => (editable ? setShowPublishModal(true) : enterDraft(totalHours))}
              >
                {editable ? `Publish v${version + 1}` : 'Edit plan'}
              </Button>
            </>
          )
        }
      >
        <Field label="Starts" inline>
          {(id) => (
            <DateInput
              id={id}
              value={startDate}
              disabled={!editable}
              onChange={(value) => {
                setStartDate(value)
                markEdited()
              }}
            />
          )}
        </Field>
      </PageHeader>

      {editable && unscheduled.length > 0 && (
        <div className={styles.banner}>
          <Callout tone="info" role="status">
            Set days and times for {unscheduled.map((task) => task.name).join(', ')} before publishing — the family
            chose these activities, not when they happen.
          </Callout>
        </div>
      )}

      {editable ? (
        <PlanTreeEditor
          items={treeItems}
          collapsedIds={collapsed}
          onToggle={toggleCollapsed}
          total={formatHoursFixed(totalHours)}
          onEditTask={setEditingTaskId}
          onDeleteTask={removeTask}
          onDeleteSubPlan={(id) => setDeleteTarget(findSubPlan(tree, id) ?? null)}
          editingTask={
            editingTask
              ? { id: editingTask.id, name: editingTask.name, schedule: dayScheduleFromVisits(editingTask.visits) }
              : null
          }
          onSaveTask={submitEditTask}
          onCancelEdit={() => setEditingTaskId(null)}
          adding={addPanelOpen}
          activityOptions={activityOptions}
          onAdd={submitAddSubPlan}
          onCancelAdd={() => setAddPanelOpen(false)}
        />
      ) : (
        <PlanTreeView
          items={treeItems}
          collapsedIds={collapsed}
          onToggle={toggleCollapsed}
          total={formatHoursFixed(totalHours)}
        />
      )}
    </>
  )

  const rail = (
    <SidePanel
      label="Elder"
      sections={[
        <div key="elder" className={styles.railGroup}>
          <Eyebrow>Elder</Eyebrow>
          <IdentityHeader
            name={elder.name}
            meta={[elder.age, elder.id, elder.sector].filter((part) => part !== '' && part !== null).join(' · ')}
          />
        </div>,
        <KeyValueList
          key="profile"
          variant="ruled"
          items={[
            { label: 'Dialect', value: dialectsLabel(elder.preferredDialects) },
            { label: 'Lives', value: livesLabel(elder.livesAlone) },
            { label: 'Family', value: family ? familyLabel(family) : '…' },
            { label: 'Mobility', value: mobilityLabel(elder.mobilityLevel) },
            { label: 'Continuity', value: continuityLabel(elder.continuityPreference) },
          ]}
        />,
        careRequests && careRequests.length > 0 && (
          <FamilyRequestsPanel
            key="requested"
            requests={careRequests}
            label={(need) => careNeedLabel(catalog, need)}
            needTag={(need) => REQUESTED_NEED_TAG[requestedNeedStatus(need, catalog ?? [], tree)]}
            onDecline={(request) => {
              setDeclineTarget(request)
              setDeclineReason('')
              setDeclineError(null)
            }}
          />
        ),
        history.length > 0 && (
          <div key="history" className={styles.railGroup}>
            <Eyebrow>Version history</Eyebrow>
            <VersionHistoryList versions={history} />
          </div>
        ),
        status === 'draft' && priorPublishedHours !== undefined && (
          <Callout key="delta" tone="info" role="status">
            Draft v{version + 1} changes weekly effort {formatHoursFixed(priorPublishedHours)} →{' '}
            {formatHoursFixed(totalHours)}. Publishing reschedules upcoming visits to match.
          </Callout>
        ),
      ]}
      footer={
        status === 'published' && (
          <StopCarePlanButton onClick={() => setShowStopModal(true)} />
        )
      }
    />
  )

  const deleteTaskCount = deleteTarget?.children.length ?? 0

  return (
    <ManagerShell headerContext={headerContext} headerRight={headerRight}>
      <SplitLayout main={main} rail={rail} />

      {showPublishModal && (
        <ConfirmDialog
          eyebrow="Publish"
          title={`Publish care plan v${version + 1}?`}
          meta={elder.name}
          confirmLabel={publishing ? 'Publishing…' : 'Publish'}
          busy={publishing}
          confirmDisabled={!startDate}
          onConfirm={publish}
          onCancel={() => setShowPublishModal(false)}
        >
          <BodyText>
            This publishes v{version + 1} at {formatHoursFixed(totalHours)}/week
            {priorPublishedHours !== undefined ? ` (from ${formatHoursFixed(priorPublishedHours)})` : ''}.
            Upcoming visits that haven't started are rescheduled to match.
          </BodyText>
          {leftOut.length > 0 && (
            <Callout tone="info">
              The family asked for {leftOut.join(', ')}, which {leftOut.length === 1 ? 'is' : 'are'} not in this
              version. You can still publish.
            </Callout>
          )}
          {publishError && (
            <Callout tone="danger" role="alert">
              {publishError}
            </Callout>
          )}
        </ConfirmDialog>
      )}

      {showDiscardModal && (
        <ConfirmDialog
          tone="danger"
          eyebrow="Discard draft"
          title={`Discard draft v${version + 1}?`}
          meta={elder.name}
          confirmLabel={discarding ? 'Discarding…' : 'Discard draft'}
          busy={discarding}
          onConfirm={discardDraft}
          onCancel={() => setShowDiscardModal(false)}
        >
          <BodyText>
            {version > 0
              ? `Your changes are thrown away and v${version} stays in force.`
              : 'Your changes are thrown away and the plan goes back to what the family applied for.'}
          </BodyText>
          {discardError && (
            <Callout tone="danger" role="alert">
              {discardError}
            </Callout>
          )}
        </ConfirmDialog>
      )}

      {declineTarget && (
        <ConfirmDialog
          tone="danger"
          eyebrow="Decline application"
          title={`Decline service application #${declineTarget.applicationId}?`}
          meta={elder.name}
          confirmLabel={declining ? 'Declining…' : 'Decline'}
          busy={declining}
          confirmDisabled={!declineReason.trim()}
          onConfirm={declineApplication}
          onCancel={() => setDeclineTarget(null)}
        >
          <BodyText>
            The family is told it won't be added to the care plan, with your reason. If you later add everything it
            asked for, it shows as planned instead.
          </BodyText>
          <Field label="Reason for the family">
            {(id) => (
              <TextArea
                id={id}
                value={declineReason}
                onChange={setDeclineReason}
                maxLength={255}
                disabled={declining}
              />
            )}
          </Field>
          {declineError && (
            <Callout tone="danger" role="alert">
              {declineError}
            </Callout>
          )}
        </ConfirmDialog>
      )}

      {showStopModal && (
        <StopCarePlanDialog
          elder={elder}
          onClose={() => setShowStopModal(false)}
          onStopped={(stopped) => {
            refreshVersions()
            setStatus('stopped')
            setStopInfo({
              effectiveDate: stopped.stopEffectiveDate ?? '',
              reason: stopped.stopReason ?? '',
            })
            setShowStopModal(false)
          }}
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          tone="danger"
          eyebrow="Delete sub-plan"
          title={`Delete "${deleteTarget.name}"?`}
          confirmLabel="Delete sub-plan"
          onConfirm={confirmDeleteSubPlan}
          onCancel={() => setDeleteTarget(null)}
        >
          <BodyText>
            This sub-plan has {deleteTaskCount} {deleteTaskCount === 1 ? 'task' : 'tasks'} totalling{' '}
            {formatHoursFixed(weeklyHours(deleteTarget))}/week. Deleting it removes{' '}
            {deleteTaskCount === 1 ? 'that task' : deleteTaskCount === 2 ? 'both tasks' : `all ${deleteTaskCount} tasks`}{' '}
            from the care plan — this can't be undone.
          </BodyText>
        </ConfirmDialog>
      )}
    </ManagerShell>
  )
}

/** Where the draft stands on the backend, beside the editor's actions. */
function DraftSaveStatus({ state, onRetry }: { state: DraftSaveState; onRetry: () => void }) {
  if (state === 'failed') {
    return (
      <span aria-live="polite">
        <MetaText as="span" tone="danger">
          Couldn't save draft
        </MetaText>{' '}
        <Button variant="ghost" onClick={onRetry}>
          Retry
        </Button>
      </span>
    )
  }
  const text = { idle: '', pending: 'Unsaved changes', saving: 'Saving…', saved: 'Draft saved' }[state]
  return (
    <span aria-live="polite">
      <MetaText as="span" tone="faint">
        {text}
      </MetaText>
    </span>
  )
}
