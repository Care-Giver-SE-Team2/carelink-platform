#!/usr/bin/env python3
"""Read-only release, AWS identity, EKS endpoint and Kubernetes connectivity checks."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def run(command, label):
    try:
        result = subprocess.run(command, capture_output=True, text=True, timeout=30)
    except subprocess.TimeoutExpired:
        raise ValueError(f"{label}: timed out; check network reachability") from None
    if result.returncode:
        # Report recognized categories, never arbitrary CLI output or kubeconfig contents.
        error = re.search(r"An error occurred \(([A-Za-z0-9]+)\)", result.stderr)
        if error:
            detail = error.group(1)
        elif "Forbidden" in result.stderr or "forbidden" in result.stderr:
            detail = "Forbidden (Kubernetes authorization)"
        elif "Unauthorized" in result.stderr or "must be logged in" in result.stderr:
            detail = "Unauthorized (Kubernetes authentication)"
        elif any(word in result.stderr.lower() for word in ("timeout", "timed out", "unable to connect", "no such host")):
            detail = "Connection failed; check network, DNS and EKS public access CIDR"
        else:
            detail = "command failed; check credentials, configuration and tool installation"
        raise ValueError(f"{label}: {detail}")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("environment", choices=("staging", "demo"))
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--registry", required=True)
    parser.add_argument("--context", required=True)
    parser.add_argument("--profile", help="Local AWS profile; omit in an OIDC runner")
    args = parser.parse_args()
    rows = run([sys.executable, str(ROOT / "scripts/release-manifest.py"), "--registry", args.registry,
                "resolve", args.manifest], "Release manifest validation").splitlines()
    aws = ["aws", "--region", "ap-southeast-1", "--output", "json", "--no-cli-pager",
           "--cli-connect-timeout", "10", "--cli-read-timeout", "15"]
    if args.profile:
        aws.extend(["--profile", args.profile])
    account = args.registry.split(".")[0]
    identity = json.loads(run(aws + ["sts", "get-caller-identity"], "AWS login"))
    if identity["Account"] != account:
        raise ValueError("AWS login: account does not match the release registry")
    cluster = json.loads(run(aws + ["eks", "describe-cluster", "--name", "carelink-runtime"], "EKS cluster"))["cluster"]
    if cluster["status"] != "ACTIVE":
        raise ValueError("EKS cluster: wait until carelink-runtime is ACTIVE")
    kube = ["kubectl", "--context", args.context, "--request-timeout=15s"]
    config = json.loads(run(kube + ["config", "view", "--minify", "-o", "json"], "Kubernetes context"))
    if (len(config["clusters"]) != 1
            or config["clusters"][0]["cluster"]["server"] != cluster["endpoint"]):
        raise ValueError("Kubernetes context: endpoint differs from carelink-runtime")
    # Verify the digest exists AND belongs to the recorded immutable commit tag.
    for row in rows:
        service, tag, digest = row.split("\t")
        details = json.loads(run(aws + ["ecr", "describe-images", "--repository-name", f"carelink/{service}",
                                       "--image-ids", f"imageTag={tag}"], f"ECR image {service}"))["imageDetails"]
        if len(details) != 1 or details[0]["imageDigest"] != digest:
            raise ValueError(f"ECR image {service}: commit tag does not match the release digest")
    # This reaches the API server using the chosen context, rather than only testing AWS IAM.
    run(kube + ["--namespace", args.environment, "get", "deployments", "-o", "name"], "Kubernetes API access")
    print(f"Preflight passed: account, four ECR images, active EKS, context and {args.environment} API access")
    print("Helm write permissions, runtime Secrets and application health still require deployment verification")


if __name__ == "__main__":
    os.environ["AWS_EC2_METADATA_DISABLED"] = "true"
    try:
        main()
    except (ValueError, KeyError, OSError) as error:
        # Malformed external JSON may contain arbitrary data; do not print its contents.
        message = "Unexpected command response" if isinstance(error, (json.JSONDecodeError, KeyError)) else str(error)
        print(f"Deployment preflight stopped: {message}", file=sys.stderr)
        sys.exit(1)
