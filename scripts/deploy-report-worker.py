#!/usr/bin/env python3
"""Opt-in staging worker deploy: reuse report digest, separate queue Secret, no infrastructure creation."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import runpy
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
DATA_KEYS = {"DB_URL", "DB_USER", "DB_PASSWORD", "SPRING_DATA_REDIS_HOST", "SPRING_DATA_REDIS_PORT",
             "SPRING_DATA_REDIS_PASSWORD", "SPRING_DATA_REDIS_SSL_ENABLED"}


def configuration(environment):
    if environment == "demo":
        return {"enabled": False}
    path = ROOT / "deploy/report-worker/staging.json"
    data = json.loads(path.read_text())
    if set(data) != {"enabled", "scalingEnabled", "scalingPaused", "maxReplicas"}:
        raise ValueError("Unexpected worker configuration fields")
    if any(type(data[k]) is not bool for k in ("enabled", "scalingEnabled", "scalingPaused")):
        raise ValueError("Worker switches must be booleans")
    if type(data["maxReplicas"]) is not int or data["maxReplicas"] not in (1, 2):
        raise ValueError("Worker trial limit is one or two replicas")
    return data


def runtime_secret(original, queue):
    source = original.get("data", {})
    if not DATA_KEYS <= source.keys() or any(not source[k] for k in DATA_KEYS):
        raise ValueError("report-runtime is missing data configuration; values suppressed")
    data = {k: source[k] for k in DATA_KEYS}
    data["CARELINK_EVENTS_QUEUEURL"] = base64.b64encode(queue.encode()).decode()
    return {"apiVersion": "v1", "kind": "Secret", "type": "Opaque",
            "metadata": {"name": "report-worker-runtime", "namespace": "staging"}, "data": data}


def run(command, payload=None):
    try:
        result = subprocess.run(command, input=payload, text=True, capture_output=True, timeout=700)
    except (OSError, subprocess.TimeoutExpired):
        raise ValueError(f"{command[0]} failed or timed out; output suppressed") from None
    if result.returncode:
        # kubectl/Helm diagnostics can include Secret payloads.
        raise ValueError(f"{command[0]} failed; output suppressed to protect credentials")
    return result.stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("enabled", "render", "deploy"))
    parser.add_argument("environment", choices=("staging", "demo"))
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--registry")
    parser.add_argument("--context")
    parser.add_argument("--profile", choices=("carelink",))
    args = parser.parse_args()
    config = configuration(args.environment)
    if args.action == "enabled":
        print(str(config["enabled"]).lower())
        return
    if not config["enabled"] and args.action == "deploy":
        print("Report worker disabled; no cloud or cluster calls.")
        return
    if args.environment != "staging":
        raise ValueError("Worker is restricted to staging")
    if not args.registry or not re.fullmatch(r"\d{12}\.dkr\.ecr\.ap-southeast-1\.amazonaws\.com", args.registry):
        raise ValueError("Expected the Singapore ECR registry")
    if not args.manifest:
        raise ValueError("Select a complete release manifest")
    records = runpy.run_path(str(ROOT / "scripts/release-manifest.py"))["read_manifest"](args.manifest, args.registry)
    record = records["report"]
    account = args.registry.split(".")[0]
    queue = f"https://sqs.ap-southeast-1.amazonaws.com/{account}/carelink-staging-report-worker"
    values = {"image": {"repository": record["image"], "digest": record["digest"], "tag": record["tag"]},
              "queueURL": queue, "scaling": {"enabled": config["scalingEnabled"],
              "paused": config["scalingPaused"], "maxReplicas": config["maxReplicas"]}}
    with tempfile.TemporaryDirectory(prefix="carelink-worker-") as temporary:
        file = Path(temporary) / "values.json"
        file.write_text(json.dumps(values))
        file.chmod(0o600)
        release = ["report-worker", str(ROOT / "charts/carelink-report-worker"), "--namespace", "staging", "-f", str(file)]
        if args.action == "render":
            print(run(["helm", "template", *release]), end="")
            return
        if not args.context:
            raise ValueError("An explicit cluster context is required")
        if args.profile != "carelink" and not os.environ.get("CODEBUILD_BUILD_ID"):
            raise ValueError("Local deploy requires --profile carelink")
        check = [sys.executable, str(ROOT / "scripts/check-deployment.py"), "staging", "--manifest", str(args.manifest),
                 "--registry", args.registry, "--context", args.context]
        if args.profile:
            for key in list(os.environ):
                if key.startswith("AWS_") and key not in {"AWS_CONFIG_FILE", "AWS_SHARED_CREDENTIALS_FILE"}:
                    del os.environ[key]
            os.environ["AWS_PROFILE"] = args.profile
            check += ["--profile", args.profile]
        run(check)
        kube = ["kubectl", "--context", args.context, "--namespace", "staging", "--request-timeout=30s"]
        ready = json.loads(run([*kube, "get", "configmap", "report-worker-cloud-ready", "-o", "json"]))["data"]
        if ready.get("workerQueueURL") != queue or ready.get("cloudReady") != "true":
            raise ValueError("Worker infrastructure readiness gate has not been recorded")
        if config["scalingEnabled"] and ready.get("kedaReady") != "true":
            raise ValueError("KEDA readiness gate has not been recorded")
        original = json.loads(run([*kube, "get", "secret", "report-runtime", "-o", "json"]))
        payload = json.dumps(runtime_secret(original, queue))
        apply = [*kube, "apply", "--server-side", "--field-manager=carelink-report-worker", "-f", "-"]
        run(apply + ["--dry-run=server"], payload)
        run(apply, payload)
        # This dedicated chart creates no Service/Ingress and never sets replicas in KEDA mode.
        run(["helm", "upgrade", "--install", *release, "--kube-context", args.context, "--wait", "--timeout", "10m"])
        live = json.loads(run([*kube, "get", "deployment", "report-worker", "-o", "json"]))
        expected = record["image"] + "@" + record["digest"]
        if live["spec"]["template"]["spec"]["containers"][0]["image"] != expected:
            raise ValueError("Worker image does not match the complete report release")
        print("Worker deployed from the report release digest; runtime/scaling acceptance is separate.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        print(f"Worker deployment stopped: {error}", file=sys.stderr)
        sys.exit(1)
