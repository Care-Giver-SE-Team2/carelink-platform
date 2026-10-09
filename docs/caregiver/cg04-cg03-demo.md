# Local CG04 / CG03 basic execution demonstration

Use only the isolated `carelink-caregiver-demo` project. These are fictional local
accounts, not staging credentials. Existing volume data is retained; do not use `down -v`.

## Start and prepare

With Docker running, build/start the normal demo with its compose file. If Docker Hub
is unavailable but `carelink:caregiver-demo` is already cached, the local fallback uses
that runtime with a newly packaged jar; it does not validate production image creation.

1. Build the frontend and backend with the normal checks.
2. Copy the contents of `frontend/dist` into `backend/target/classes/static`, then
   package the backend **without clean** to include the generated assets. Confirm the
   executable jar includes `BOOT-INF/classes/static/index.html`.
3. Build the fallback and start it without pulling/building the original image:

```powershell
docker build --pull=false -f deploy/caregiver-demo/Dockerfile.local -t carelink:caregiver-execution-demo .
docker compose --project-name carelink-caregiver-demo -f deploy/caregiver-demo/compose.yml -f deploy/caregiver-demo/compose.local.yml up -d --no-build --wait
./scripts/prepare-caregiver-execution-demo.ps1
```

The setup script inserts only synthetic people and a family binding, then uses real
manager primary-caregiver assignment and care-plan publication HTTP commands. It prints
the Singapore service date, plan and Visit IDs. Rerunning publishes a new demo plan:
use it before a demonstration, not while someone is executing a previous demo Visit.

Open `http://localhost:8081`. All four local-only accounts use password `Demo#2026`:
`demo-exec-cg-a`, `demo-exec-cg-b`, `demo-exec-manager`, `demo-exec-family`.
Use separate browser profiles/private windows or log out before changing roles.

## What to confirm by eye

1. Caregiver A: choose the printed date on My schedule, open **Execution demo care
   routine**, inspect the assigned plan version and check-in window. B must not access
   A's work-pack.
2. Select the explicitly labelled manual-location option and enter a fictional note,
   then Check in. Observe IN_PROGRESS, the server timestamp, manual source and one
   initialized task. Refresh: it stays saved and Check in cannot be repeated.
3. Save DONE with optional factual text. Refresh: the task is read-only and has a
   completion timestamp, while the Visit stays IN_PROGRESS, not COMPLETED. Separate
   fresh Visits are needed to demonstrate SKIPPED and REFUSED; reasons are mandatory.
4. Family: open that Visit's progress, timeline and tasks. Observe arrival/start and
   the task result. Caregiver notes, outcome and precise/manual location must not appear.
5. After the scheduled start, A opens Report incident, selects category/severity and
   records fictional facts. The own receipt shows its number and live status; the
   work-pack becomes EXCEPTION and further task writes are blocked.
6. Manager: locate the same incident in the existing queue/notification, claim and
   resolve with fictional handling notes. A refreshes own report and sees RESOLVED;
   this does not resume or complete the Visit. B cannot open A's own-report detail.
7. Unknown-result handling is tested automatically: no automatic POST retry; the
   original UUID/payload is retained on the original page. Own reports open in a new
   tab so reconciliation does not discard that command.

## Recorded verification boundary (2026-10-07)

The isolated application started healthy and manager HTTP assignment/publication
created a current-day Visit. Automated real HTTP/MySQL workflows cover check-in,
three task results, incident/manager handling, family projections and rollback.
Browser-based visual acceptance was **not completed**: the available in-app browser
rejected the local address with `ERR_BLOCKED_BY_CLIENT`; no alternative controlled
browser was available. Do not interpret automated DOM/HTTP tests as a screenshot or
as the user's manual acceptance. Record that separately in external plans 002/003.

Not delivered here: evidence uploads, vital signs, check-out/CG05, independent elder
confirmation, automatic exception recovery or SYS03 missed-check-in scheduling.
