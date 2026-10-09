import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ManagerShell } from '../components/ManagerShell'
import { UserIdentity } from '../components/UserIdentity'
import { ACTIVITY_CATALOG, activityCategory } from '../data/carePlans'
import type { PlanNode, SubPlanNode, TaskNode } from '../data/carePlans'
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
import {
  createCarePlanDraft,
  fetchCarePlanNodes,
  fetchLatestCarePlan,
  publishCarePlan,
} from '../../../shared/api/careplan'
import type { CarePlanResponse, PlanNodePayload } from '../../../shared/api/careplan'
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
} from '../../../shared/components/ui'
import type { BadgeStatus, SelectGroup } from '../../../shared/components/ui'
import { PlanTreeEditor } from '../components/PlanTreeEditor'
import { StopCarePlanButton } from '../components/StopCarePlanButton'
import { StopCarePlanDialog } from '../components/StopCarePlanDialog'
import { VersionHistoryList } from '../components/VersionHistoryList'
import type { VersionEntry } from '../components/VersionHistoryList'
import type { DayScheduleValue } from '../components/weekdays'
import styles from './CarePlan.module.css'

const ACTIVITY_OPTIONS: SelectGroup[] = ACTIVITY_CATALOG.map((group) => ({
  group: group.category,
  items: group.activities.map((activity) => ({ value: activity, label: activity })),
}))

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

  useEffect(() => {
    if (!latestPlan || !planNodes) return
    setTree(fromCarePlanNodeResponses(planNodes))
    setVersion(latestPlan.status === 'DRAFT' ? latestPlan.version - 1 : latestPlan.version)
    setStartDate(latestPlan.startDate ?? '')
    if (latestPlan.status === 'PUBLISHED') {
      setStatus('published')
    } else if (latestPlan.status === 'STOPPED') {
      setStatus('stopped')
      setStopInfo({
        effectiveDate: latestPlan.stopEffectiveDate ?? '',
        reason: latestPlan.stopReason ?? '',
      })
    } else {
      setStatus('draft')
    }
  }, [latestPlan, planNodes])

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

  function removeTask(taskId: string) {
    enterDraft(totalHours)
    setTree((prev) => removeNode(prev, taskId))
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
    setEditingTaskId(null)
  }

  function confirmDeleteSubPlan() {
    if (!deleteTarget) return
    enterDraft(totalHours)
    setTree((prev) => removeNode(prev, deleteTarget.id))
    setDeleteTarget(null)
  }

  function submitAddSubPlan(activity: string, schedule: DayScheduleValue) {
    enterDraft(totalHours)
    const task: TaskNode = {
      id: `task-${Date.now()}`,
      type: 'task',
      name: activity,
      visits: visitsFromDaySchedule(schedule),
      evidence: 'CHECKLIST',
    }
    // Activities are filed under their catalog category, so a second activity from the same
    // category joins the existing sub-plan rather than starting a new one.
    const groupName = activityCategory(activity) ?? activity
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
    setAddPanelOpen(false)
  }

  /** The draft this publish writes to: reuses one already open on the backend, or opens one.
   * Only called from the publish dialog, which only renders once the elder guard above has
   * passed, but that narrowing doesn't reach this nested function declaration. */
  async function getOrCreateDraftPlanId(): Promise<number> {
    const currentElderId = elder!.id
    const latest = await fetchLatestCarePlan(currentElderId)
    if (latest && latest.status === 'DRAFT') return latest.id
    const created = await createCarePlanDraft(currentElderId)
    return created.id
  }

  async function publish() {
    if (!startDate) return
    setPublishing(true)
    setPublishError(null)
    try {
      const planId = await getOrCreateDraftPlanId()
      const published = await publishCarePlan(planId, startDate, toPlanNodePayloads(tree))
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
              {editable && <Button onClick={() => setAddPanelOpen(true)}>Add sub-plan</Button>}
              <Button
                variant="primary"
                disabled={editable && (tasks === 0 || !startDate)}
                onClick={() => (editable ? setShowPublishModal(true) : enterDraft(totalHours))}
              >
                {editable ? `Publish v${version + 1}` : 'Edit plan'}
              </Button>
            </>
          )
        }
      >
        <Field label="Starts" inline>
          {(id) => <DateInput id={id} value={startDate} disabled={!editable} onChange={setStartDate} />}
        </Field>
      </PageHeader>

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
          activityOptions={ACTIVITY_OPTIONS}
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
          {publishError && (
            <Callout tone="danger" role="alert">
              {publishError}
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
