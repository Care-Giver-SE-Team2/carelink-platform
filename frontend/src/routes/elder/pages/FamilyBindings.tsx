import {
  useEffect,
  useId,
  useState,
} from 'react'
import type {
  FormEvent,
} from 'react'

import {
  createFamilyBinding,
  getFamilyBindings,
  revokeFamilyBinding,
} from '../../../features/family-binding/api'
import type {
  AccessScope,
  FamilyBinding,
  Relationship,
} from '../../../features/family-binding/types'
import { ApiError } from '../../../shared/api/client'
import { ElderShell } from '../components/ElderShell'
import {
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

function relationshipLabel(
  relationship: Relationship,
): string {
  switch (relationship) {
    case 'SON':
      return 'Son'
    case 'DAUGHTER':
      return 'Daughter'
    case 'SPOUSE':
      return 'Spouse'
    case 'GUARDIAN':
      return 'Guardian'
    default:
      return 'Other'
  }
}

function statusLabel(
  status: FamilyBinding['status'],
): string {
  switch (status) {
    case 'PENDING_CONFIRMATION':
      return 'Pending confirmation'
    case 'ACTIVE':
      return 'Active'
    case 'REJECTED':
      return 'Rejected'
    case 'REVOKED':
      return 'Removed'
  }
}

export default function FamilyBindings() {
  const [bindings, setBindings] =
    useState<FamilyBinding[]>([])

  const [loading, setLoading] =
    useState(true)

  const [submitting, setSubmitting] =
    useState(false)

  const [revokingId, setRevokingId] =
    useState<number | null>(null)

  const [
    familyUsername,
    setFamilyUsername,
  ] = useState('')

  const [
    relationship,
    setRelationship,
  ] =
    useState<Relationship>('OTHER')

  const [
    accessScope,
    setAccessScope,
  ] =
    useState<AccessScope>('FULL')

  const [
    primaryContact,
    setPrimaryContact,
  ] = useState(false)

  const [error, setError] =
    useState<string | null>(null)

  const [success, setSuccess] =
    useState<string | null>(null)

  const [usernameError, setUsernameError] =
    useState<string | null>(null)

  const id = useId()

  useEffect(() => {
    const controller =
      new AbortController()

    getFamilyBindings()
      .then((result) => {
        setBindings(result)
      })
      .catch((failure: unknown) => {
        if (
          failure instanceof DOMException &&
          failure.name === 'AbortError'
        ) {
          return
        }

        setError(
          'Unable to load family members.',
        )
      })
      .finally(() => {
        if (
          !controller.signal.aborted
        ) {
          setLoading(false)
        }
      })

    return () => {
      controller.abort()
    }
  }, [])

  async function handleCreate(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (submitting) {
      return
    }

    const username =
      familyUsername.trim().toLowerCase()

    if (!username) {
      setUsernameError(
        'Enter the username of their family account.',
      )
      return
    }

    // The same rule family sign-up uses, so a typo is caught before it is looked up.
    if (
      !/^[a-z0-9][a-z0-9._-]{2,63}$/.test(username)
    ) {
      setUsernameError(
        'A username has 3 to 64 letters, digits, dots, dashes or underscores, and no spaces.',
      )
      return
    }

    setSubmitting(true)
    setError(null)
    setSuccess(null)

    try {
      const created =
        await createFamilyBinding({
          familyUsername: username,
          relationship,
          primaryContact,
          accessScope,
        })

      setBindings((current) => [
        created,
        ...current.filter(
          (binding) =>
            binding.id !== created.id,
        ),
      ])

      setFamilyUsername('')
      setRelationship('OTHER')
      setAccessScope('FULL')
      setPrimaryContact(false)

      setSuccess(
        'Family binding request created.',
      )
    } catch (failure) {
      if (
        failure instanceof ApiError &&
        failure.status === 404
      ) {
        setUsernameError(
          'No family member account was found with that username.',
        )
      } else if (
        failure instanceof ApiError &&
        failure.status === 409
      ) {
        setError(
          'This family member is already linked or awaiting confirmation.',
        )
      } else if (
        failure instanceof ApiError
      ) {
        setError(failure.message)
      } else {
        setError(
          'Unable to create the family binding.',
        )
      }
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRevoke(
    bindingId: number,
  ) {
    if (revokingId !== null) {
      return
    }

    setRevokingId(bindingId)
    setError(null)
    setSuccess(null)

    try {
      const updated =
        await revokeFamilyBinding(
          bindingId,
        )

      setBindings((current) =>
        current.map((binding) =>
          binding.id === updated.id
            ? updated
            : binding,
        ),
      )

      setSuccess(
        'Family binding removed.',
      )
    } catch (failure) {
      if (
        failure instanceof ApiError
      ) {
        setError(failure.message)
      } else {
        setError(
          'Unable to remove the family binding.',
        )
      }
    } finally {
      setRevokingId(null)
    }
  }

  const visibleBindings =
    bindings.filter(
      (binding) =>
        binding.status !== 'REVOKED',
    )

  return (
    <ElderShell>
      <ScreenColumns
        left={
          <>
            <Slot order={1}>
              <ScreenHeader
                backTo="/elder"
                eyebrow="Family"
                eyebrowStyle="label"
                title="My family"
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
              <div className={styles.form}>
                {loading ? (
                  <p className={styles.lead}>
                    Loading family members…
                  </p>
                ) : visibleBindings.length === 0 ? (
                  <InfoNote>
                    You do not have any family
                    members linked yet.
                  </InfoNote>
                ) : (
                  visibleBindings.map(
                    (binding) => (
                      <InfoCard key={binding.id}>
                        <h2 className={styles.familyName}>
                          {binding.familyMemberName}
                        </h2>

                        <p className={styles.familyMeta}>
                          {relationshipLabel(
                            binding.relationship,
                          )}
                          {binding.primaryContact
                            ? ' · Primary contact'
                            : ''}
                        </p>

                        <p className={styles.familyRelation}>
                          Access:{' '}
                          {binding.accessScope === 'FULL'
                            ? 'Full'
                            : 'Read only'}
                          {' · '}
                          Status:{' '}
                          {statusLabel(binding.status)}
                        </p>

                        <div className={styles.cardAction}>
                          <WideButton
                            variant="secondary"
                            disabled={revokingId !== null}
                            onClick={() =>
                              handleRevoke(binding.id)
                            }
                          >
                            {revokingId === binding.id
                              ? 'Removing…'
                              : 'Remove family member'}
                          </WideButton>
                        </div>
                      </InfoCard>
                    ),
                  )
                )}
              </div>
            </Slot>

            <Slot order={6}>
              <p className={styles.meta}>
                You can stop this at any time. A care
                manager can help you.
              </p>
            </Slot>
          </>
        }
        right={
          <Slot order={5}>
            <InfoCard>
              <form
                className={styles.form}
                onSubmit={handleCreate}
              >
                <h2 className={styles.sectionTitle}>
                  Bind another family member
                </h2>

                <p className={styles.lead}>
                  Enter the username of their
                  CareLink family account.
                </p>

                <div className={styles.fieldLabel}>
                  <label htmlFor={`${id}-username`}>
                    Family username
                  </label>

                  <input
                    id={`${id}-username`}
                    type="text"
                    value={familyUsername}
                    autoCapitalize="none"
                    spellCheck={false}
                    maxLength={64}
                    disabled={submitting}
                    aria-invalid={!!usernameError}
                    aria-describedby={
                      usernameError
                        ? `${id}-username-error`
                        : undefined
                    }
                    onChange={(event) => {
                      setFamilyUsername(
                        event.target.value,
                      )
                      setUsernameError(null)
                    }}
                  />

                  {usernameError && (
                    <span
                      id={`${id}-username-error`}
                      className={styles.fieldError}
                    >
                      {usernameError}
                    </span>
                  )}
                </div>

                <label className={styles.fieldLabel}>
                  <span>Relationship</span>

                  <select
                    value={relationship}
                    disabled={submitting}
                    onChange={(event) =>
                      setRelationship(
                        event.target
                          .value as Relationship,
                      )
                    }
                  >
                    <option value="SON">Son</option>
                    <option value="DAUGHTER">Daughter</option>
                    <option value="SPOUSE">Spouse</option>
                    <option value="GUARDIAN">Guardian</option>
                    <option value="OTHER">Other</option>
                  </select>
                </label>

                <label className={styles.fieldLabel}>
                  <span>Access</span>

                  <select
                    value={accessScope}
                    disabled={submitting}
                    onChange={(event) =>
                      setAccessScope(
                        event.target
                          .value as AccessScope,
                      )
                    }
                  >
                    <option value="FULL">Full</option>
                    <option value="READ_ONLY">Read only</option>
                  </select>
                </label>

                <label className={styles.checkboxField}>
                  <input
                    type="checkbox"
                    checked={primaryContact}
                    disabled={submitting}
                    onChange={(event) =>
                      setPrimaryContact(
                        event.target.checked,
                      )
                    }
                  />

                  <span>Primary contact</span>
                </label>

                <WideButton
                  type="submit"
                  disabled={
                    submitting ||
                    !familyUsername.trim()
                  }
                >
                  {submitting
                    ? 'Sending request…'
                    : 'Send binding request'}
                </WideButton>
              </form>
            </InfoCard>
          </Slot>
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
