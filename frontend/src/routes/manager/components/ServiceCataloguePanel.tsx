import { useState } from 'react'
import {
  Button,
  Callout,
  ConfirmDialog,
  Eyebrow,
  Field,
  MetaText,
  NumberInput,
  TextArea,
  TextInput,
} from '../../../shared/components/ui'
import type {
  CatalogueService,
  ValueAddedService,
  ValueAddedServiceCreate,
  ValueAddedServiceUpdate,
} from '../../../features/value-added-services/types'
import { formatDuration, validateService } from '../lib/serviceCatalogue'
import type { ServiceForm, ServiceFormErrors } from '../lib/serviceCatalogue'
import styles from './ServiceCataloguePanel.module.css'

/**
 * The right-hand panel of the Extra services catalogue (D21). Without a `service` it adds one: a name, what it
 * is, and how long its visit lasts; it is available at once. With one it changes what it says and how long it
 * lasts, withdraws it or makes it available again, and removes it if nobody ever asked for it. The name never
 * changes, since providers offer services by name. Each callback does the request and rejects with the error to
 * show. Key it by service id.
 */
export function ServiceCataloguePanel({
  service,
  catalogue,
  onAdd,
  onSave,
  onRemove,
  onCancel,
}: {
  service: CatalogueService | null
  catalogue: ValueAddedService[]
  onAdd: (create: ValueAddedServiceCreate) => Promise<void>
  onSave: (id: number, update: ValueAddedServiceUpdate) => Promise<void>
  onRemove: (id: number) => Promise<void>
  onCancel: () => void
}) {
  const [form, setForm] = useState<ServiceForm>({
    name: service?.name ?? '',
    description: service?.description ?? '',
    durationMinutes: service?.durationMinutes ?? 60,
  })
  const [attempted, setAttempted] = useState(false)
  const [busy, setBusy] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)
  const [confirmingRemove, setConfirmingRemove] = useState(false)

  const adding = service === null
  const errors: ServiceFormErrors = attempted ? validateService(form, adding ? catalogue : null) : {}
  const changed =
    adding || form.description.trim() !== (service.description ?? '') || form.durationMinutes !== service.durationMinutes

  async function run(action: () => Promise<void>, fallback: string) {
    setBusy(true)
    setFailure(null)
    try {
      await action()
    } catch (err) {
      setFailure(err instanceof Error ? err.message : fallback)
    } finally {
      setBusy(false)
      setConfirmingRemove(false)
    }
  }

  function submit() {
    setAttempted(true)
    if (Object.keys(validateService(form, adding ? catalogue : null)).length > 0) return
    const description = form.description.trim() || null
    const durationMinutes = form.durationMinutes!
    if (adding) {
      void run(() => onAdd({ name: form.name.trim(), description, durationMinutes }), 'Could not add this service.')
    } else {
      void run(() => onSave(service.id, { description, durationMinutes, status: service.status }), 'Could not save this service.')
    }
  }

  function setAvailability(status: ValueAddedService['status']) {
    if (adding) return
    // Withdrawing or offering again keeps what was saved, not what is typed and unsaved.
    void run(
      () => onSave(service.id, { description: service.description, durationMinutes: service.durationMinutes, status }),
      'Could not change this service.',
    )
  }

  return (
    <aside className={styles.panel} aria-label={adding ? 'New extra service' : 'Extra service in the catalogue'}>
      <header className={styles.header}>
        <Eyebrow wide>{adding ? 'Catalogue · new service' : `Catalogue · #${service.id}`}</Eyebrow>
        <h2 className={styles.title}>{adding ? 'New extra service' : service.name}</h2>
        {!adding && (
          <MetaText className={styles.meta}>
            {service.status === 'AVAILABLE' ? 'Available' : 'Unavailable'} · {formatDuration(service.durationMinutes)}
          </MetaText>
        )}
      </header>

      <form
        className={styles.section}
        noValidate
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
      >
        {adding ? (
          <Field label="Name">
            {(id) => (
              <>
                <TextInput
                  id={id}
                  value={form.name}
                  onChange={(name) => setForm((f) => ({ ...f, name }))}
                  aria-invalid={errors.name ? true : undefined}
                  aria-describedby={`${id}-help`}
                  autoFocus
                />
                {errors.name ? (
                  <MetaText id={`${id}-help`} tone="danger">
                    {errors.name}
                  </MetaText>
                ) : (
                  <MetaText id={`${id}-help`} tone="faint">
                    Providers sign up for a service by its name, so it can't be changed later.
                  </MetaText>
                )}
              </>
            )}
          </Field>
        ) : (
          <MetaText tone="faint">The name can't be changed: providers sign up for a service by its name.</MetaText>
        )}

        <Field label="Description">
          {(id) => (
            <>
              <TextArea
                id={id}
                rows={3}
                value={form.description}
                onChange={(description) => setForm((f) => ({ ...f, description }))}
                invalid={Boolean(errors.description)}
                placeholder="What the caregiver or provider does, as elders and families will read it."
              />
              {errors.description && <MetaText tone="danger">{errors.description}</MetaText>}
            </>
          )}
        </Field>

        <Field label="Duration">
          {(id) => (
            <>
              <NumberInput
                id={id}
                value={form.durationMinutes}
                onChange={(durationMinutes) => setForm((f) => ({ ...f, durationMinutes }))}
                suffix="min"
                aria-invalid={errors.durationMinutes ? true : undefined}
              />
              {errors.durationMinutes ? (
                <MetaText tone="danger">{errors.durationMinutes}</MetaText>
              ) : (
                <MetaText tone="faint">
                  How long the visit lasts. It counts towards caregiver clashes and daily hours.
                  {!adding && ' A change applies to visits booked from now on.'}
                </MetaText>
              )}
            </>
          )}
        </Field>

        {failure && (
          <Callout tone="danger" role="alert">
            {failure}
          </Callout>
        )}

        <div className={styles.actions}>
          <Button type="submit" variant="primary" disabled={busy || !changed}>
            {adding ? (busy ? 'Adding…' : 'Add service') : busy ? 'Saving…' : 'Save changes'}
          </Button>
          {adding && (
            <Button variant="ghost" onClick={onCancel} disabled={busy}>
              Cancel
            </Button>
          )}
        </div>
      </form>

      {!adding && (
        <section className={styles.section} aria-label="Availability">
          <Eyebrow wide>Availability</Eyebrow>
          {service.status === 'AVAILABLE' ? (
            <>
              <MetaText>
                Making it unavailable hides it from elders, families and providers signing up. Requests already made
                carry on.
              </MetaText>
              <div className={styles.actions}>
                <Button onClick={() => setAvailability('UNAVAILABLE')} disabled={busy}>
                  Mark unavailable
                </Button>
              </div>
            </>
          ) : (
            <>
              <MetaText>Elders and families can't ask for it, and providers can't sign up for it.</MetaText>
              <div className={styles.actions}>
                <Button onClick={() => setAvailability('AVAILABLE')} disabled={busy}>
                  Make available
                </Button>
              </div>
            </>
          )}
        </section>
      )}

      {!adding && (
        <section className={styles.section} aria-label="Remove">
          <Eyebrow wide>Remove</Eyebrow>
          {service.removable ? (
            <>
              <MetaText>Nobody has asked for it yet, so it can be removed from the catalogue for good.</MetaText>
              <div className={styles.actions}>
                <Button variant="dangerOutline" onClick={() => setConfirmingRemove(true)} disabled={busy}>
                  Remove service
                </Button>
              </div>
            </>
          ) : (
            <MetaText tone="faint">
              Asked for {service.requestCount} {service.requestCount === 1 ? 'time' : 'times'}, so it stays in the catalogue
              for those requests. Make it unavailable to stop new ones.
            </MetaText>
          )}
        </section>
      )}

      {confirmingRemove && !adding && (
        <ConfirmDialog
          tone="danger"
          eyebrow="Remove service"
          title={`Remove ${service.name}?`}
          confirmLabel="Remove service"
          cancelLabel="Keep it"
          busy={busy}
          onConfirm={() => void run(() => onRemove(service.id), 'Could not remove this service.')}
          onCancel={() => setConfirmingRemove(false)}
        >
          <MetaText>
            It disappears from what elders, families and providers can choose. A provider that offers it keeps the name
            until it changes its services.
          </MetaText>
        </ConfirmDialog>
      )}
    </aside>
  )
}
