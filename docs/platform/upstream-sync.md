# Taking changes from upstream

This repository started from the care platform's single-service code base, which its own team keeps developing
(the `upstream` remote, push disabled). Since then the code here has been split: visit, report and notification are
services of their own, shared code is in `libs/`, the services talk through internal APIs and events, and each has a
database of its own ([service-boundaries.md](service-boundaries.md), section 9). So upstream's changes can no longer
be taken by merging the whole tree. They are ported, a batch at a time.

**Last synced:** upstream `d6f3656` (2026-10-16, upstream #100–#110). Update this line in every sync pull
request.

## 1. What has changed upstream

```sh
git fetch upstream
git log --oneline f9bc4f1..upstream/main          # the last synced commit, from the line above
git diff --stat f9bc4f1 upstream/main
```

Sync in batches, not one commit at a time: after upstream merges a set of changes, or at an agreed point.

## 2. Where each upstream file lives here

| Upstream path (`backend/src/...`) | Here |
|---|---|
| `java/sg/nus/carelink/{careplan,identity,incident,profile,rostering}/` | `services/core/src/.../` (same package) |
| `java/sg/nus/carelink/visit/` | `services/visit/src/.../visit/` |
| `java/sg/nus/carelink/report/` | `services/report/src/.../report/` |
| `java/sg/nus/carelink/notification/` | `services/notification/src/.../notification/` |
| `java/sg/nus/carelink/shared/` | errors, request context, access audit, roles, `GlobalExceptionHandler`: `libs/shared`; security configuration and page controllers: `services/core` |
| `resources/db/migration/` | see section 4 |
| `resources/db/demo/` | `services/core/src/main/resources/db/demo/`, with each service's tables named with its database |
| `frontend/` | `frontend/` |

## 3. Bring the commits over

Cherry-pick each upstream commit with directory rename detection. Files that only moved are found at their new
place, and the original author is kept:

```sh
git switch -c feat/upstream-sync-<last commit>
git -c merge.directoryRenames=true cherry-pick -x <commit>
```

Then look at what git could not decide:
- **A file that upstream added** goes where its package now lives (section 2). git places it by the nearest moved
  directory, which can be the wrong one. For example, upstream #97 adds `SpaPageController` to `shared/web`, and git
  puts it in `libs/shared`; it belongs in core, since every service loads `libs/shared`.
- **A file that upstream changed and that moved to another service** shows as a conflict. Apply the change by hand
  in its new place.
- **A class that reaches another service's code or tables** is not carried over as it is. Replace the call or the
  SQL with the service's API (`CoreApi`, `VisitApi`) or one of its events, the way
  [service-boundaries.md](service-boundaries.md) section 5 did for the code that was there at the split.

## 4. Migrations

Upstream numbers its migrations in one sequence, for one schema (V26 is the next). Here each service migrates its
own database:

| An upstream migration that changes | Goes to | As |
|---|---|---|
| core's tables | `services/core/src/main/resources/db/migration/` | the next number there |
| visit's tables | `services/visit/src/main/resources/db/migration/` | the next number there |
| report's tables | `services/report/src/main/resources/db/migration/` | the next number there |
| notification's tables | `services/notification/src/main/resources/db/migration/` | the next number there |

A migration that touches tables of two services is split in two. A foreign key between two services' tables is
left out: the service checks the reference through the other's API instead. `ServiceTables` (libs/test-support)
lists which table belongs to which service; a new table goes on its service's list.

## 5. Before the pull request

- `scripts/build.sh all test`, or the CI, is green. Three checks catch most of what a port can get wrong:
  - each service's `OwnTablesTest` fails on SQL or a JPA mapping that names another service's table;
  - core's `CrossServiceForeignKeysIT` fails when a service's table or a key to one comes back into core's database;
  - each service's integration tests run on its own database, so a statement on another service's table fails.
- The **Last synced** line above names the last upstream commit taken.
- The pull request lists the upstream commits it brings and anything ported by hand.
