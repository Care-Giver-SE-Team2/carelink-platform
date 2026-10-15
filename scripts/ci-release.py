#!/usr/bin/env python3
"""Build only changed inputs, but publish a verified, complete main release."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def load(name):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


release = load("release-manifest")
frontend = load("frontend-release")
source = load("select-ci-release")
SERVICES = release.SERVICES
COMPONENTS = (*SERVICES, "frontend")
SHARED = ("pom.xml", "mvnw", ".mvn", ".dockerignore", "build", "libs", "docs/api",
          ".github/workflows/ci.yml", ".github/workflows/service.yml", ".trivyignore",
          "scripts/build.sh", "scripts/push-image.sh", "scripts/sonar-project.sh", "scripts/ci-release.py")


def git(*args):
    return subprocess.check_output(["git", *args])


def ancestor(older, newer):
    return subprocess.run(["git", "merge-base", "--is-ancestor", older, newer],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def fingerprint(sha, component):
    if component == "frontend":
        paths = ("frontend", "scripts/frontend-release.py", "scripts/ci-release.py", ".github/workflows/ci.yml")
    else:
        # Maven reads every module's pom even when building one service with -pl/-am.
        paths = (*SHARED, f"services/{component}", *(f"services/{s}/pom.xml" for s in SERVICES))
    return hashlib.sha256(git("ls-tree", "-r", "-z", sha, "--", *paths)).hexdigest()


def fingerprints(sha):
    return {name: fingerprint(sha, name) for name in COMPONENTS}


def validate_provenance(proof, sha, records):
    if (set(proof) != {"commit", "fingerprints", "image_commits"} or proof["commit"] != sha
            or set(proof["fingerprints"]) != set(COMPONENTS)
            or any(not re.fullmatch(r"[0-9a-f]{64}", value) for value in proof["fingerprints"].values())
            or proof["image_commits"] != {s: records[s]["tag"] for s in SERVICES}):
        raise ValueError("Release provenance does not match this complete release")


def verify(directory, sha, registry):
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Invalid release commit")
    directory = Path(directory)
    records = release.read_manifest(directory / "release.json", registry)
    proof = json.loads((directory / "inputs.json").read_text())
    validate_provenance(proof, sha, records)
    if proof["fingerprints"] != fingerprints(sha):
        raise ValueError("Release input fingerprints differ from the selected source")
    for name, record in records.items():
        if not ancestor(record["tag"], sha) or fingerprint(record["tag"], name) != proof["fingerprints"][name]:
            raise ValueError(f"{name}: image source differs from the selected main source")
    frontend.verify(directory / "frontend", sha)
    return proof


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True)


def download(run_id, name, directory):
    gh("run", "download", str(run_id), "--repo", source.REPOSITORY,
       "--name", name, "--dir", str(directory))


def baseline(directory, sha, registry):
    runs = json.loads(gh("api", f"repos/{source.REPOSITORY}/actions/workflows/ci.yml/runs?branch=main&status=success&per_page=30"))
    for run in runs["workflow_runs"]:
        try:
            previous = source.select(run, sha, False)
        except ValueError:
            continue
        if previous == sha or not ancestor(previous, sha):
            continue
        name = "verified-release-" + previous
        artifacts = json.loads(gh("api", f"repos/{source.REPOSITORY}/actions/runs/{run['id']}/artifacts?per_page=100"))
        if not any(a["name"] == name and not a["expired"] for a in artifacts["artifacts"]):
            continue
        download(run["id"], name, directory)
        verify(directory, previous, registry)
        print(f"Reusing verified baseline {previous} from successful main CI {run['id']}")
        return previous
    return None


def plan(directory, registry):
    directory = Path(directory)
    directory.mkdir(parents=True)
    sha = git("rev-parse", "HEAD").decode().strip()
    event = os.environ["EVENT"]
    previous = None
    if event == "push" and os.environ["GITHUB_REF"] == "refs/heads/main":
        previous = baseline(directory / "baseline", sha, registry)
    elif event == "pull_request":
        base = os.environ.get("BASE", "")
        if re.fullmatch(r"[0-9a-f]{40}", base) and ancestor(base, sha):
            previous = base
    current = fingerprints(sha)
    old = fingerprints(previous) if previous else {}
    picked = [s for s in SERVICES if old.get(s) != current[s]]
    build_frontend = old.get("frontend") != current["frontend"]
    document = {"commit": sha, "baseline": previous, "fingerprints": current,
                "services": picked, "frontend": build_frontend}
    (directory / "plan.json").write_text(json.dumps(document, indent=2) + "\n")
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write("services=" + json.dumps(picked) + "\nfrontend=" + str(build_frontend).lower() + "\n")
    print("Build services: " + json.dumps(picked) + "; build frontend: " + str(build_frontend))


def assemble(inputs, directory, registry):
    inputs, directory = Path(inputs), Path(directory)
    plan = json.loads((inputs / "plan.json").read_text())
    sha = git("rev-parse", "HEAD").decode().strip()
    if plan["commit"] != sha or plan["fingerprints"] != fingerprints(sha):
        raise ValueError("Build plan does not match this CI source")
    old = inputs / "baseline"
    records = {}
    previous = {}
    if plan["baseline"]:
        previous = verify(old, plan["baseline"], registry)["fingerprints"]
        records = release.read_manifest(old / "release.json", registry)
    if (plan["services"] != [s for s in SERVICES if previous.get(s) != plan["fingerprints"][s]]
            or plan["frontend"] != (previous.get("frontend") != plan["fingerprints"]["frontend"])):
        raise ValueError("Build selection omits changed source inputs")
    directory.mkdir(parents=True)
    with tempfile.TemporaryDirectory() as temporary:
        for name in plan["services"]:
            target = Path(temporary) / name
            download(os.environ["GITHUB_RUN_ID"], f"ecr-reference-{name}-{sha}", target)
            record = json.loads((target / "image-record.json").read_text())
            if release.validate_record(record, registry) != name or record["tag"] != sha:
                raise ValueError("New image record differs from this CI build")
            records[name] = record
    if set(records) != set(SERVICES):
        raise ValueError("A complete four-service release is required")
    site = directory / "frontend"
    if plan["frontend"]:
        download(os.environ["GITHUB_RUN_ID"], "frontend-dist-" + sha, site)
        frontend.verify(site, sha)
    else:
        shutil.copytree(old / "frontend", site)
        # Source-identical assets are reusable; the public marker still identifies this release.
        (site / "site/version.json").write_text(json.dumps({"commit": sha}) + "\n")
        (site / "release.json").write_text(json.dumps({"commit": sha, "files": frontend.files(site)}, indent=2) + "\n")
    (directory / "release.json").write_text(json.dumps({"schema_version": 1, "services": records}, indent=2) + "\n")
    proof = {"commit": sha, "fingerprints": plan["fingerprints"],
             "image_commits": {s: records[s]["tag"] for s in SERVICES}}
    (directory / "inputs.json").write_text(json.dumps(proof, indent=2) + "\n")
    verify(directory, sha, registry)
    print("Verified complete release for " + sha)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("plan", "assemble", "verify", "verify-archive"))
    parser.add_argument("--directory", required=True)
    parser.add_argument("--inputs")
    parser.add_argument("--sha")
    args = parser.parse_args()
    registry = os.environ["IMAGE_REGISTRY"]
    if not re.fullmatch(r"[0-9]{12}\.dkr\.ecr\.ap-southeast-1\.amazonaws\.com", registry):
        raise ValueError("Select the Singapore ECR registry")
    if args.command == "plan":
        plan(args.directory, registry)
    elif args.command == "assemble":
        assemble(args.inputs, args.directory, registry)
    elif args.command == "verify":
        verify(args.directory, args.sha, registry)
    else:
        records = release.read_manifest(Path(args.directory) / "release.json", registry)
        proof_path = Path(args.directory) / "inputs.json"
        if proof_path.exists():
            validate_provenance(json.loads(proof_path.read_text()), args.sha, records)
        elif any(record["tag"] != args.sha for record in records.values()):
            raise ValueError("Mixed image commits require verified source provenance")
