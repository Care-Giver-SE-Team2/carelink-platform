#!/usr/bin/env python3
"""Sample deployment/HPA/Pod metrics using read-only Kubernetes GET requests."""
import argparse
import csv
import json
import time
from pathlib import Path

from common import deployment_summary, kubectl, pod_summary, resources, utc_now


def snapshot(context, namespace, name):
    deployment = kubectl(context, "get", "deployment", name, "-n", namespace, "-o", "json")
    selector = deployment["spec"]["selector"]
    if selector.get("matchExpressions"):
        raise ValueError("This collector requires a matchLabels deployment selector")
    labels = ",".join(f"{key}={value}" for key, value in sorted(selector["matchLabels"].items()))
    raw = kubectl(context, "get", "pods", "-n", namespace, "-l", labels, "-o", "json")
    hpas = kubectl(context, "get", "hpa", "-n", namespace, "-o", "json")
    matching = [h for h in hpas["items"] if h["spec"]["scaleTargetRef"].get("kind") == "Deployment"
                and h["spec"]["scaleTargetRef"]["name"] == name]
    metrics_error = None
    try:
        response = kubectl(context, "get", "--raw", f"/apis/metrics.k8s.io/v1beta1/namespaces/{namespace}/pods")
        metrics = {m["metadata"]["name"]: m for m in response["items"]}
    except (RuntimeError, OSError, ValueError):
        metrics, metrics_error = {}, "Pod Metrics API unavailable (login, RBAC, metrics-server or network)"
    pods = []
    for pod in raw["items"]:
        entry = pod_summary(pod)
        metric = metrics.get(entry["name"])
        usage = None
        if metric:
            readings = [resources(c["usage"]) for c in metric["containers"]]
            usage = {key: sum(r[key] for r in readings) for key in ("cpu_m", "memory_mib")}
        entry.update({"usage": usage, "metrics_timestamp": metric.get("timestamp") if metric else None,
                      "metrics_window": metric.get("window") if metric else None,
                      "scheduling_reason": next((c.get("reason") for c in pod.get("status", {}).get("conditions", [])
                                                 if c["type"] == "PodScheduled" and c["status"] != "True"), None)})
        pods.append(entry)
    hpa = [{"name": h["metadata"]["name"], "min": h["spec"].get("minReplicas", 1),
            "max": h["spec"]["maxReplicas"], "metrics_spec": h["spec"].get("metrics", []),
            "status": h.get("status", {})} for h in matching]
    return {"timestamp": utc_now(), "deployment": deployment_summary(deployment), "pods": pods,
            "hpa": hpa, "metrics_error": metrics_error,
            "warnings": ["Multiple HPAs target the deployment"] if len(matching) > 1 else []}


def csv_row(sample):
    pods = sample.get("pods", [])
    active = [p for p in pods if p["phase"] not in ("Succeeded", "Failed") and not p["terminating"]]
    known = [p for p in active if p["usage"] is not None]
    request_cpu = sum(p["requests"]["cpu_m"] for p in known)
    cpu = sum(p["usage"]["cpu_m"] for p in known)
    deployment = sample.get("deployment", {})
    return {"timestamp": sample["timestamp"], "desired": deployment.get("desired", ""),
            "ready": deployment.get("ready", ""), "updated": deployment.get("updated", ""),
            "pending": sum(p["phase"] == "Pending" for p in active),
            "pods_with_metrics": len(known), "active_pods": len(active),
            "cpu_m_known_pods": round(cpu, 3) if known else "",
            "memory_mib_known_pods": round(sum(p["usage"]["memory_mib"] for p in known), 3) if known else "",
            "observed_cpu_request_percent": round(cpu / request_cpu * 100, 2) if request_cpu else "",
            "error": sample.get("error") or sample.get("metrics_error") or ""}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--context", required=True)
    parser.add_argument("--namespace", default="staging")
    parser.add_argument("--deployment", default="visit")
    parser.add_argument("--duration", type=int, default=1200, help="Seconds; finite local sampling, no cloud schedule")
    parser.add_argument("--interval", type=float, default=10)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    if args.interval < 5 or args.duration < args.interval:
        parser.error("interval >= 5 seconds and duration >= interval required")
    if args.output_dir.exists() and any(args.output_dir.iterdir()):
        parser.error("Use a new empty output directory to preserve previous evidence")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    metadata = {"started_at": utc_now(), "context": args.context, "namespace": args.namespace,
                "deployment": args.deployment, "duration_seconds": args.duration,
                "interval_seconds": args.interval,
                "meaning": "Known-Pod CPU/request is an observation, not the HPA controller algorithm. "
                           "HPA status/conditions and metric timestamps are retained in samples.jsonl."}
    (args.output_dir / "run.json").write_text(json.dumps(metadata, indent=2) + "\n")
    started, end = time.monotonic(), time.monotonic() + args.duration
    first, errors, skipped, count = None, 0, 0, 0
    with (args.output_dir / "samples.jsonl").open("w") as raw, (args.output_dir / "metrics.csv").open("w", newline="") as csvfile:
        writer = None
        try:
            due = started
            while time.monotonic() < end:
                time.sleep(max(0, due - time.monotonic()))
                if time.monotonic() >= end:
                    break
                try:
                    sample = snapshot(args.context, args.namespace, args.deployment)
                    current = {k: sample["deployment"][k] for k in ("uid", "images", "revision")}
                    if first is None:
                        first = current
                    sample["release_changed"] = current != first
                except (RuntimeError, OSError, ValueError, KeyError) as error:
                    sample = {"timestamp": utc_now(), "error": f"Incomplete sample: {type(error).__name__}; check login/RBAC/network"}
                errors += bool(sample.get("error") or sample.get("metrics_error"))
                raw.write(json.dumps(sample) + "\n")
                raw.flush()
                row = csv_row(sample)
                if writer is None:
                    writer = csv.DictWriter(csvfile, fieldnames=row.keys())
                    writer.writeheader()
                writer.writerow(row)
                csvfile.flush()
                count += 1
                print(f'{sample["timestamp"]} desired={row["desired"]} ready={row["ready"]} pending={row["pending"]} metrics={row["pods_with_metrics"]}/{row["active_pods"]} {row["error"]}', flush=True)
                due += args.interval
                while due < min(time.monotonic(), end):
                    skipped += 1
                    due += args.interval
        except KeyboardInterrupt:
            metadata["operator_stopped"] = True
    metadata.update({"finished_at": utc_now(), "samples": count, "samples_with_errors": errors,
                     "skipped_sample_slots": skipped})
    (args.output_dir / "run.json").write_text(json.dumps(metadata, indent=2) + "\n")
    return 0 if count and not errors else 2


if __name__ == "__main__":
    raise SystemExit(main())
