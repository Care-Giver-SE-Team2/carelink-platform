#!/usr/bin/env python3
"""Read-only AWS/Kubernetes inventory. Does not install, apply, publish or alter budgets."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import tempfile


def request(command, env):
    try:
        result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=45)
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"status": "unverified", "error": str(error)}
    if result.returncode:
        error = result.stderr.strip()
        # A failed/forbidden read is not evidence of absence.
        status = "not_found" if "(NotFound)" in error else "unverified"
        return {"status": status, "error": error}
    try:
        return {"status": "observed", "data": json.loads(result.stdout)}
    except json.JSONDecodeError:
        return {"status": "unverified", "error": "Command did not return JSON."}


def subscriber_summary(result):
    if result["status"] != "observed":
        return result
    subscribers = result["data"].get("Subscribers", [])
    # Do not put recipient addresses or SNS endpoint ARNs into a shareable report.
    return {"status": "observed", "count": len(subscribers),
            "types": sorted({item.get("SubscriptionType", "UNKNOWN") for item in subscribers})}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--account-id", required=True)
    parser.add_argument("--cluster", default="carelink-runtime")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not (args.account_id.isdigit() and len(args.account_id) == 12):
        parser.error("--account-id must contain 12 digits")
    # Retain the caller's config paths and PATH, but never inherit credential overrides.
    env = {key: value for key, value in os.environ.items()
           if not key.startswith("AWS_") or key in {"AWS_CONFIG_FILE", "AWS_SHARED_CREDENTIALS_FILE"}}
    env.update(AWS_PROFILE="carelink", AWS_PAGER="")
    aws = ["aws", "--profile", "carelink", "--no-cli-pager", "--output", "json"]
    report = {"captured_at_utc": datetime.now(timezone.utc).isoformat(),
              "scope": "read-only; no cloud or cluster writes", "cluster": args.cluster}
    identity = request([*aws, "sts", "get-caller-identity"], env)
    report["identity"] = identity
    if identity["status"] == "observed":
        caller = identity["data"]
        if caller["Account"] != args.account_id or caller["Arn"].endswith(":root"):
            raise SystemExit("Wrong account or root identity; no further reads performed.")
        budgets = request([*aws, "--region", "us-east-1", "budgets", "describe-budgets",
                           "--account-id", args.account_id], env)
        report["budgets"] = budgets
        if budgets["status"] == "observed":
            items = []
            for budget in budgets["data"].get("Budgets", []):
                name = budget["BudgetName"]
                notifications = request([*aws, "--region", "us-east-1", "budgets", "describe-notifications-for-budget",
                                         "--account-id", args.account_id, "--budget-name", name], env)
                entries = []
                if notifications["status"] == "observed":
                    for notification in notifications["data"].get("Notifications", []):
                        subscribers = request([*aws, "--region", "us-east-1", "budgets", "describe-subscribers-for-notification",
                                               "--account-id", args.account_id, "--budget-name", name,
                                               "--notification", json.dumps(notification)], env)
                        entries.append({"notification": notification, "subscribers": subscriber_summary(subscribers)})
                items.append({"name": name, "limit": budget.get("BudgetLimit"),
                              "time_unit": budget.get("TimeUnit"), "type": budget.get("BudgetType"),
                              "notifications_read": notifications["status"], "notifications": entries,
                              "notification_error": notifications.get("error"),
                              "actual_notification_receipt": "not verified by configuration inventory"})
            report["budgets"] = {"status": "observed", "items": items}
        report["eks_addons"] = request([*aws, "--region", "ap-southeast-1", "eks", "list-addons",
                                         "--cluster-name", args.cluster], env)
        # --dry-run writes only our private temporary kubeconfig, never ~/.kube/config.
        with tempfile.TemporaryDirectory(prefix="carelink-readiness-") as temporary:
            try:
                result = subprocess.run([*aws, "--region", "ap-southeast-1", "eks", "update-kubeconfig",
                                         "--name", args.cluster, "--alias", args.cluster, "--dry-run"],
                                        env=env, capture_output=True, text=True, timeout=45)
            except (OSError, subprocess.TimeoutExpired) as error:
                # Partial process output can contain kubeconfig; record only the failure type.
                report["kubernetes"] = {"status": "unverified",
                                        "error": f"Local kubeconfig generation failed ({type(error).__name__}); command output suppressed."}
            else:
                if result.returncode:
                    report["kubernetes"] = {"status": "unverified", "error": result.stderr.strip()}
                else:
                    config = Path(temporary) / "kubeconfig"
                    config.write_text(result.stdout)
                    config.chmod(0o600)
                    kubectl = ["kubectl", "--kubeconfig", str(config), "--context", args.cluster, "--request-timeout=20s"]
                    queries = {
                        "metrics_server": ["-n", "kube-system", "get", "deployment", "metrics-server", "-o", "json"],
                        "metrics_api": ["get", "apiservice", "v1beta1.metrics.k8s.io", "-o", "json"],
                        "node_metrics": ["get", "--raw=/apis/metrics.k8s.io/v1beta1/nodes"],
                        "staging_pod_metrics": ["get", "--raw=/apis/metrics.k8s.io/v1beta1/namespaces/staging/pods"],
                        "deployments": ["get", "deployments", "-A", "-o", "json"],
                        "hpa": ["get", "hpa", "-A", "-o", "json"],
                        "external_metrics_api": ["get", "apiservice", "v1beta1.external.metrics.k8s.io", "-o", "json"],
                    }
                    for name in ["scaledobjects", "scaledjobs", "triggerauthentications", "clustertriggerauthentications"]:
                        queries[name] = ["get", "crd", f"{name}.keda.sh", "-o", "json"]
                    report["kubernetes"] = {}
                    for name, query in queries.items():
                        value = request([*kubectl, *query], env)
                        if value["status"] == "observed" and name in {"metrics_server", "deployments"}:
                            data = value["data"]
                            deployments = data.get("items", [data])
                            value["data"] = [{"namespace": d["metadata"].get("namespace"), "name": d["metadata"]["name"],
                                              "desired": d.get("spec", {}).get("replicas"),
                                              "ready": d.get("status", {}).get("readyReplicas", 0),
                                              "images": [c["image"] for c in d["spec"]["template"]["spec"]["containers"]]}
                                             for d in deployments]
                        report["kubernetes"][name] = value
    args.output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with os.fdopen(os.open(args.output, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w") as target:
        json.dump(report, target, indent=2)
        target.write("\n")
    args.output.chmod(0o600)
    print(f"Saved read-only inventory to {args.output}. Unverified reads require follow-up; no resources were changed.")


if __name__ == "__main__":
    main()
