import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getWebhookSettings,
  listWebhookDeliveries,
  resendDelivery,
  rotateWebhookSecret,
  sendTestEvent,
  updateWebhookSettings,
} from '../../../features/provider-platform/api'
import { formatLong, formatShortSeconds } from '../../../features/provider-platform/format'
import { WEBHOOK_EVENTS } from '../../../features/provider-platform/types'
import { ALLOW_HTTP_WEBHOOKS } from '../../../features/provider-platform/validation'
import type { Delivery, DeliveryAttempt, WebhookEvent, WebhookSettings } from '../../../features/provider-platform/types'
import { MAX_ATTEMPTS, retryScheduleLabel } from '../../../features/provider-platform/webhooks'
import { cx } from '../../../shared/components/ui/cx'
import { RevealSecret } from '../components/RevealSecret'
import { Banner, Button, Checkbox, Dialog, Field, SectionLabel, StateTag, TextInput } from '../components/ui'
import { WorkbenchShell } from '../components/WorkbenchShell'
import { useWorkbench } from '../workbenchContext'
import styles from './Webhooks.module.css'

const EVENT_HELP: Record<(typeof WEBHOOK_EVENTS)[number], string> = {
  'job.dispatched': 'CareLink sends you a job',
  'job.cancelled': 'A job sent to you is cancelled',
}

/** "200 · 84 ms", "503 · 40 ms" or "Timed out". */
function responseLabel(attempt: DeliveryAttempt): string {
  return attempt.status === null ? 'Timed out' : `${attempt.status} · ${attempt.ms} ms`
}

/**
 * Webhooks: where CareLink sends this provider's job events, and what became of each one. The
 * endpoint and its events can be changed, the signing secret rotated, and a test event sent.
 * The log is CareLink's side of each delivery — every attempt, the response it got, when it
 * tries next, or that it gave up — and any delivery can be sent again with the same ID.
 */
export default function Webhooks() {
  const { portal } = useWorkbench()
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [testing, setTesting] = useState(false)
  const [resending, setResending] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  const settings = useQuery({
    queryKey: ['provider', 'webhook', portal?.token],
    queryFn: () => getWebhookSettings(portal!),
    enabled: portal !== null,
    retry: false,
  })

  const deliveries = useQuery({
    queryKey: ['provider', 'deliveries', portal?.token],
    queryFn: () => listWebhookDeliveries(portal!),
    enabled: portal !== null,
    refetchInterval: 30_000,
    retry: false,
  })

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['provider', 'deliveries'] }),
      queryClient.invalidateQueries({ queryKey: ['provider', 'health'] }),
    ])

  async function sendTest() {
    setTesting(true)
    setFailure(null)
    try {
      const sent = await sendTestEvent(portal!)
      await refresh()
      setSelectedId(sent.id)
    } catch (err) {
      setFailure(err instanceof Error ? err.message : "Couldn't reach CareLink.")
    } finally {
      setTesting(false)
    }
  }

  async function resend(delivery: Delivery) {
    setResending(true)
    setFailure(null)
    try {
      await resendDelivery(portal!, delivery.id)
      await refresh()
    } catch (err) {
      setFailure(err instanceof Error ? err.message : "Couldn't reach CareLink.")
    } finally {
      setResending(false)
    }
  }

  const rows = deliveries.data ?? []
  const open = rows.find((d) => d.id === selectedId) ?? rows[0]

  return (
    <WorkbenchShell>
      <div className={styles.page}>
        <div className={styles.intro}>
          <h1 className={styles.title}>Webhooks</h1>
          <p className={styles.sub}>
            CareLink sends your job events here, signed with your signing secret. Unanswered or non-2xx deliveries are retried after{' '}
            {retryScheduleLabel()}: {MAX_ATTEMPTS} attempts in all, then the delivery is marked failed.
          </p>
        </div>

        {failure && (
          <Banner tone="danger" title="Couldn't reach CareLink" role="alert">
            {failure}
          </Banner>
        )}

        {settings.isError ? (
          <Banner tone="danger" title="Couldn't load your webhook settings" role="alert">
            {settings.error.message}
          </Banner>
        ) : settings.data ? (
          <Settings settings={settings.data} onTest={() => void sendTest()} testing={testing} />
        ) : (
          <p className={styles.status}>Loading your webhook settings…</p>
        )}

        <section className={styles.log} aria-labelledby="deliveries-heading">
          <h2 id="deliveries-heading" className={styles.subtitle}>
            Deliveries
          </h2>
          {deliveries.isPending ? (
            <p className={styles.status}>Loading deliveries…</p>
          ) : rows.length === 0 ? (
            <p className={styles.status}>Nothing sent yet. Send a test event to check your endpoint.</p>
          ) : (
            <div className={styles.layout}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th scope="col">Delivery</th>
                    <th scope="col">Event</th>
                    <th scope="col">Created</th>
                    <th scope="col">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((d) => {
                    const last = d.attempts[d.attempts.length - 1]
                    return (
                      <tr
                        key={d.id}
                        className={cx(d === open && styles.selected, d.status === 'failed' && styles.failedRow)}
                        aria-selected={d === open}
                        tabIndex={0}
                        onClick={() => setSelectedId(d.id)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault()
                            setSelectedId(d.id)
                          }
                        }}
                      >
                        <td className={styles.delivery}>{d.id}</td>
                        <td>{d.event}</td>
                        <td className={styles.muted}>{formatShortSeconds(d.createdAt)}</td>
                        <td>
                          <div className={styles.statusCell}>
                            <StateTag kind={d.status} />
                            <span className={cx(styles.response, last.status !== null && last.status < 300 && styles.ok)}>
                              {responseLabel(last)}
                              {d.attempts.length > 1 && <span className={styles.tries}> · {d.attempts.length} tries</span>}
                            </span>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>

              {open && <DeliveryPanel delivery={open} resending={resending} onResend={() => void resend(open)} />}
            </div>
          )}
        </section>
      </div>
    </WorkbenchShell>
  )
}

function Settings({ settings, onTest, testing }: { settings: WebhookSettings; onTest: () => void; testing: boolean }) {
  const { portal } = useWorkbench()
  const queryClient = useQueryClient()
  const [url, setUrl] = useState(settings.url)
  const [events, setEvents] = useState<WebhookEvent[]>(settings.events)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const [rotating, setRotating] = useState(false)
  const [rotated, setRotated] = useState<string | null>(null)

  const changed = url.trim() !== settings.url || events.join() !== settings.events.join()

  async function save(e: FormEvent) {
    e.preventDefault()
    if (ALLOW_HTTP_WEBHOOKS ? !/^https?:\/\/[^/\s]+/i.test(url.trim()) : !/^https:\/\/[^/\s]+\.[^/\s]+/i.test(url.trim())) {
      setError('Must be a full HTTPS URL, for example https://hooks.example.sg/carelink.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await updateWebhookSettings(portal!, { url, events })
      setSaved(true)
      await queryClient.invalidateQueries({ queryKey: ['provider', 'webhook'] })
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't reach CareLink.")
    } finally {
      setSaving(false)
    }
  }

  async function rotate() {
    setRotating(true)
    try {
      const { webhookSecret } = await rotateWebhookSecret(portal!)
      setRotated(webhookSecret)
      await queryClient.invalidateQueries({ queryKey: ['provider', 'webhook'] })
    } catch (err) {
      setError(err instanceof Error ? err.message : "Couldn't reach CareLink.")
    } finally {
      setRotating(false)
      setConfirming(false)
    }
  }

  return (
    <section className={styles.card} aria-labelledby="endpoint-heading">
      <div className={styles.cardHead}>
        <SectionLabel id="endpoint-heading">Endpoint</SectionLabel>
        <Button size="small" busy={testing && 'Sending…'} onClick={onTest}>
          Send test event
        </Button>
      </div>
      <form className={styles.cardBody} onSubmit={save}>
        <Field
          label="Endpoint URL"
          htmlFor="webhook-url"
          helper="Must be HTTPS. Answer with any 2xx within 10 seconds."
          error={error ?? undefined}
          errorId="webhook-url-error"
        >
          <TextInput
            id="webhook-url"
            mono
            type="url"
            value={url}
            onChange={(e) => {
              setUrl(e.target.value)
              setSaved(false)
            }}
            invalid={Boolean(error)}
            aria-describedby={error ? 'webhook-url-error' : undefined}
          />
        </Field>
        <fieldset className={styles.events}>
          <legend className={styles.legend}>Events</legend>
          {WEBHOOK_EVENTS.map((event) => (
            <Checkbox
              key={event}
              checked={events.includes(event)}
              onChange={(on) => {
                setEvents(on ? WEBHOOK_EVENTS.filter((e) => e === event || events.includes(e)) : events.filter((e) => e !== event))
                setSaved(false)
              }}
            >
              <span className={styles.eventName}>{event}</span> <span className={styles.eventHelp}>{EVENT_HELP[event]}</span>
            </Checkbox>
          ))}
        </fieldset>
        <div className={styles.saveRow}>
          <Button type="submit" variant="primary" disabled={!changed} busy={saving && 'Saving…'}>
            Save changes
          </Button>
          {saved && !changed && (
            <span className={styles.saved} role="status">
              Saved
            </span>
          )}
        </div>

        <div className={styles.secretRow}>
          <div className={styles.secretText}>
            <span className={styles.secretLabel}>Signing secret</span>
            <span className={styles.secretValue}>whsec_••••••••••••{settings.secretHint}</span>
            <span className={styles.secretMeta}>
              Issued {formatLong(settings.secretIssuedAt)}. Verify X-CareLink-Signature (HMAC-SHA256 over timestamp.body) with it.
            </span>
          </div>
          <Button onClick={() => setConfirming(true)} disabled={rotated !== null}>
            Rotate secret
          </Button>
        </div>
        {rotated && (
          <RevealSecret
            label="Signing secret"
            value={rotated}
            helper="Use this to verify the X-CareLink-Signature header (HMAC-SHA256) on every webhook."
            onDone={() => setRotated(null)}
          />
        )}
      </form>

      {confirming && (
        <Dialog
          title="Rotate signing secret?"
          onClose={() => setConfirming(false)}
          footer={
            <>
              <Button onClick={() => setConfirming(false)} disabled={rotating}>
                Cancel
              </Button>
              <Button variant="danger" busy={rotating && 'Rotating…'} onClick={() => void rotate()}>
                Rotate secret
              </Button>
            </>
          }
        >
          <p className={styles.dialogText}>
            Webhooks from now on are signed with the new secret. Until your server uses it, it will reject them and CareLink will retry.
          </p>
        </Dialog>
      )}
    </section>
  )
}

function DeliveryPanel({ delivery, resending, onResend }: { delivery: Delivery; resending: boolean; onResend: () => void }) {
  return (
    <section className={styles.panel} aria-label={`Delivery ${delivery.id}`}>
      <div className={styles.panelHead}>
        <div className={styles.panelTitle}>
          <span className={styles.panelId}>{delivery.id}</span>
          <StateTag kind={delivery.status} />
        </div>
        <Button size="small" busy={resending && 'Resending…'} onClick={onResend}>
          Resend
        </Button>
      </div>
      <div className={styles.panelSection}>
        <SectionLabel>Attempts</SectionLabel>
        <ol className={styles.attempts}>
          {delivery.attempts.map((attempt, i) => (
            <li key={attempt.at + i} className={attempt.status !== null && attempt.status < 300 ? styles.attemptOk : styles.attemptBad}>
              <span className={styles.attemptNo}>#{i + 1}</span>
              <span>{formatShortSeconds(attempt.at)}</span>
              <span className={styles.attemptResult}>{responseLabel(attempt)}</span>
            </li>
          ))}
        </ol>
        {delivery.status === 'retrying' && delivery.nextRetryAt && (
          <p className={styles.panelNote}>
            Next attempt {formatShortSeconds(delivery.nextRetryAt)} ({delivery.attempts.length + 1} of {MAX_ATTEMPTS}).
          </p>
        )}
        {delivery.status === 'failed' && (
          <p className={styles.panelNote}>CareLink gave up after {MAX_ATTEMPTS} attempts. Fix your endpoint, then Resend.</p>
        )}
      </div>
      <div className={cx(styles.panelSection, styles.ruled)}>
        <SectionLabel>Request</SectionLabel>
      </div>
      <pre className={styles.headers}>{delivery.headers}</pre>
      <pre className={styles.payload}>{delivery.payload}</pre>
    </section>
  )
}
