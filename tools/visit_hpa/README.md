# Visit HPA business workload

**D1–D5 local preparation update (2026-10-19).** The joint A template is now the candidate 5 RPS / 2700 business GETs per arm. See the [read budget and evidence contract](../../docs/testing/visit-hpa-read-contract.md). D3/D4 source lock and fixture instructions are now available; real local fixture validation is blocked by the Docker engine, and D6/operator review/cloud conditions remain open; this branch is not cloud-test approval.

Python **3.10+**, standard library only. No package install, k6, Maven, Docker or cluster credentials are required on the load generator. Windows, Linux and macOS use the same `load.py`. Docker and production JARs are needed **only** for the optional disposable local smoke test.

Start with [the handoff and joint confirmation sheet](../../docs/testing/visit-hpa-handoff.md). Application baseline: `09d6a3f` (main `59a91a3` + caregiver migration + Redis fix); record the later tool delivery commit separately. No application/deployment configuration is changed by this package.

## Offline first

From the repository root:

```text
python -m unittest discover -s tools/visit_hpa/tests -v
python tools/visit_hpa/load.py --config tools/visit_hpa/example.local.json
python tools/visit_hpa/load.py --config tools/visit_hpa/example.joint.json
```

Without `--execute` there is **no network access**, not even login or DNS lookup. Example IDs, actors and targets are placeholders, not prepared accounts or approved cloud parameters. Copy a template to a private directory and fill in real synthetic fixtures. Do not commit the filled file.

## Data and credentials

Each actor has a private fixture list: numeric `userId`, `visitId`, `elderId`, `expectedStatus`, `expectedVersion`. The actor must be the assigned caregiver. Supply its username/password in the environment variables named by `usernameEnv`/`passwordEnv`; the tool never loads browser Cookies or sends credentials to another host. Use a secure prompt or secret manager; keep passwords out of command arguments, source control and chat. Every VU logs in independently, using a genuine CSRF token and shared-session Cookie. No logout or account switch occurs. Optional expectedTaskCount freezes the task count of a read fixture; an empty SCHEDULED pack is valid but does not establish representative data.

`coreBase` and `visitBase` must use the **same hostname and scheme** (different local ports are allowed). Remote use requires a single HTTPS origin, exact `approval.allowedOrigins`, `confirmed: true`, owner, environment, agreed window and deployment reference. The check is an operator-filled guard, **not verification of human approval or the deployed image**. Zhili must independently confirm both. Do not set it merely to bypass a refusal. No supplied file authorizes remote execution; this delivery was tested only locally.

Run only after the separate environment/window approval:

```text
python tools/visit_hpa/load.py --config /private/run-config.json --output /private/results/run-A0 --execute
```

PowerShell accepts Windows paths for both arguments. Output must be a new directory. `runId` must be unique. `approval.deploymentRef` records the **actual deployment**, and `codeBaseline` records source; the script does not deploy or verify `/actuator/info` automatically.

## Candidate A profile and offline comparison

example.joint.json preserves readProfile=visit-hpa-A-waf-v1: 1/2/4/5 RPS for 180/120/120/120 seconds, hold 5 RPS for 240 seconds, then 480 seconds at zero. That is 21 minutes and 2700 business GETs; 8 VUs add 40 setup HTTP requests, so 2740 planned requests per arm before preparation/smoke/manual/background traffic.

The dated WAF model is 2000 requests / 300 seconds / source IP. Nominal business peak=1500, headroom after setup=460. A request-deadline/launch-lag/20ms-poll shift model gives 1552 business + 40 setup =1592, leaving 408 before other traffic. This is not a hard traffic bound or WAF allowance. Keep otherRequestsPerWindow null and sourceIpReviewed false until Zhili fills the private budget; remote execution of this profile rejects unreviewed inputs before DNS/login. No extra probe, retry or WAF bypass. The profile enforces peak<=5, VUs<=8, cap<=2700 and sample floor>=100; legacy configs without the profile do not inherit this model and are not the formal A handoff.

Use compare_read.py only on saved local result files, with compare.context.example.json copied and completed privately. It checks versions, fixture/workload fingerprints, raw counts, sample coverage, cleanup and declared same-condition references. Known differences are NOT_COMPARABLE; incomplete rows/samples are INSUFFICIENT_CLIENT_EVIDENCE; missing resource declarations are AWAITING_RESOURCE_REVIEW. Even CLIENT_AND_DECLARED_CONDITIONS_MATCH/exit0 is only a consistency check: HPA and performance benefit remain NOT_EVALUATED, formalAcceptance=false. Full commands/conditions are in the contract linked above.

## Scenarios

* `work-pack`: one `GET /api/visits/{id}/work-pack` per iteration; assert visit, elder, state, version, tasks and execution envelope. Fixed synthetic visits can be reused. This endpoint writes access audit records even though the HTTP method is GET.
* `check-in`: GET work-pack → POST check-in → GET saved work-pack. Every arrival needs a **different SCHEDULED, assigned, eligible visit**, a valid published service instruction, an open check-in window and its true current version. Check the server's `allowedActions` includes `CHECK_IN`: execution `tasks` can legitimately be empty before check-in materializes them. Expect `replayed=false`, `savedState=IN_PROGRESS`, `visitVersion=old+1`, and verify saved state via HTTP. It is approximately **three business HTTP requests per successful iteration**, not one. Do not reuse visits or count idempotent replays as new throughput. A timed-out write is ambiguous; it is **not retried**.

Phases specify **business iteration arrivals per second**, duration, and unique names. `maxVUs` is the upper bound on simultaneous iteration clients. More VUs than actors creates isolated sessions for the same synthetic actor; distribute actor fixtures/rates intentionally. With multiple actors, round-robin allocation and per-actor lanes may drop one actor's arrival even if another actor has an idle session. These are generator drops, not a claim about server capacity.

The open-loop scheduler never queues arrivals behind busy VUs. A busy lane or launch delay above `maxLaunchLagMs` is counted as `droppedIterations`. There is no retry/catch-up burst. Write-pool sizing counts **all planned arrivals**, including drops; dropped slots are not recycled. The fixture pool must cover the entire configured run before any request is sent. Recreate an independent pool for a second write arm. The read template must not be changed to `check-in` without enough new visits.

## Results, stop and interpretation

* `manifest.json`: origins, code/deployment references, exact phases, bounds, thresholds, config/tool hashes. No passwords, actor names, fixture IDs, Cookies or response bodies are recorded.
* `requests.jsonl`: per HTTP operation, UTC start, method, endpoint template, elapsed milliseconds, status, HTTP/business checks. `setup` is separate from workload.
* `iterations.jsonl`: per business iteration, UTC, launch delay, latency, pass/error category. No visit IDs or bodies.
* `summary.json` / `summary.csv`: per phase/endpoint, all-response and successful-response p95, actual counts and failure rate; iteration all/success p95/error rate, launch rate/delay, drops and unscheduled arrivals are separate. HTTP/iteration errorCategories, cancelledRequests and requestDeadlineFailures explain subsets of failures without double counting; CSV errorCategories is a JSON string. `phases.csv` uses `timestamp,phase` and timezone-aware ISO8601, compatible with Zhili's scaling report markers. Generation is local; no uploads.

`httpPassed` means HTTP 200; `businessPassed` additionally requires JSON and the endpoint assertions. A JSON 200 with wrong visit/state is a failure. Setup is excluded from measured load. The GET summary in a write scenario includes before/after reads. Percentiles use nearest-rank, client-observed elapsed time; they include transport/HTTP checks and exclude scheduler wait. Success-only p95 never hides the all-response p95 and failure counts. `businessFailures` includes HTTP failures; do not add the two failure counters together. `actualLaunchRps` uses the measured emission interval, not just completed throughput. Some requests can finish during the next phase/drain; assignment stays with the originating iteration. Deadline/forced-drain attempts are failures. A partial HTTP 200 may have httpPassed=true and businessPassed=false. wireStartObserved only means the worker's start message was observed; its absence does not prove the server received nothing.

Under 100 observations/endpoint/phase the label is `INSUFFICIENT_SAMPLES`; zero-RPS cooldown is `NOT_APPLICABLE`. clientEvidenceStatus separately records complete client samples or missing load/samples. Targets stay draft even when execution approval is true. Even `COMPLETED` / `WITHIN_DRAFT_TARGET` is **not HPA, formal SLA, cache, or complete 6b acceptance**. Check generator CPU/network, drops, missing samples and test validity, then combine with Zhili's resource/replica observations. A standalone threaded urllib tool should be replaced or calibrated against a larger generator if its own overhead becomes material.

Exit 0: completed with no failed or dropped iterations and complete worker cleanup. Exit 2: rejected config, stopped, failures, drops or incomplete cleanup. Inspect summary for the exact category; a successful tiny pilot still lacks a capacity conclusion.

Stop immediately on wrong identity/visit/state, replayed first check-in, 401/403, redirects or malformed/non-JSON contracts. Unexpected 5xx/transport failures use the configured consecutive **non-overlapping** error windows; latency uses separate windows on completed business iterations. A low-sample/empty window is not evidence of health and resets a streak. Setup failures stop before load. Record a monitoring failure or stuck requests separately; a low request rate may never fill a statistical window, so operators must watch in-flight timeouts and cluster health.

To stop: Ctrl+C, or create an empty file named `STOP` in that run's output directory. This closes new HTTP admission when observed. An already-admitted request can still reach or commit at the server; this is not server cancellation. Zhili stops the workload on Ready/Pending/OOM/database conditions; this client cannot observe those automatically. The tool does **not** scale, patch, roll back or delete server data.

## Request deadlines and draining (D1)

* socketTimeoutSeconds (examples: 8): urllib network-operation timeout; **not** a total request duration.
* requestDeadlineSeconds (examples: 10): supervisor budget from HTTP admission, including IPC, DNS/connect/TLS, headers, full bounded body, JSON parsing and assertions. Every login/CSRF/setup request is supervised too. The deadline kills that VU's HTTP process, marks the attempt failed and stops the run; there is no replacement session, retry or catch-up.
* stopDrainSeconds (examples: 12): one shared budget from the first detected stop (or scheduled final drain), not one budget per VU. In-flight requests end by their own deadline or the earlier drain deadline. No subsequent B step is admitted after operator stop. Timed-out/cancelled POSTs remain ambiguous and require server reconciliation.
* Before its first HTTP admission, each VU has a separately supervised **10-second worker bootstrap budget**. Startup is excluded from HTTP p95 and can be interrupted by STOP/Ctrl+C. One persistent child retains the VU's private Cookie/CSRF session; credentials/bodies travel only through private pipes.
* Legacy timeoutSeconds is accepted only as a socket setting; explicit deadline/drain defaults are reported with a deprecation warning in offline/manifest plan output. Conflicting legacy and new socket values are rejected.

The supervisor polls at 20 ms. Once a deadline is observed, worker termination/kill/join adds time: each cleanup grace is 0.5 seconds; forced cleanup has terminate, optional kill, reader/writer joins, while idle shutdown can first wait a further 0.5 seconds. VU cleanup runs concurrently. OS scheduling, process creation/reaping and result-file I/O have no arbitrary-machine hard real-time guarantee. **Neither the socket value, total-deadline value nor drain value is a promise for complete program exit.** Failed samples expose deadlineObservedMs separately from elapsedMs (which includes cleanup). See the [D1 local record](../../docs/testing/visit-hpa-d1-local-validation.md) for observed timings and coverage.

manifest timing records the settings, poll and grace; summary timing records stop/signal/drain/admission UTC, in-flight counts and stopToSupervisorEndMs (before final file I/O/program exit). worker-pids.json identifies only this run's children; worker-cleanup.json reports their exit, reader/writer joins and cleanup duration. accountingValid checks planned = launched + dropped + unscheduled. Forced-drain requests are included in failed iterations, identified by error category; they are not counted a second time.

If ordinary STOP/Ctrl+C fails to produce final artifacts, identify the exact original CLI process and this run's worker-pids.json, verify script/argument/ownership, and terminate only those processes. Do not broadly kill Python/Java or delete shared data. A killed parent also closes the private pipe, and the worker's input watchdog exits even during blocked I/O. If summary is absent, or worker cleanup is incomplete, the run is **INCOMPLETE** regardless of partial successful rows. Keep the artifacts and report the gap. This client shutdown does not replace Zhili's separate HPA restoration.


## Disposable real local smoke

Build current core/visit JARs using the project's normal offline-capable build. Record hashes and compare source before reusing existing JARs. Do not use a pre-migration JAR accidentally. Require Java 25 and **already-cached** `mysql:8.4`, `redis:7.4-alpine`. This command never pulls images:

```text
python tools/visit_hpa/local_smoke.py --core-jar /local/core.jar --visit-jar /local/visit.jar --java /local/jdk25/bin/java --docker-context desktop-linux --output /private/results/local-smoke-unique
```

Use Java's actual platform path (`java.exe` on Windows). The helper binds only 127.0.0.1:13306/16379/18080/18081, clears inherited application/cloud overrides **for child services only**, disables events and background business scans, seeds five fictional accounts and four independent caregiver/elder pairs in **empty disposable databases**, then uses actual manager publication APIs to produce four plans/eight scheduled visits. Each pair has one eligible visit and one untouched future control. It uses `{noop}` passwords solely in the disposable local fixtures; this is **not** a shared-environment seed strategy. Same-day Singapore scheduling currently requires starting before the late-evening window stated by the helper; it refuses rather than fabricating legal roster conditions.

It runs scheduled work-pack reads → four unique first check-ins → saved-state/receipt/control SQL checks → repeatable IN_PROGRESS reads. Original containers are snapshotted. Cleanup terminates only its child Java processes and removes only exact-ID/name/ownership-label-matched containers and network. MySQL uses tmpfs. Output records port closure and unchanged original containers. It does not perform broad Docker cleanup or delete a filesystem tree. Keep all logs/config/SQL fixtures under the private output directory. Share only reviewed summaries.

On a hard process kill, `runtime-private/owned-resources.json` and `child-pids.json` identify resources. Verify container label `carelink.visit-hpa=<runId>`, exact name and Java command/config path before manual cleanup. Never use `docker system prune`, wildcard deletion or a stale PID blindly. Shared/cloud fixture cleanup follows the separately approved inventory, not this local teardown.


## D3/D4 update (2026-10-19)

See the [fixed release/migration prerequisites](../../docs/testing/visit-hpa-release-prerequisites.md) and [synthetic A fixture runbook](../../docs/testing/visit-hpa-fixture-runbook.md). The release-lock.json pins application 09d6a3f separately from engine c5b13e2, all 41 migrations and the old report evidence V4 stop condition. fixture_guard.py checks a private four-pair scheduled-small-v1 whitelist/config offline; it does not prepare or query shared data. All approvals and cloud history remain operator checks. Current real local A validation is BLOCKED (Docker unavailable); offline checks are separate evidence.
