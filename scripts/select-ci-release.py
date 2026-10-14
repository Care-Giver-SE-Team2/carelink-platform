#!/usr/bin/env python3
"""Select a successful main CI run and reject stale automatic deployment."""
import json
import os
import re
import subprocess

REPOSITORY = "Care-Giver-SE-Team2/carelink-platform"
REPOSITORY_ID = 1408273257


def select(metadata, main_sha, automatic):
    if (metadata.get("status") != "completed" or metadata.get("conclusion") != "success"
            or metadata.get("head_branch") != "main" or metadata.get("path") != ".github/workflows/ci.yml"
            or metadata.get("event") not in ("push", "workflow_dispatch")
            or metadata.get("repository", {}).get("id") != REPOSITORY_ID
            or metadata.get("head_repository", {}).get("id") != REPOSITORY_ID):
        raise ValueError("Select a successful main CI run from this repository")
    sha = metadata.get("head_sha", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid CI commit")
    if automatic and sha != main_sha:
        raise ValueError("A newer main commit superseded this release")
    return sha


if __name__ == "__main__":
    assert os.environ["GH_REPO"] == REPOSITORY
    run_id = os.environ["CI_RUN_ID"]
    assert re.fullmatch(r"[0-9]+", run_id)
    metadata = json.loads(subprocess.check_output(["gh", "api", f"repos/{REPOSITORY}/actions/runs/{run_id}"]))
    main = json.loads(subprocess.check_output(["gh", "api", f"repos/{REPOSITORY}/commits/main"]))
    sha = select(metadata, main["sha"], os.environ.get("AUTOMATIC_DEPLOY") == "true")
    print("sha=" + sha)
    print("run_id=" + run_id)
