"""Offline source/migration lock. Never connects to a database or changes Flyway history."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zlib

APP = "09d6a3f9ec453906f613efd0af7f99fad4fdd8d3"
ENGINE = "c5b13e2d91b9e5daa8cb536b8427fa184f60d394"
BOARD2 = "ade044a8bf47c97bb0017dd4445405552ce97b07"
SERVICES = ("core", "visit", "report", "notification")
RELEASE_PATHS = ("services", "libs", "frontend", "charts", "deploy", "infra", "scripts",
                 "pom.xml", ".mvn", "mvnw", "mvnw.cmd", ".github", "docker-compose.yml", ".dockerignore")
ENGINE_FILES = ("load.py", "compare_read.py", "example.joint.json")
REPO = Path(__file__).resolve().parents[2]


def require(ok, code):
    if not ok:
        raise ValueError(code)


def git(*args):
    return subprocess.run(["git", "-C", str(REPO), *args], check=True,
                          capture_output=True, timeout=30).stdout


def flyway_checksum(raw):
    """Flyway 12.4.0 UTF-8 non-placeholder SQL CRC32; Java readLine strips CR/LF, not other spaces."""
    lines = re.split(r"\r\n|\n|\r", raw.decode("utf-8"))
    if lines:
        lines[0] = lines[0].lstrip("\ufeff")
    value = zlib.crc32("".join(lines).encode("utf-8"))
    return value if value < 2**31 else value - 2**32


def build_lock():
    rows = []
    for service in SERVICES:
        prefix = f"services/{service}/src/main/resources/db/migration/"
        files = git("ls-tree", "-r", "--name-only", APP, prefix).decode().splitlines()
        for name in files:
            match = re.fullmatch(r"V(\d+)__(.+)\.sql", name.rsplit("/", 1)[-1])
            require(match is not None, "unexpected_migration_name")
            raw = git("show", APP + ":" + name)
            require(b"$" + b"{" not in raw, "placeholder_checksum_needs_flyway_review")
            rows.append({"service": service, "version": match[1], "script": name.rsplit("/", 1)[-1],
                         "path": name, "gitBlobSha256": hashlib.sha256(raw).hexdigest(),
                         "flywayChecksum": flyway_checksum(raw)})
    rows.sort(key=lambda r: (SERVICES.index(r["service"]), int(r["version"])))
    old = git("show", "b486ccd:services/report/src/main/resources/db/migration/V4__visit_evidence_projection.sql")
    new = git("show", APP + ":services/report/src/main/resources/db/migration/V5__visit_evidence_projection.sql")
    engine = {name: hashlib.sha256(git("show", ENGINE + ":tools/visit_hpa/" + name)).hexdigest()
              for name in ENGINE_FILES}
    return {"schemaVersion": 1, "applicationCommit": APP, "engineCommit": ENGINE,
            "board2ReviewedCommit": BOARD2, "cloudRelease": "UNCONFIRMED",
            "sourceObjects": {p: git("rev-parse", APP + ":" + p).decode().strip() for p in RELEASE_PATHS},
            "engineGitBlobSha256": engine, "migrationEncoding": "UTF-8; no SQL placeholders",
            "checksumMethod": "Flyway 12.4.0 line-normalized signed CRC32; SHA256 is a separate Git-blob hash",
            "expectedVersions": {s: max(int(r["version"]) for r in rows if r["service"] == s) for s in SERVICES},
            "migrations": rows, "oldReportEvidenceV4": {
                "oldCommit": "b486ccd", "oldScript": "V4__visit_evidence_projection.sql",
                "gitBlobSha256": hashlib.sha256(old).hexdigest(), "flywayChecksum": flyway_checksum(old),
                "sameSqlAsNewV5": old == new, "sharedDatabaseApplied": "UNKNOWN"}}


def verify(lock):
    require(lock == build_lock(), "fixed_manifest_changed")
    require(not git("diff", "--name-only", APP, "--", *RELEASE_PATHS).strip(), "application_tree_changed")
    require(not git("ls-files", "--others", "--exclude-standard", "--", *RELEASE_PATHS).strip(), "untracked_application_files")
    for name, expected in lock["engineGitBlobSha256"].items():
        raw = (REPO / "tools" / "visit_hpa" / name).read_bytes()
        require(hashlib.sha256(raw.replace(b"\r\n", b"\n")).hexdigest() == expected, "engine_file_changed")
    return {"status": "PINNED_SOURCE_MATCH", "applicationCommit": APP, "engineCommit": ENGINE,
            "migrations": len(lock["migrations"]), "cloudReleaseVerified": False}


def check_history(lock, export):
    """Operator-exported JSON only. Strict subset before upgrade; exact inventory after upgrade."""
    require(type(export) is dict and type(export.get("schemaVersion")) is int and export["schemaVersion"] == 1, "invalid_history_export")
    entries = export.get("databases")
    require(type(entries) is list, "invalid_history_databases")
    seen, outcomes = set(), []
    for entry in entries:
        require(type(entry) is dict, "history_database_object_required")
        service = entry.get("service")
        require(service in SERVICES and service not in seen, "duplicate_or_unknown_history_service")
        seen.add(service)
        require(all(type(entry.get(k)) is str and entry[k].strip() for k in ("database", "capturedAtUtc", "capturedBy")),
                "history_provenance_required")
        expected = {r["version"]: r for r in lock["migrations"] if r["service"] == service}
        rows = entry.get("rows"); require(type(rows) is list, "history_rows_required")
        found, issues = set(), []
        for row in rows:
            require(type(row) is dict, "history_row_required")
            version = row.get("version")
            if version is not None:
                version = str(version)
            if row.get("type") != "SQL":
                issues.append("non_sql_history_requires_manual_review")
                continue
            if version in found:
                issues.append("duplicate_version")
            found.add(version)
            target = expected.get(version)
            if row.get("success") is not True:
                issues.append("failed_or_untyped_success")
            if target is None:
                issues.append("unknown_version")
                continue
            if row.get("script") != target["script"]:
                issues.append("script_mismatch")
            if type(row.get("checksum")) is not int or row["checksum"] != target["flywayChecksum"]:
                issues.append("checksum_mismatch")
            if not row.get("installed_on") or not row.get("description"):
                issues.append("missing_history_metadata")
            if service == "report" and version == "4" and (
                row.get("script") == lock["oldReportEvidenceV4"]["oldScript"] or
                row.get("checksum") == lock["oldReportEvidenceV4"]["flywayChecksum"]):
                issues.append("OLD_REPORT_EVIDENCE_V4_STOP")
        missing = sorted(set(expected) - found, key=int)
        present_numbers = [int(v) for v in found if v in expected]
        if present_numbers and any(int(v) < max(present_numbers) for v in missing):
            issues.append("history_gap_requires_manual_review")
        outcomes.append({"service": service, "status": "STOP_REVIEW" if issues else (
            "TARGET_MATCH" if not missing else "UPGRADE_REQUIRED"),
            "issues": sorted(set(issues)), "pendingVersions": missing})
    require(seen == set(SERVICES), "all_four_histories_required")
    return {"status": "STOP_REVIEW" if any(r["issues"] for r in outcomes) else "SOURCE_HISTORY_COMPATIBLE",
            "services": outcomes, "deploymentApproved": False,
            "note": "Offline history consistency only; provenance, actual schema, Flyway validate and deployment remain operator checks."}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--manifest", required=True)
    p.add_argument("--write", action="store_true", help="Write NEW lock file from fixed local Git objects")
    p.add_argument("--history", help="Private, read-only exported history JSON; no DB connection")
    a = p.parse_args()
    try:
        path = Path(a.manifest)
        if a.write:
            with path.open("x", encoding="utf-8") as f:
                json.dump(build_lock(), f, indent=2); f.write("\n")
        lock = json.loads(path.read_text(encoding="utf-8-sig"))
        result = verify(lock)
        if a.history:
            result["history"] = check_history(lock, json.loads(Path(a.history).read_text(encoding="utf-8-sig")))
        print(json.dumps(result, indent=2))
        return 2 if result.get("history", {}).get("status") == "STOP_REVIEW" else 0
    except (ValueError, KeyError, TypeError, OSError, subprocess.SubprocessError):
        print(json.dumps({"status": "FAIL", "detail": "Invalid or changed lock/history; inspect offline with Owner."}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
