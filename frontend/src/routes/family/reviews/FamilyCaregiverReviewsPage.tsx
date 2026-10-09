import { useEffect, useState } from 'react'
import { useFamilyElders } from '../../../features/family-account/useFamilyAccount'
import { ApiError } from '../../../shared/api/client'
import {
  createCaregiverReview,
  fetchCaregiverReviews,
  fetchReviewableCaregivers,
} from '../../../features/caregiver-reviews/api'
import type {
  CaregiverReview,
  RenewalDecision,
  ReviewableCaregiver,
} from '../../../features/caregiver-reviews/types'
import { useSelectedElder } from '../components/selectedElder'
import styles from './FamilyCaregiverReviewsPage.module.css'

const decisions: { value: RenewalDecision; label: string }[] = [
  { value: 'RENEW_CURRENT', label: 'Renew current caregiver' },
  { value: 'REQUEST_CHANGE', label: 'Request a different caregiver' },
  { value: 'CANCEL_SERVICE', label: 'Cancel service' },
]

export function FamilyCaregiverReviewsPage() {
  const elders = useFamilyElders()
  const { elderId } = useSelectedElder()
  const selectedElderId = elderId ?? elders.data?.[0]?.id ?? null
  const [caregivers, setCaregivers] = useState<ReviewableCaregiver[]>([])
  const [reviews, setReviews] = useState<CaregiverReview[]>([])
  const [caregiverId, setCaregiverId] = useState<number | null>(null)
  const [periodStart, setPeriodStart] = useState('')
  const [periodEnd, setPeriodEnd] = useState('')
  const [overallRating, setOverallRating] = useState(5)
  const [punctualityScore, setPunctualityScore] = useState(5)
  const [careQualityScore, setCareQualityScore] = useState(5)
  const [feedbackNotes, setFeedbackNotes] = useState('')
  const [renewalDecision, setRenewalDecision] = useState<RenewalDecision>('RENEW_CURRENT')
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  useEffect(() => {
    if (selectedElderId === null) return
    let cancelled = false
    setLoading(true)
    setError(null)
    Promise.all([
      fetchReviewableCaregivers(selectedElderId),
      fetchCaregiverReviews(selectedElderId),
    ])
      .then(([availableCaregivers, existingReviews]) => {
        if (cancelled) return
        setCaregivers(availableCaregivers)
        setReviews(existingReviews)
        setCaregiverId(availableCaregivers[0]?.id ?? null)
      })
      .catch((cause) => !cancelled && setError(
        cause instanceof ApiError ? cause.message : 'Unable to load caregiver reviews.',
      ))
      .finally(() => !cancelled && setLoading(false))
    return () => { cancelled = true }
  }, [selectedElderId])

  async function submit() {
    if (selectedElderId === null || caregiverId === null || !periodStart || !periodEnd) {
      setError('Choose a caregiver and review period.')
      return
    }
    setSubmitting(true)
    setError(null)
    setSuccess(null)
    try {
      const created = await createCaregiverReview({
        elderId: selectedElderId,
        caregiverId,
        periodStart,
        periodEnd,
        overallRating,
        punctualityScore,
        careQualityScore,
        feedbackNotes: feedbackNotes.trim() || null,
        renewalDecision,
      })
      setReviews((current) => [created, ...current])
      setFeedbackNotes('')
      setSuccess('Review submitted successfully.')
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Unable to submit the review.')
    } finally {
      setSubmitting(false)
    }
  }

  return <div className={styles.page}>
    <header className={styles.band}>
      <p className={styles.eyebrow}>CAREGIVER REVIEW</p>
      <h1>Review caregiver and renewal</h1>
      <p>Rate care over a period and record the family's renewal decision.</p>
    </header>

    {error && <div className={styles.error} role="alert">{error}</div>}
    {success && <div className={styles.success} role="status">{success}</div>}
    {loading && <p>Loading caregiver reviews...</p>}
    {!loading && selectedElderId === null && <p className={styles.card}>No linked elder is available.</p>}

    {!loading && selectedElderId !== null && <>
      <section className={styles.card}>
        <h2>New periodic review</h2>
        {caregivers.length === 0 ? <p className={styles.muted}>No caregiver with a visit relationship is available for review.</p> : <>
          <label className={styles.field}>Caregiver
            <select value={caregiverId ?? ''} onChange={(event) => setCaregiverId(Number(event.target.value))}>
              {caregivers.map((caregiver) => <option key={caregiver.id} value={caregiver.id}>{caregiver.fullName}</option>)}
            </select>
          </label>
          <div className={styles.twoColumns}>
            <label className={styles.field}>Period start
              <input type="date" value={periodStart} onChange={(event) => setPeriodStart(event.target.value)} />
            </label>
            <label className={styles.field}>Period end
              <input type="date" value={periodEnd} onChange={(event) => setPeriodEnd(event.target.value)} />
            </label>
          </div>
          <div className={styles.scores}>
            <Score label="Overall rating" value={overallRating} onChange={setOverallRating} />
            <Score label="Punctuality" value={punctualityScore} onChange={setPunctualityScore} />
            <Score label="Care quality" value={careQualityScore} onChange={setCareQualityScore} />
          </div>
          <label className={styles.field}>Feedback
            <textarea value={feedbackNotes} onChange={(event) => setFeedbackNotes(event.target.value)} placeholder="Optional feedback" />
          </label>
          <fieldset className={styles.decisions}>
            <legend>Renewal decision</legend>
            {decisions.map((decision) => <label key={decision.value}>
              <input type="radio" name="renewal" value={decision.value} checked={renewalDecision === decision.value} onChange={() => setRenewalDecision(decision.value)} />
              {decision.label}
            </label>)}
          </fieldset>
          <button className={styles.primary} disabled={submitting} onClick={submit}>
            {submitting ? 'Submitting...' : 'Submit review'}
          </button>
        </>}
      </section>

      <section className={styles.card}>
        <h2>Review history</h2>
        {reviews.length === 0 ? <p className={styles.muted}>No caregiver reviews yet.</p> : reviews.map((review) => <article className={styles.review} key={review.id}>
          <div><strong>{review.caregiverName}</strong><p>{review.periodStart} to {review.periodEnd}</p></div>
          <dl>
            <div><dt>Overall</dt><dd>{review.overallRating}/5</dd></div>
            <div><dt>Punctuality</dt><dd>{review.punctualityScore ?? 'Not rated'}</dd></div>
            <div><dt>Care quality</dt><dd>{review.careQualityScore ?? 'Not rated'}</dd></div>
            <div><dt>Decision</dt><dd>{review.renewalDecision}</dd></div>
          </dl>
          {review.feedbackNotes && <p>{review.feedbackNotes}</p>}
        </article>)}
      </section>
    </>}
  </div>
}

function Score({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return <label className={styles.field}>{label}
    <select value={value} onChange={(event) => onChange(Number(event.target.value))}>
      {[1, 2, 3, 4, 5].map((score) => <option key={score} value={score}>{score}</option>)}
    </select>
  </label>
}
