import { useEffect, useRef, useState } from 'react'
import { checkIn, checkOut, completeTask, saveHealthRecord, type CommandIdentity, type CheckInInput, type TaskInput, type HealthInput, type HealthFlag } from '../../features/caregiver-execution/api'
import { isAccessFailure, useSelfServiceWrite } from './selfService'
import { unknownResult } from './commandErrors'

type TaskDraft = { status: 'DONE' | 'SKIPPED' | 'REFUSED'; outcome: string; caregiverNote: string }
type Attempt = { visitId: number } & ({ kind: 'CHECK_IN'; input: CheckInInput } | { kind: 'CHECK_OUT'; input: CommandIdentity } | { kind: 'TASK_RESULT'; taskId: number; input: TaskInput } | { kind: 'HEALTH_RECORD'; input: HealthInput })
export type HealthDraft = { systolic: string; diastolic: string; pulse: string; temperature: string; healthFlag: HealthFlag | ''; healthNote: string }
const blankHealth: HealthDraft = { systolic: '', diastolic: '', pulse: '', temperature: '', healthFlag: '', healthNote: '' }
/** Mounted in the work-pack query owner, so a return refresh does not erase drafts. */
export function useExecutionCommands(reload: () => void) {
  const write = useSelfServiceWrite()
  const [attempt, setAttempt] = useState<Attempt | null>(null)
  const inFlight = useRef(false)
  const retainedAttempt = useRef<Attempt | null>(null)
  const [drafts, setDrafts] = useState<Record<number, TaskDraft>>({})
  const [mode, setMode] = useState<'GPS' | 'MANUAL_LOCATION_NOTE'>('GPS')
  const [note, setNote] = useState('')
  const [fix, setFix] = useState<{ latitude: number; longitude: number; accuracy: number; clientCapturedAt: string } | null>(null)
  const [locating, setLocating] = useState(false)
  const generation = useRef(0)
  useEffect(() => () => { generation.current++ }, [])
  const [validation, setValidation] = useState('')
  const [saved, setSaved] = useState('')
  const [healthDraft, setHealthDraft] = useState<HealthDraft>(blankHealth)
  const [healthEditing, setHealthEditing] = useState(false)
  const [healthAccessError, setHealthAccessError] = useState<unknown>(null)
  const accessError = healthAccessError ?? (isAccessFailure(write.error) ? write.error : null)
  function clearProtected() {
    retainedAttempt.current = null
    generation.current++; setDrafts({}); setAttempt(null); setNote(''); setFix(null); setSaved(''); setValidation(''); setLocating(false)
    setHealthDraft(blankHealth); setHealthEditing(false)
  }
  function send(next: Attempt) {
    // Guard synchronously too: a second click before React renders must not replace the retry key.
    if (inFlight.current || (retainedAttempt.current !== null && retainedAttempt.current !== next)) return
    inFlight.current = true; retainedAttempt.current = next
    setAttempt(next); setSaved(''); setValidation('')
    void write.send<unknown>(signal => {
      if (next.kind === 'CHECK_IN') return checkIn(next.visitId, next.input, signal)
      if (next.kind === 'CHECK_OUT') return checkOut(next.visitId, next.input, signal)
      if (next.kind === 'HEALTH_RECORD') return saveHealthRecord(next.visitId, next.input, signal)
      return completeTask(next.visitId, next.taskId, next.input, signal)
    }, () => {
      if (next.kind === 'TASK_RESULT') setDrafts(old => { const copy = { ...old }; delete copy[next.taskId]; return copy })
      if (next.kind === 'HEALTH_RECORD') { setHealthDraft(blankHealth); setHealthEditing(false) }
      retainedAttempt.current = null
      setAttempt(null); setNote(''); setFix(null); setSaved(next.kind === 'HEALTH_RECORD' ? 'Health record saved. Reloading the work pack…' : 'Saved on the server. Reloading the work pack…'); reload()
    }).finally(() => { inFlight.current = false })
  }
  function locate() {
    if (!navigator.geolocation) { setValidation('Location is unavailable. You may choose a clearly marked manual location note.'); return }
    const token = ++generation.current; setLocating(true); setValidation('')
    navigator.geolocation.getCurrentPosition(position => {
      if (token !== generation.current) return
      setFix({ latitude: position.coords.latitude, longitude: position.coords.longitude, accuracy: position.coords.accuracy, clientCapturedAt: new Date(position.timestamp).toISOString() }); setLocating(false)
    }, () => {
      if (token !== generation.current) return
      setLocating(false); setValidation('GPS could not be obtained. Retry GPS or choose a manual location note; no GPS verification will be claimed.')
    }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 })
  }
  return { write, attempt, drafts, setDrafts, mode, setMode, note, setNote, fix, locating, locate, validation, setValidation, saved, accessError, clearProtected,
    healthDraft, setHealthDraft, healthEditing, setHealthAccessError,
    hasHealthDraft: Object.values(healthDraft).some(Boolean) || healthEditing,
    anotherMeasurement: () => { setHealthDraft(blankHealth); setHealthEditing(true); setSaved(''); setValidation('') },
    blocked: write.pending || attempt !== null || locating,
    uncertain: write.error != null && unknownResult(write.error),
    send, retry: () => { if (attempt) send(attempt) }, edit: () => { retainedAttempt.current = null; setAttempt(null); write.clearError(); reload() }, recover: () => { setHealthAccessError(null); write.clearError(); reload() },
    recordHealth: (visitId: number, version: number) => {
      const { systolic, diastolic, pulse, temperature, healthFlag, healthNote } = healthDraft
      if (!healthFlag) { setValidation('Select a health observation.'); return }
      if (Boolean(systolic) !== Boolean(diastolic)) { setValidation('Enter both blood pressure values or leave both blank.'); return }
      const raw = [systolic, diastolic, pulse, temperature]
      if (raw.some((value, index) => value !== '' && (!(index === 3 ? /^\d+(\.\d{1,2})?$/ : /^\d+$/).test(value) || Number(value) <= 0 || Number(value) > 999999.99))) {
        setValidation('Use positive whole numbers for blood pressure and pulse, and up to two decimal places for temperature.'); return
      }
      if ((healthFlag !== 'NO_CONCERN' || raw.every(value => value === '')) && !healthNote.trim()) { setValidation('Explain the concern or why no readings were measured.'); return }
      if (healthNote.length > 1000) { setValidation('Health note must not exceed 1000 characters.'); return }
      send({ kind: 'HEALTH_RECORD', visitId, input: { systolic: systolic ? Number(systolic) : null, diastolic: diastolic ? Number(diastolic) : null,
        pulse: pulse ? Number(pulse) : null, temperature: temperature ? Number(temperature) : null, healthFlag, healthNote: healthNote.trim() || null,
        expectedVersion: version, clientRequestId: crypto.randomUUID() } })
    },
    finishVisit: (visitId: number, version: number) => {
      send({ kind: 'CHECK_OUT', visitId, input: { expectedVersion: version, clientRequestId: crypto.randomUUID() } })
    },
    start: (visitId: number, version: number) => {
      if (mode === 'GPS' && !fix) { setValidation('Obtain a GPS fix before checking in.'); return }
      if (mode === 'MANUAL_LOCATION_NOTE' && (!note.trim() || note.trim().length > 500)) { setValidation('Write a manual location note of 1 to 500 characters.'); return }
      send({ kind: 'CHECK_IN', visitId, input: { expectedVersion: version, clientRequestId: crypto.randomUUID(), locationSource: mode, ...(mode === 'GPS' ? fix! : { locationNote: note.trim() }) } })
    },
    finishTask: (visitId: number, taskId: number, version: number) => {
      const draft = drafts[taskId] ?? { status: 'DONE' as const, outcome: '', caregiverNote: '' }
      if (draft.status !== 'DONE' && !draft.caregiverNote.trim()) { setValidation('Skipped or refused tasks require a factual reason.'); return }
      send({ kind: 'TASK_RESULT', visitId, taskId, input: { ...draft, expectedVersion: version, clientRequestId: crypto.randomUUID() } })
    },
  }
}
