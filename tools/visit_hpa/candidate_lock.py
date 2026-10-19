"""Offline lock for a committed main integration candidate. No database/network access."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import release_lock as legacy

REPO = legacy.REPO


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def build(application, main):
    app = legacy.git("rev-parse", application + "^{commit}").decode().strip()
    base = legacy.git("rev-parse", main + "^{commit}").decode().strip()
    legacy.git("merge-base", "--is-ancestor", base, app)
    rows = []
    for service in legacy.SERVICES:
        prefix = f"services/{service}/src/main/resources/db/migration/"
        for name in legacy.git("ls-tree", "-r", "--name-only", app, prefix).decode().splitlines():
            match = re.fullmatch(r"V(\d+)__(.+)\.sql", name.rsplit("/", 1)[-1])
            legacy.require(match is not None, "unsupported_migration_name")
            raw = legacy.git("show", app + ":" + name)
            legacy.require(b"${" not in raw, "placeholder_requires_manual_review")
            rows.append(dict(service=service, version=match[1], script=name.rsplit("/", 1)[-1],
                             path=name, gitBlobSha256=digest(raw), flywayChecksum=legacy.flyway_checksum(raw)))
    rows.sort(key=lambda r: (legacy.SERVICES.index(r["service"]), int(r["version"])))
    # Keep the accepted historical lock immutable, without requiring its old application's
    # unreachable Git objects in a fresh clone of this integration branch.
    raw_historical = Path(__file__).with_name("release-lock.json").read_bytes().replace(b"\r\n", b"\n")
    legacy.require(digest(raw_historical) == "054755d3c31556ff1797aa23c6aa4865e95feccd43f1bdc18fba9913c5a33aa2",
                   "accepted_historical_lock_changed")
    historical = json.loads(raw_historical)
    shared_platform = [p for p in legacy.git("ls-tree", "-r", "--name-only", app, "libs").decode().splitlines()
                       if "/src/main/resources/db/platform/" in p and p.endswith(".sql")]
    platform = []
    for service in legacy.SERVICES:
        own = legacy.git("ls-tree", "-r", "--name-only", app,
                         f"services/{service}/src/main/resources/db/platform/").decode().splitlines()
        for name in shared_platform + own:
            match = re.fullmatch(r"V(\d+)__(.+)\.sql", name.rsplit("/", 1)[-1])
            legacy.require(match is not None, "unsupported_platform_migration")
            raw = legacy.git("show", app + ":" + name)
            legacy.require(b"${" not in raw, "platform_placeholder_requires_manual_review")
            platform.append(dict(service=service, historyTable="flyway_platform_history", version=match[1],
                                 script=name.rsplit("/", 1)[-1], path=name,
                                 gitBlobSha256=digest(raw), flywayChecksum=legacy.flyway_checksum(raw)))
    platform.sort(key=lambda r: (legacy.SERVICES.index(r["service"]), int(r["version"])))
    return dict(schemaVersion=2, applicationCommit=app, mainBaseCommit=base,
                applicationGitTree=legacy.git("rev-parse", app + "^{tree}").decode().strip(),
                engineCommit=legacy.ENGINE, acceptedHandoffCommit="34371c24014ed9d12f1f3a6e86873dd34804c007",
                cloudRelease="UNCONFIRMED", sharedHistory="PENDING",
                sourceObjects={p: legacy.git("rev-parse", app + ":" + p).decode().strip() for p in legacy.RELEASE_PATHS},
                engineGitBlobSha256=historical["engineGitBlobSha256"],
                candidateValidatorSha256=digest(Path(__file__).read_bytes().replace(b"\r\n", b"\n")),
                checksumMethod=historical["checksumMethod"], migrations=rows, platformMigrations=platform,
                expectedVersions={s: max(int(r["version"]) for r in rows if r["service"] == s) for s in legacy.SERVICES},
                oldReportEvidenceV4=historical["oldReportEvidenceV4"],
                coreLineage="V26 credential / V27 admission; old numbering requires STOP_REVIEW",
                reportLineage=("main V4 task dispatch; additive V5 evidence projection present" if any(
                    r["service"] == "report" and r["script"] == "V5__visit_evidence_projection.sql" for r in rows)
                    else "V5 evidence projection ABSENT; review runtime capability separately"),
                deploymentApproved=False)


def verify(lock):
    legacy.require(lock == build(lock["applicationCommit"], lock["mainBaseCommit"]), "candidate_manifest_changed")
    legacy.require(not legacy.git("diff", "--name-only", lock["applicationCommit"], "--", *legacy.RELEASE_PATHS).strip(),
                   "candidate_application_tree_changed")
    legacy.require(not legacy.git("ls-files", "--others", "--exclude-standard", "--", *legacy.RELEASE_PATHS).strip(),
                   "untracked_application_files")
    for name, expected in lock["engineGitBlobSha256"].items():
        raw = (Path(__file__).parent / name).read_bytes().replace(b"\r\n", b"\n")
        legacy.require(digest(raw) == expected, "accepted_engine_changed")
    return dict(status="LOCAL_CANDIDATE_SOURCE_MATCH", applicationCommit=lock["applicationCommit"],
                migrations=len(lock["migrations"]), sharedHistory="PENDING", deploymentApproved=False)


def check_history(lock, export):
    legacy.require(type(export) is dict and type(export.get("databases")) is list, "history_container_required")
    for database in export["databases"]:
        legacy.require(type(database) is dict and type(database.get("rows")) is list, "history_database_rows_required")
        legacy.require(all(type(row) is dict for row in database["rows"]), "history_row_object_required")
        ranks = [row.get("installed_rank") for row in database["rows"]]
        legacy.require(all(type(n) is int and n > 0 for n in ranks) and ranks == sorted(set(ranks)),
                       "ordered_unique_installed_ranks_required")
        legacy.require(database.get("completeHistory") is True, "complete_history_assertion_required")
    result = legacy.check_history(lock, export)
    result.update(platformHistoryVerified=False, actualSchemaVerified=False,
                  note="Primary migration histories only. Platform history (including baseline rows) and actual schema still require separate review; no deployment approval.")
    for database, outcome in zip(export["databases"], result["services"]):
        if database["service"] == "core" and any(
            (str(r.get("version")) == "26" and r.get("script") == "V26__caregiver_admission.sql") or
            (str(r.get("version")) == "27" and r.get("script") == "V27__caregiver_credential_submission.sql")
            for r in database["rows"]):
            outcome["issues"] = sorted(set(outcome["issues"] + ["OLD_CORE_NUMBERING_STOP"]))
            outcome["status"] = "STOP_REVIEW"
            result["status"] = "STOP_REVIEW"
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--application-ref")
    parser.add_argument("--main-ref")
    parser.add_argument("--history", help="Private complete operator export, never a database connection")
    args = parser.parse_args()
    try:
        path = Path(args.manifest)
        if args.write:
            legacy.require(bool(args.application_ref and args.main_ref), "explicit_refs_required")
            lock = build(args.application_ref, args.main_ref)
            verify(lock)
            with path.open("x", encoding="utf-8") as f:
                json.dump(lock, f, indent=2); f.write("\n")
        lock = json.loads(path.read_text(encoding="utf-8-sig"))
        result = verify(lock)
        if args.history:
            result["history"] = check_history(lock, json.loads(Path(args.history).read_text(encoding="utf-8-sig")))
        print(json.dumps(result, indent=2))
        return 2 if result.get("history", {}).get("status") == "STOP_REVIEW" else 0
    except (ValueError, KeyError, TypeError, OSError, legacy.subprocess.SubprocessError):
        print(json.dumps(dict(status="FAIL", reason="Changed source, invalid lock or incomplete/unsupported history", deploymentApproved=False)))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
