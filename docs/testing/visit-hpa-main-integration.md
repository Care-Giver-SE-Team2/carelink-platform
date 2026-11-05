# Visit HPA: latest-main local integration candidate

Date: 2026-10-20. Owner: Chenyu. This branch is independent of PR68 and C05.

The source base is `bfad2c7a3a8bbd03123389f9c6be7688b1864ce4` (main including PR69 and PR70). A local integration commit records application changes; a later handoff commit may add manifests and evidence without changing application inputs. Neither is a deployment identifier.

## Application decisions

- Keep main's **core V26 credential / V27 admission / V28 work settings** exactly as published. No renumbering, checksum editing or Flyway repair. Databases that applied the old branch's V26 admission / V27 credential must stop for a separately reviewed compatibility route.
- Preserve main's **report V4 task dispatch**, worker behavior and `VisitClosed`. Restore **V5 evidence projection** together with the upload producer, event contract, API fact handler, facts query and worker compatibility tests. Upload retries publish once; rollback publishes nothing; snapshots cannot rewind a newer visit state. This does not introduce evidence verification or AI calls.
- Main's incomplete-session probes failed on real Redis. Restore only the existing five-file atomic Redis fix: same namespace/serialization/cookie format, conditional atomic save, no resurrection after logout/expiry, fail closed for incomplete hashes, real Redis faults still surfaced. CSRF remains enforced.
- C05 late logout/anonymous-response Cookie ordering and frontend account-switch work are **not completed here**. Their independent branch and evidence remain preserved. Fixed-session A reading does not log out or switch accounts during load.

## Version binding and local commands

Use Python 3.10+, Java 25 and Maven. Build offline with previously cached dependencies; Docker Desktop must have cached `mysql:8.4` and `redis:7.4-alpine`. This workflow never pulls a database image or starts a cloud client.

`candidate_lock.py` is the new entry point. The old `release_lock.py` and `release-lock.json` retain their historical 09d meaning. Do not relabel old JARs or edit a legacy lock to claim a new application.

The checked-in `candidate-release-lock.json` binds the tested application source. `example.joint.main-candidate.json` and `fixture-whitelist.main-candidate.example.json` bind that same source while retaining placeholder IDs and unconfirmed approvals. They are not ready-to-run cloud data. The sanitized execution evidence is in [the local evidence folder](evidence/visit-hpa-main-integration-20261020/README.md).

From the repository root, after the application source has a real local commit:

```text
python tools/visit_hpa/candidate_lock.py --manifest /private/candidate.json --write --application-ref <APP_SHA> --main-ref bfad2c7a3a8bbd03123389f9c6be7688b1864ce4
python tools/visit_hpa/candidate_lock.py --manifest /private/candidate.json
python -m unittest discover -s tools/visit_hpa/tests -v
```

The output path must be new. Git history for the explicit application and main base must be available. The validator rejects changed manifests, changed/untracked application inputs and changed accepted engine files. It inventories primary and platform SQL separately; primary history checks do not approve platform history or actual schema compatibility.

Build the four services and required libraries from this checkout. Run the focused integration classes listed in `services/core/.../MainMigrationLineageIT.java`, `SessionStoreIT`, caregiver admission/credential/work-settings/work-pack/evidence, family tasks, report schema/projection/events/dispatch/worker/flow. No real AWS or model credentials are needed. Existing test doubles replace internal service calls in those integration tests; the runtime acceptance below uses real core and visit processes.

A private build manifest has this shape (all hashes come from the actual new build):

```json
{"applicationCommit":"<APP_SHA>","jarHashes":{"core":"<64-hex SHA256>","visit":"<64-hex SHA256>"}}
```

Verify source and JAR hashes before each run. The local helper requires both manifests and refuses occupied ports or an existing output directory:

```text
python tools/visit_hpa/local_main_acceptance.py --candidate-manifest /private/candidate.json --build-manifest /private/build.json --core-jar /private/core.jar --visit-jar /private/visit.jar --java /path/to/java --output /private/local-run-unique
```

Run twice with fresh output paths. It starts labelled temporary MySQL/Redis plus **two core and two visit processes**, all on loopback. Only the otherwise-unavailable initial manager is bootstrapped by SQL. Four caregivers go through normal certificate-free registration and manager approval; four elders, primary assignments, published plans and generated rosters use normal APIs. Generated visits are inventoried privately. No business-table SQL is used to fabricate visits.

Each caregiver has two independent login jars (eight distinct sessions). Each is checked on both core and both visit instances, followed by concurrent work-pack reads, cross-actor rejection, old-cookie 401 after one logout and survival of the other independent session. The default small workload is four reads with real setup/CSRF. Runtime output includes assertions, audit/state checks, source/JAR identity, and owned-resource cleanup. Raw logs, credentials, fixture identifiers and Cookie values stay private. Session fingerprints in summaries are hashes only.

The optional `--include-handoff-checks` repeats the historical longer local CLI pilots and STOP/Ctrl+C checks; these are not needed to claim HPA behavior and are never a substitute for cloud evidence. The accepted load engine itself is unchanged.

## Shared-history and cloud boundary

Local empty/prefix/negative histories are generated by Flyway in disposable databases. They do not identify a shared database's actual lineage.

Before merge/deployment, Zhili or a named authorized reader must return, for each actual service/schema:

1. Complete ordered **`flyway_schema_history` and `flyway_platform_history`**, raw export plus provenance/UTC/executor; preserve baseline, NULL, signed checksums and failed/special rows. Include table/schema mapping and actual Flyway configuration/version. Platform baseline rows require separate review.
2. Core V26/V27 lineage, report V4/V5 lineage and relevant read-only schema summary. Old core numbering or old report evidence V4 means STOP; adding a later SQL does not fix an earlier checksum conflict.
3. The intended application SHA and actual four-service/worker image digests after authorized deployment. A frontend version file is insufficient.
4. Redis namespace/format and Lua permission compatibility; environment/data/audit owners, retention, approved routes, monitoring/capacity, HPA Helm load/restore, traffic/IP budget and execution window.

Optional primary-history consistency check, offline only:

```text
python tools/visit_hpa/candidate_lock.py --manifest /private/candidate.json --history /private/four-primary-histories.json
```

Use the historical export structure with an added `completeHistory: true` per database and ordered integer `installed_rank` per row. Missing/incomplete/untyped histories fail; old core/report lineages stop. Even a consistency match leaves platform/schema/deployment approval pending.

For cloud A, copy the accepted four-pair template privately and set **both** `config.codeBaseline` and `whitelist.sourceApplicationCommit` to the selected APP_SHA. Retain 5 RPS / 2700 business GET per arm, eight VUs, the reviewed deadlines, assertions, audit/WAF budget and same-condition rules. Fill actual deployment/approval/window fields only after Zhili confirms them. Do not run the example remotely as supplied. A0/A1 must use the same newly approved application; old-baseline A0 cannot be paired with new-baseline A1.

Local success permits review/push preparation, **not merge, deployment or cloud-test GO**. Zhili independently executes authorized cloud tests; no shared AWS credentials, Cookie or kubeconfig is needed.
