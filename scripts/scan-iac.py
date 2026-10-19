#!/usr/bin/env python3
"""Scan tracked Terraform without credentials; retain all findings and gate new ones."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
# Source-analysis fixture only, never inputs to Terraform or the running environment.
SCAN_VARIABLES = {"enable_data_services": True, "high_availability": True,
                  "enable_waf": True, "ha_worker_migration": "expand"}


def copy_sources(root, destination):
    tracked = subprocess.check_output(
        ["git", "ls-files", "-z", "--", "infra"], cwd=root
    ).decode().split("\0")
    manifest = []
    for name in sorted(tracked):
        relative = Path(name)
        if relative.suffix != ".tf" or any(p.startswith(".") for p in relative.parts):
            continue
        source = root / relative
        if source.is_symlink() or not source.resolve().is_relative_to(root.resolve()):
            raise ValueError("Terraform source must be a regular file inside the repository")
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        manifest.append({"path": name, "sha256": hashlib.sha256(source.read_bytes()).hexdigest()})
    if not manifest:
        raise ValueError("No tracked Terraform source; refusing an empty scan")
    return manifest


def read_report(output, returncode):
    if returncode not in (0, 1):
        raise ValueError("Checkov failed to execute")
    report = json.loads(output)
    summary = report["summary"]
    if report.get("check_type") != "terraform" or summary.get("resource_count", 0) <= 0:
        raise ValueError("Checkov did not scan Terraform resources")
    if summary.get("parsing_errors") or report["results"].get("parsing_errors"):
        raise ValueError("Terraform parsing errors must not be treated as a pass")
    if not summary.get("passed", 0) + summary.get("failed", 0) + summary.get("skipped", 0):
        raise ValueError("Checkov produced no check results")
    # A baseline run may retain skipped findings, but execution failures still fail closed.
    if (returncode == 1) != bool(summary.get("failed", 0)):
        raise ValueError("Checkov exit status and findings disagree")
    return report


def require_data_checks(report):
    checks = [c for group in ("passed_checks", "failed_checks", "skipped_checks")
              for c in report["results"].get(group, [])]
    if not any(c["check_id"] == "CKV_AWS_17" and c["resource"].split("[")[0] == "aws_db_instance.primary"
               for c in checks):
        raise ValueError("RDS public-access check missing; optional data resources were not scanned")


def scan(output):
    output.mkdir(parents=True, exist_ok=False)
    expected = (ROOT / ".github/checkov-requirements.txt").read_text().strip().split("==")[1]
    version = subprocess.check_output(["checkov", "--version"], text=True, timeout=30).strip()
    if version != expected:
        raise ValueError("Install the pinned .github/checkov-requirements.txt before scanning")
    with tempfile.TemporaryDirectory(prefix="carelink-checkov-") as temporary:
        workspace = Path(temporary)
        sources = copy_sources(ROOT, workspace)
        (workspace / "home").mkdir()
        (workspace / "config.yaml").write_text("{}\n")
        (workspace / "scan.tfvars.json").write_text(json.dumps(SCAN_VARIABLES))
        # Do not inherit cloud/platform credentials, scanner overrides or user configuration.
        env = {k: v for k, v in os.environ.items()
               if not k.startswith(("AWS_", "TF_", "CKV_", "BC_", "PRISMA_", "CHECKOV_"))}
        env.update(HOME=str(workspace / "home"), AWS_EC2_METADATA_DISABLED="true", LOG_LEVEL="ERROR")
        command = ["checkov", "--directory", str(workspace / "infra"), "--framework", "terraform",
                   "--config-file", str(workspace / "config.yaml"), "--skip-download",
                   "--skip-results-upload", "--download-external-modules", "false",
                   "--var-file", str(workspace / "scan.tfvars.json"),
                   "--compact", "--output", "json"]
        reports = {}
        for name, extra in [("full", []), ("gate", ["--baseline", str(ROOT / ".github/checkov-baseline.json"),
                                                  "--output-baseline-as-skipped"])]:
            result = subprocess.run(command + extra, cwd=workspace, env=env,
                                    capture_output=True, text=True, timeout=180)
            (output / f"{name}-report.json").write_text(result.stdout)
            (output / f"{name}-stderr.log").write_text(result.stderr)
            reports[name] = read_report(result.stdout, result.returncode)
            # Native baseline output omits passed checks; require coverage in the full report.
            if name == "full":
                require_data_checks(reports[name])
        full, gate = reports["full"]["summary"], reports["gate"]["summary"]
        evidence = {"scanned_at_utc": datetime.now(timezone.utc).isoformat(),
                    "checkov_version": version, "sources": sources, "analysis_variables": SCAN_VARIABLES,
                    "full_summary": full, "gate_summary": gate,
                    "baseline_sha256": hashlib.sha256((ROOT / ".github/checkov-baseline.json").read_bytes()).hexdigest(),
                    "meaning": "Gate blocks new findings, execution and parsing failures. "
                               "Pre-existing baseline findings remain unresolved and visible in full-report.json. "
                               "This is source analysis, not a scan of the running AWS configuration."}
        (output / "scan-evidence.json").write_text(json.dumps(evidence, indent=2) + "\n")
        summary = ("### Checkov Terraform scan\n\n"
                   f"- Tracked Terraform files: {len(sources)}\n"
                   f"- All findings: {full['failed']}; new findings: {gate['failed']}\n"
                   f"- Parsing errors: {full['parsing_errors']}\n"
                   "- Existing findings are **not fixed or accepted as safe** by the baseline.\n"
                   "- Download the checkov-iac artifact for both full and differential reports.\n")
        (output / "summary.md").write_text(summary)
        print(summary)
        return 1 if gate["failed"] else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=Path, help="New report directory outside tracked source")
    args = parser.parse_args()
    try:
        return scan(args.output.resolve())
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        # Avoid echoing plugin output or caller configuration into the CI log.
        print(f"IaC scan incomplete ({type(error).__name__}); inspect retained artifacts. No pass recorded.")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
