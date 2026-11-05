import {
  useEffect,
  useState,
} from 'react'
import type {
  FormEvent,
} from 'react'
import {
  getVisitsAwaitingConfirmation,
  submitVisitConfirmation,
} from '../../../features/visit-confirmation/api'
import type {
  ConfirmationStatus,
  PendingElderVisit,
} from '../../../features/visit-confirmation/types'
import {
  ApiError,
} from '../../../shared/api/client'
import { ElderShell } from '../components/ElderShell'
import {
  ActionStack,
  ChoiceRadio,
  InfoCard,
  InfoNote,
  ScreenColumns,
  ScreenFooter,
  ScreenHeader,
  Slot,
  SpeakButton,
  StatusNote,
  WideButton,
} from '../components/ElderUi'
import styles from '../Elder.module.css'

/** The answer controls sit in the right column; the visit picker on the left joins them by id. */
const FORM_ID = 'visit-confirmation'

function formatDateTime(
  value: string,
): string {
  return new Intl.DateTimeFormat(
    undefined,
    {
      dateStyle: 'medium',
      timeStyle: 'short',
    },
  ).format(
    new Date(value),
  )
}

function formatTime(
  value: string,
): string {
  return new Intl.DateTimeFormat(
    undefined,
    {
      timeStyle: 'short',
    },
  ).format(
    new Date(value),
  )
}

/**
 * "24 Sep 2026 · 10:00 to 11:00": the booked slot, so the elder recognises the visit they were
 * expecting. Just the date and start time when the visit was booked without an end.
 */
function formatVisitTimes(
  visit: PendingElderVisit,
): string {
  const day = new Intl.DateTimeFormat(
    undefined,
    {
      dateStyle: 'medium',
    },
  ).format(
    new Date(visit.scheduledStart),
  )

  const start = formatTime(
    visit.scheduledStart,
  )

  return visit.scheduledEnd
    ? `${day} · ${start} to ${formatTime(visit.scheduledEnd)}`
    : `${day} · ${start}`
}

export default function ConfirmVisit() {
  const [
    visits,
    setVisits,
  ] =
    useState<PendingElderVisit[]>([])

  const [
    selectedVisitId,
    setSelectedVisitId,
  ] =
    useState<number | null>(null)

  const [
    response,
    setResponse,
  ] =
    useState<ConfirmationStatus>(
      'CONFIRMED',
    )

  const [
    rating,
    setRating,
  ] =
    useState<number | null>(null)

  const [
    notes,
    setNotes,
  ] =
    useState('')

  const [
    loading,
    setLoading,
  ] =
    useState(true)

  const [
    submitting,
    setSubmitting,
  ] =
    useState(false)

  const [
    error,
    setError,
  ] =
    useState<string | null>(null)

  const [
    success,
    setSuccess,
  ] =
    useState<string | null>(null)

  useEffect(() => {
    let active = true

    getVisitsAwaitingConfirmation()
      .then((result) => {
        if (!active) {
          return
        }

        setVisits(result)

        if (result.length > 0) {
          setSelectedVisitId(
            result[0].visitId,
          )
        }
      })
      .catch(() => {
        if (active) {
          setError(
            'Unable to load visits awaiting confirmation.',
          )
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false)
        }
      })

    return () => {
      active = false
    }
  }, [])

  const selectedVisit =
    visits.find(
      (visit) =>
        visit.visitId ===
        selectedVisitId,
    ) ?? null

  async function handleSubmit(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (
      selectedVisit === null ||
      submitting
    ) {
      return
    }

    setSubmitting(true)
    setError(null)
    setSuccess(null)

    try {
      await submitVisitConfirmation(
        selectedVisit.visitId,
        {
          confirmationStatus:
            response,
          rating,
          comment:
            notes.trim() ||
            null,
        },
      )

      const remaining =
        visits.filter(
          (visit) =>
            visit.visitId !==
            selectedVisit.visitId,
        )

      setVisits(remaining)

      setSelectedVisitId(
        remaining.length > 0
          ? remaining[0].visitId
          : null,
      )

      setResponse('CONFIRMED')
      setRating(null)
      setNotes('')

      setSuccess(
        response === 'CONFIRMED'
          ? 'Thank you. Your confirmation has been recorded.'
          : 'Thank you. Your concern has been recorded and sent for follow-up.',
      )
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        failure.status === 409
      ) {
        setError(
          'This visit has already been confirmed or is no longer awaiting confirmation.',
        )
      } else if (
        failure instanceof ApiError &&
        failure.status === 404
      ) {
        setError(
          'This visit could not be found.',
        )
      } else if (
        failure instanceof ApiError
      ) {
        setError(
          failure.message,
        )
      } else {
        setError(
          'Unable to submit your confirmation.',
        )
      }
    } finally {
      setSubmitting(false)
    }
  }

  const hasVisits =
    !loading && visits.length > 0

  return (
    <ElderShell>
      <ScreenColumns
        left={
          <>
            <Slot order={1}>
              <ScreenHeader
                backTo="/elder"
                title="Was the visit alright?"
              />
            </Slot>

            <Slot order={2}>
              {error && (
                <StatusNote tone="problem">
                  {error}
                </StatusNote>
              )}
            </Slot>

            <Slot order={3}>
              {success && (
                <StatusNote tone="success">
                  {success}
                </StatusNote>
              )}
            </Slot>

            <Slot order={4}>
              {loading ? (
                <p className={styles.lead}>
                  Loading visits…
                </p>
              ) : visits.length === 0 ? (
                <InfoCard>
                  <h2 className={styles.sectionTitle}>
                    Nothing to confirm
                  </h2>

                  <p className={styles.lead}>
                    You have no finished visits
                    waiting for your answer.
                  </p>
                </InfoCard>
              ) : (
                <div className={styles.form}>
                  {visits.length > 1 && (
                    <label className={styles.fieldLabel}>
                      Which visit?
                      <select
                        form={FORM_ID}
                        value={selectedVisitId ?? ''}
                        disabled={submitting}
                        onChange={(event) =>
                          setSelectedVisitId(
                            Number(event.target.value),
                          )
                        }
                      >
                        {visits.map((visit) => (
                          <option
                            key={visit.visitId}
                            value={visit.visitId}
                          >
                            {visit.serviceType ??
                              'Care service'}
                            {' · '}
                            {formatDateTime(
                              visit.scheduledStart,
                            )}
                          </option>
                        ))}
                      </select>
                    </label>
                  )}

                  {selectedVisit && (
                    <InfoCard>
                      <h2 className={styles.meta}>
                        Completed visit
                      </h2>

                      <p className={styles.visitService}>
                        {selectedVisit.serviceType ??
                          'Care service'}
                      </p>

                      <p className={styles.visitTimes}>
                        {formatVisitTimes(selectedVisit)}
                      </p>
                    </InfoCard>
                  )}
                </div>
              )}
            </Slot>

            <Slot order={6}>
              {hasVisits && (
                <InfoNote>
                  Your answer closes the visit. If it
                  was not so good, your care manager
                  will follow up.
                </InfoNote>
              )}
            </Slot>
          </>
        }
        right={
          hasVisits && (
            <Slot order={5}>
              <form
                id={FORM_ID}
                className={styles.form}
                onSubmit={handleSubmit}
              >
                <ActionStack>
                  <ChoiceRadio
                    name="confirmation"
                    icon="✓"
                    label="Yes, good"
                    checked={response === 'CONFIRMED'}
                    disabled={submitting}
                    onChange={() =>
                      setResponse('CONFIRMED')
                    }
                  />

                  <ChoiceRadio
                    name="confirmation"
                    icon="–"
                    label="Not so good"
                    checked={response === 'DISPUTED'}
                    disabled={submitting}
                    onChange={() =>
                      setResponse('DISPUTED')
                    }
                  />
                </ActionStack>

                <fieldset
                  className={styles.fieldset}
                  disabled={submitting}
                >
                  <legend className={styles.legend}>
                    How many stars?{' '}
                    <span className={styles.hint}>
                      (you can skip this)
                    </span>
                  </legend>

                  <div className={styles.ratingRow}>
                    {[1, 2, 3, 4, 5].map((value) => (
                      <ChoiceRadio
                        key={value}
                        compact
                        name="rating"
                        label={String(value)}
                        checked={rating === value}
                        onChange={() =>
                          setRating(value)
                        }
                      />
                    ))}
                  </div>
                </fieldset>

                <label className={styles.fieldLabel}>
                  <span>
                    Anything to tell us?{' '}
                    <span className={styles.hint}>
                      (you can skip this)
                    </span>
                  </span>

                  <textarea
                    value={notes}
                    disabled={submitting}
                    onChange={(event) =>
                      setNotes(event.target.value)
                    }
                    placeholder="Tell us how the visit went"
                  />
                </label>

                <WideButton
                  type="submit"
                  disabled={
                    submitting ||
                    selectedVisit === null
                  }
                >
                  {submitting
                    ? 'Sending…'
                    : 'Send my answer'}
                </WideButton>
              </form>
            </Slot>
          )
        }
        footer={
          <ScreenFooter>
            <SpeakButton />
          </ScreenFooter>
        }
      />
    </ElderShell>
  )
}
