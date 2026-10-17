#!/usr/bin/env python3
"""Opt-in, unauthenticated passive baseline of the accepted staging website."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request
from xml.sax.saxutils import escape

REPOSITORY = "Care-Giver-SE-Team2/carelink-platform"
REPOSITORY_ID = 1408273257
TARGET = "https://dzfs8sv334fwf.cloudfront.net"
IMAGE = "ghcr.io/zaproxy/zaproxy:stable"


def accepted_commit(run, jobs):
    if (run.get("status") != "completed" or run.get("conclusion") != "success"
            or run.get("head_branch") != "main" or run.get("event") != "workflow_run"
            or run.get("path") != ".github/workflows/deploy.yml"
            or run.get("repository", {}).get("id") != REPOSITORY_ID
            or run.get("head_repository", {}).get("id") != REPOSITORY_ID):
        raise ValueError("Select a successful automatic staging deployment from this repository")
    sha = run.get("head_sha", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid staging commit")
    results = {job["name"]: job.get("conclusion") for job in jobs}
    required = ("release", "deploy", "frontend / deploy", "acceptance")
    if any(results.get(name) != "success" for name in required):
        raise ValueError("Backend, frontend and public acceptance must all have succeeded")
    return sha


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError("Staging version check must not redirect to another target")


def public_commit():
    client = urllib.request.build_opener(NoRedirect())
    request = urllib.request.Request(TARGET + "/version.json", headers={"Cache-Control": "no-cache"})
    with client.open(request, timeout=30) as response:
        return json.load(response).get("commit")


def context_xml():
    # No /api endpoints, forms, authenticated sessions or arbitrary external targets.
    scope = (re.escape(TARGET) + r"/(?:|index\.html|version\.json|robots\.txt|sitemap\.xml|assets/[^?#]+)"
             + r"(?:\?[^#]*)?")
    return ('<?xml version="1.0" encoding="UTF-8"?>\n'
            '<configuration><context><name>carelink-public-baseline</name>'
            '<inscope>true</inscope><incregex>' + escape(scope) + '</incregex>'
            '</context></configuration>\n')


def gh(path):
    return json.loads(subprocess.check_output(["gh", "api", f"repos/{REPOSITORY}/{path}"], text=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if os.environ.get("CARELINK_ZAP_ENABLED") != "true":
        raise ValueError("ZAP is disabled; enabling the repository flag is a separate decision")
    if not re.fullmatch(r"[0-9]+", args.run_id):
        raise ValueError("Invalid deployment run ID")
    run = gh(f"actions/runs/{args.run_id}")
    jobs = gh(f"actions/runs/{args.run_id}/attempts/{run['run_attempt']}/jobs?per_page=100")
    sha = accepted_commit(run, jobs["jobs"])
    args.output.mkdir(parents=True, exist_ok=True)
    evidence = {"target": TARGET, "commit": sha, "staging_run_id": args.run_id,
                "staging_run_attempt": run["run_attempt"], "staging_url": run["html_url"],
                "started_at_utc": datetime.now(timezone.utc).isoformat(),
                "scope": "unauthenticated public static pages/assets; no API or active scan",
                "completed": False, "accepted": False}
    result = args.output / "release-evidence.json"
    result.write_text(json.dumps(evidence, indent=2) + "\n")
    if public_commit() != sha:
        raise ValueError("Website has moved to another release; select its accepted deployment")
    (args.output / "scope.context").write_text(context_xml())
    # The runner owns the directory; the official image runs as its non-root zap user.
    args.output.chmod(0o777)
    subprocess.run(["docker", "pull", IMAGE], check=True, timeout=180)
    image = json.loads(subprocess.check_output(["docker", "image", "inspect", IMAGE], text=True))[0]
    evidence["scanner_image_id"] = image["Id"]
    evidence["scanner_repo_digests"] = image.get("RepoDigests", [])
    result.write_text(json.dumps(evidence, indent=2) + "\n")
    container = f"carelink-zap-{os.getpid()}"
    command = ["docker", "run", "--rm", "--name", container, "--cpus=1", "--memory=2g", "--cap-drop=ALL",
               "--security-opt=no-new-privileges", "-v", f"{args.output.resolve()}:/zap/wrk:rw",
               image["Id"], "zap-baseline.py", "--autooff", "-t", TARGET + "/",
               "-n", "/zap/wrk/scope.context", "-m", "1", "-T", "5",
               "-r", "report.html", "-J", "report.json", "-w", "report.md",
               "-z", "-config spider.thread=1 -config spider.maxDepth=2 "
               "-config spider.maxChildren=20 -config spider.processForm=false "
               "-config spider.postForm=false -config spider.parseRobotsTxt=false "
               "-config spider.parseSitemapXml=false"]
    try:
        completed = subprocess.run(command, timeout=420)
        evidence["zap_exit_code"] = completed.returncode
        evidence["public_commit_after"] = public_commit()
        evidence["completed"] = ((args.output / "report.json").is_file()
                                 and (args.output / "report.html").is_file())
        if not evidence["completed"] or evidence["public_commit_after"] != sha:
            raise ValueError("Missing reports or website changed during scan; result is not accepted")
        if completed.returncode != 0:
            raise ValueError("ZAP reported findings or failed; inspect reports (WARN is not a clean pass)")
        evidence["accepted"] = True
    finally:
        subprocess.run(["docker", "rm", "-f", container], stdout=subprocess.DEVNULL,
                       stderr=subprocess.DEVNULL, timeout=30, check=False)
        evidence["finished_at_utc"] = datetime.now(timezone.utc).isoformat()
        result.write_text(json.dumps(evidence, indent=2) + "\n")
    print("Passive baseline passed for accepted staging commit " + sha)


if __name__ == "__main__":
    main()
