# CareLink Open Platform webhooks

How CareLink tells a provider's system that something happened to its jobs. The job API itself, and the token it
needs, are in [open-platform.yaml](open-platform.yaml).

Status: version 0.1, built (see [decisions](../section5/decisions.md), D1, D2 and D16). Owner: Section 5
(notification).

## 1. In short

- CareLink sends an HTTPS `POST` with a JSON body to the webhook URL the provider registered.
- Every request is signed with HMAC-SHA256 using the provider's signing secret. Verify it before trusting the body.
- Answer with any `2xx` within 10 seconds. Anything else is retried, up to 6 attempts over about 8.6 hours.
- The same delivery can arrive more than once. Deduplicate by its delivery ID.
- A webhook is a signal, not the record: read the job through the API for its current state and details.

## 2. Events

| Event | Sent when | What to do |
|---|---|---|
| `job.dispatched` | CareLink offers the provider a new job | Read it (`GET /api/open/v1/jobs/{jobId}`), then accept or decline it |
| `job.cancelled` | A job dispatched to the provider is called off, whether it was accepted or not: the family cancelled, CareLink staffed it first, or its `respondBy` passed without an answer | Stop any work on it. Nothing to answer |
| `ping` | The provider presses **Send test event** in the portal | Answer `2xx`. Always sent, whatever the subscription |

A provider chooses which of `job.dispatched` and `job.cancelled` it receives (portal, Webhooks). CareLink does not
send an event for the provider's own actions: accepting, declining or completing a job.

Adding an event type, or a field to a body, is compatible: receivers must ignore types and fields they do not know.
Removing or renaming a field, or changing what it means, gets a new event name.

## 3. The request

```http
POST /carelink HTTP/1.1
Host: hooks.example.sg
Content-Type: application/json
User-Agent: CareLink-Webhooks/1
X-CareLink-Delivery: dlv_8Fq2kZ71
X-CareLink-Event: job.dispatched
X-CareLink-Timestamp: 1791864000
X-CareLink-Signature: v1=5c3e…

{ …body… }
```

| Header | What |
|---|---|
| `X-CareLink-Delivery` | The delivery ID. The same on every retry and resend of this delivery |
| `X-CareLink-Event` | The event type, as in the body |
| `X-CareLink-Timestamp` | When this attempt was signed: Unix time in seconds. New on every attempt |
| `X-CareLink-Signature` | `v1=` and the lowercase hex signature (section 5). Two values, comma-separated, for a day after the secret is rotated |

- The URL must be HTTPS with a certificate a public CA issued. CareLink does not follow redirects: a `3xx` is a
  failed attempt.
- The body is UTF-8 JSON, at most 64 KB.

## 4. The body

Every event has the same envelope:

| Field | Type | What |
|---|---|---|
| `id` | string | The delivery ID, as in `X-CareLink-Delivery` |
| `type` | string | The event type |
| `createdAt` | string | When CareLink created the delivery, Singapore time with its offset. The same on every attempt |
| `data` | object | The event's fields. Empty for `ping` |

Times follow the job API: Singapore wall-clock time with its offset, to the second.

### `job.dispatched`

```json
{
  "id": "dlv_8Fq2kZ71",
  "type": "job.dispatched",
  "createdAt": "2026-10-15T09:02:11+08:00",
  "data": {
    "job": {
      "id": "JOB-24081",
      "version": 0,
      "status": "NEW",
      "serviceName": "Hospital escort",
      "district": "19",
      "areaName": "Hougang",
      "scheduledStart": "2026-10-17T10:00:00+08:00",
      "durationMinutes": 180,
      "respondBy": "2026-10-16T10:00:00+08:00",
      "receivedAt": "2026-10-15T09:02:11+08:00",
      "cancelReason": null
    }
  }
}
```

### `job.cancelled`

```json
{
  "id": "dlv_Q0n4tLs9",
  "type": "job.cancelled",
  "createdAt": "2026-10-16T14:30:00+08:00",
  "data": {
    "job": {
      "id": "JOB-24081",
      "version": 2,
      "status": "CANCELLED",
      "serviceName": "Hospital escort",
      "district": "19",
      "areaName": "Hougang",
      "scheduledStart": "2026-10-17T10:00:00+08:00",
      "durationMinutes": 180,
      "respondBy": null,
      "receivedAt": "2026-10-15T09:02:11+08:00",
      "cancelReason": "The family cancelled the booking."
    }
  }
}
```

`data.job` is the job's summary as the job API's `JobSummary` has it, at the change the event reports. It never
carries the elder's name, address or care details; those come from `GET /api/open/v1/jobs/{jobId}`, and the address
only once the provider has accepted.

### `ping`

```json
{ "id": "dlv_0000test", "type": "ping", "createdAt": "2026-10-13T12:00:00+08:00", "data": {} }
```

## 5. Verifying the signature

The signature is HMAC-SHA256, keyed with the provider's webhook signing secret (`whsec_…`, shown once in the
portal), over the bytes:

```text
{X-CareLink-Timestamp} + "." + {the raw request body}
```

To verify a request:

1. Read the raw body bytes, before any JSON parsing: the signature is over the exact bytes CareLink sent.
2. Refuse the request if `X-CareLink-Timestamp` is more than **300 seconds** from your clock, either way. This
   stops a captured request being replayed later.
3. Compute `hex(HMAC_SHA256(secret, timestamp + "." + body))`.
4. Accept if it equals **any** `v1=` value in `X-CareLink-Signature`, compared in constant time.
5. Otherwise answer `401` and do nothing.

```js
// Node, Express: keep the raw body
app.post('/carelink', express.raw({ type: 'application/json' }), (req, res) => {
  const timestamp = req.get('X-CareLink-Timestamp')
  if (Math.abs(Date.now() / 1000 - Number(timestamp)) > 300) return res.sendStatus(401)
  const expected = crypto.createHmac('sha256', process.env.CARELINK_WEBHOOK_SECRET)
    .update(`${timestamp}.`).update(req.body).digest('hex')
  const valid = (req.get('X-CareLink-Signature') ?? '').split(',')
    .map((part) => part.trim().replace(/^v1=/, ''))
    .some((sig) => sig.length === expected.length && crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected)))
  if (!valid) return res.sendStatus(401)
  res.sendStatus(204)                       // answer first …
  queue.add(JSON.parse(req.body))           // … then work, deduplicating by the delivery ID
})
```

**Test vector.** With the secret `whsec_example_do_not_use`, the timestamp `1791864000` (2026-10-13 12:00 Singapore
time) and the body below, exactly as written, on one line and with no trailing newline:

```text
{"id":"dlv_0000test","type":"ping","createdAt":"2026-10-13T12:00:00+08:00","data":{}}
```

the header is:

```text
X-CareLink-Signature: v1=929d8bf95a92a264f468f1d6f0be8e2b88f07be25e4610b319fdb3e9987be1f8
```

**Rotating the secret.** Rotating in the portal issues a new secret at once. For the next **24 hours** CareLink
signs with both, new first (`v1=<new>,v1=<old>`), so the receiver can switch over without dropping a webhook.
After that, only the new one.

## 6. Answering

- Answer any `2xx` to say the delivery arrived. The body of the answer is ignored.
- Answer within **10 seconds**, or the attempt counts as unanswered. Do the work after answering.
- Every other answer is a failed attempt and is retried: `3xx`, `4xx`, `5xx`, a timeout, a refused connection or a
  TLS error. Answer `401` to a bad signature; it is retried like any other failure.

## 7. Retries

A delivery is tried at once, then retried after each failure, waiting longer each time:

| Attempt | Delay after the previous attempt | Time since the first attempt |
|---|---|---|
| 1 | | 0 |
| 2 | 1 minute | 1 min |
| 3 | 5 minutes | 6 min |
| 4 | 30 minutes | 36 min |
| 5 | 2 hours | 2 h 36 min |
| 6 | 6 hours | 8 h 36 min |

- A delivery is **delivered** once an attempt gets a `2xx`; **retrying** while another attempt is scheduled; and
  **failed** when the sixth attempt fails. CareLink then gives up on it.
- The portal's delivery log shows every delivery, its attempts and their answers, for 30 days. **Resend** sends a
  failed delivery again, as one more attempt with the same delivery ID and body, signed afresh.
- Suspending a provider stops its deliveries: none is created, and those still retrying are failed.

## 8. Duplicates and order

- **Receivers must be idempotent.** Delivery is at least once: an attempt can arrive although CareLink did not hear
  the answer, and a resend repeats a delivery. Keep the delivery IDs you have handled, for at least 30 days, and
  ignore a repeat.
- **Order is not guaranteed.** A retried `job.dispatched` can arrive after the same job's `job.cancelled`. Each job
  carries a `version` that increases with every change to the job: ignore an event whose `version` is lower than
  one you already hold for that job, or read the job through the API, whose answer is always current.

## 9. CareLink's side (implementation notes)

Not part of the contract partners rely on. Built in notification's `openplatform` module
(`services/notification/src/main/java/sg/nus/carelink/openplatform`).

- **Tables** (notification's V2): `provider_webhook_endpoint` (URL, events, signing-secret references, enabled),
  `webhook_delivery` (one row per delivery, with its URL and body as created, so every attempt repeats the same
  bytes) and `webhook_delivery_attempt` (every attempt and its answer).
- **Creating a delivery** happens in the transaction of its cause, with a `dedupe_key` naming that cause (unique),
  so the same cause handled twice makes one delivery (`WebhookDeliveryService.enqueue`). Nothing is created for an
  event the provider did not subscribe to, or while it is suspended.
- **Attempts** are made by a scheduled job every 10 seconds (`carelink.webhooks.poll-interval`), on one replica at a
  time (ShedLock `notification.webhook-deliveries`), at most 20 a run. Each request runs outside any database
  transaction; its outcome and the delivery's next state are written together, only if the delivery is still as it
  was read. Each attempt is signed afresh with the time it is made.
- **Sending** uses the JDK's HTTP client: 5-second connect and 10-second request timeout, no redirects, and the
  answer's body never read. In the cloud it sends only HTTPS to public addresses: a provider's URL that resolves
  to a loopback, private, link-local (including the cloud metadata service) or reserved address is refused, so a
  provider cannot make CareLink call inside its own network. The cluster's egress rules are the second line.
  `carelink.webhooks.allow-local-endpoints` lifts this for a local run and the tests only.
- **Signing secrets** are kept encrypted in `provider_webhook_endpoint` (AES-256-GCM), never as themselves. The key
  is the service's setting `carelink.webhooks.encryption-key`: in `.env` locally (`scripts/run.sh` writes it) and in
  the service's Kubernetes Secret in the cloud, never in the database or git. A changed or foreign value is refused.
- **Still to build, with onboarding (Phase 4):** turning report's `ProviderJobUpdated` into `job.dispatched` and
  `job.cancelled` (status `NEW` and `CANCELLED`; the other statuses send nothing), the portal's delivery log, resend
  and test-event endpoints over the service methods that exist, suspension, and removing deliveries after 30 days.
- The constants (retry delays, 6 attempts, 10-second timeout, 300-second tolerance, 24-hour overlap) are the ones
  the portal's Docs page and mock already show (`frontend/src/features/provider-platform/webhooks.ts`).
