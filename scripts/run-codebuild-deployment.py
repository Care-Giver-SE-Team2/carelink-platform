#!/usr/bin/env python3
"""Upload the verified source/release to a private prefix and run VPC deployment."""
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile
import time
import zipfile


def aws(*args):
    return subprocess.check_output(["aws", "--region", "ap-southeast-1", *args], text=True)


def validate(manifest, sha, account, bucket, project):
    if (not re.fullmatch(r"[0-9a-f]{40}", sha) or not re.fullmatch(r"[0-9]{12}", account)
            or bucket != f"carelink-tfstate-{account}-ap-southeast-1"
            or project != "carelink-staging-deploy"):
        raise ValueError("Unexpected deployment target")
    if set(manifest["services"]) != {"core", "visit", "report", "notification"}:
        raise ValueError("A complete four-service release is required")
    for service, record in manifest["services"].items():
        if (record["tag"] != sha or record["service"] != service
                or record["image"] != f"{account}.dkr.ecr.ap-southeast-1.amazonaws.com/carelink/{service}"
                or not re.fullmatch(r"sha256:[0-9a-f]{64}", record["digest"])):
            raise ValueError("Backend image does not match this CI commit")


def main(path):
    manifest = json.loads(Path(path).read_text())
    sha, account = os.environ["RELEASE_SHA"], os.environ["AWS_ACCOUNT_ID"]
    bucket, project = os.environ["DEPLOY_INPUT_BUCKET"], os.environ["CODEBUILD_PROJECT"]
    validate(manifest, sha, account, bucket, project)
    if subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip() != sha:
        raise ValueError("Source checkout does not match the CI release")
    if json.loads(aws("sts", "get-caller-identity"))["Account"] != account:
        raise ValueError("Wrong AWS account")
    attempt = os.environ["GITHUB_RUN_ID"] + "-" + os.environ.get("GITHUB_RUN_ATTEMPT", "1")
    if not re.fullmatch(r"[0-9]+-[0-9]+", attempt):
        raise ValueError("Invalid workflow run identity")
    key = f"carelink/deployment-inputs/{sha}/{attempt}/source.zip"
    with tempfile.TemporaryDirectory() as temporary:
        archive = Path(temporary) / "source.zip"
        subprocess.run(["git", "archive", "--format=zip", "--output", str(archive), sha], check=True)
        with zipfile.ZipFile(archive, "a", compression=zipfile.ZIP_DEFLATED) as bundle:
            bundle.writestr(".deployment/release.json", json.dumps(manifest))
        aws("s3", "cp", str(archive), f"s3://{bucket}/{key}", "--sse", "AES256", "--only-show-errors")
    build = json.loads(aws("codebuild", "start-build", "--project-name", project,
                          "--source-location-override", bucket + "/" + key,
                          "--environment-variables-override", f"name=RELEASE_SHA,value={sha},type=PLAINTEXT"))["build"]
    build_id = build["id"]
    print("VPC deployment build: " + build_id, flush=True)
    active = True
    try:
        deadline = time.monotonic() + 1800
        while time.monotonic() < deadline:
            builds = json.loads(aws("codebuild", "batch-get-builds", "--ids", build_id))["builds"]
            if len(builds) != 1:
                raise RuntimeError("Deployment build was not returned")
            status = builds[0]["buildStatus"]
            if status != "IN_PROGRESS":
                active = False
                if status != "SUCCEEDED":
                    raise RuntimeError("VPC deployment failed: " + status + "; inspect this build's CloudWatch log")
                print("Verified backend deployment succeeded for " + sha)
                return
            time.sleep(15)
        raise TimeoutError("VPC deployment exceeded 30 minutes")
    finally:
        if active:
            aws("codebuild", "stop-build", "--id", build_id)


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, lambda *_: sys.exit(1))
    main(sys.argv[1])
