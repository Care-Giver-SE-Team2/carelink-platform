#!/usr/bin/env python3
"""Request-budget screening, never a scheduler simulation or peak-load guarantee."""
import argparse
import json
from pathlib import Path

from common import add, deployment_summary, kubectl, node_summary, pod_requests, pod_summary, utc_now, zero


def assess(nodes, pods, deployment, max_replicas, reserve_pods):
    nodes = [node_summary(n) for n in nodes["items"]]
    pods = [pod_summary(p) for p in pods["items"] if p.get("status", {}).get("phase") not in ("Succeeded", "Failed")]
    target = pod_requests(deployment["spec"]["template"]["spec"])
    desired = deployment["spec"].get("replicas", 1)
    extra = max(0, max_replicas - desired) + reserve_pods
    extra_request = {k: v * extra for k, v in target.items()}
    pending = [p for p in pods if not p["node"]]
    for node in nodes:
        assigned = [p for p in pods if p["node"] == node["name"]]
        node["requested"] = add(zero(), *(p["requests"] for p in assigned))
        node["free_requests"] = {k: node["allocatable"][k] - node["requested"][k] for k in target}
        node["assigned_pods"] = len(assigned)
    eligible = [n for n in nodes if n["ready"] and not n["unschedulable"]]
    capacity = add(zero(), *(n["allocatable"] for n in eligible))
    # Include requests for unscheduled Pods; avoid treating Pending demand as free space.
    demand = add(zero(), *(p["requests"] for p in pods), extra_request)
    fits = lambda cap, req: all(cap[k] >= req[k] for k in target)
    demand_slots = len(pods) + extra
    slots_fit = sum(n["pod_slots"] for n in eligible) >= demand_slots
    n_minus_one = []
    for lost in eligible:
        remaining = [n for n in eligible if n["name"] != lost["name"]]
        # DaemonSet Pods on the lost node do not need to migrate to surviving nodes.
        migrate = [p for p in pods if not (p["node"] == lost["name"] and "DaemonSet" in p["owner_kinds"])]
        after = add(zero(), *(p["requests"] for p in migrate), extra_request)
        available = add(zero(), *(n["allocatable"] for n in remaining))
        n_minus_one.append({"lost_node": lost["name"], "lost_zone": lost["zone"],
                            "remaining_allocatable": available, "projected_requests": after,
                            "aggregate_budget_fits": bool(remaining) and fits(available, after)
                            and sum(n["pod_slots"] for n in remaining) >= len(migrate) + extra})
    warnings = []
    if pending:
        warnings.append("Existing unscheduled Pods: resolve before load testing")
    if len(eligible) != len(nodes):
        warnings.append("Some nodes are unready or cordoned; investigate before testing")
    if any(n["taints"] for n in eligible):
        warnings.append("Node taints present; aggregate capacity is not proof the target can schedule")
    if deployment.get("status", {}).get("readyReplicas", 0) != desired:
        warnings.append("Target is not fully ready")
    if any(c.get("resources", {}).get("requests", {}).get("cpu") is None
           for c in deployment["spec"]["template"]["spec"]["containers"]):
        warnings.append("Target has a container without a CPU request; CPU HPA may not calculate utilization")
    if any(not result["aggregate_budget_fits"] for result in n_minus_one):
        warnings.append("Proposed maximum plus reserve does not fit at least one single-node-loss budget")
    if any(c.get("maxSkew", 0) for c in deployment["spec"]["template"]["spec"].get("topologySpreadConstraints", [])):
        warnings.append("Topology constraints exist: inspect per-zone scheduling and Pending reasons during test")
    return {"assessed_at": utc_now(), "deployment": deployment_summary(deployment),
            "proposed_max_replicas": max_replicas, "additional_reserve_target_pods": reserve_pods,
            "target_pod_requests": target, "nodes": nodes, "pending_pods": pending,
            "cluster_allocatable": capacity, "projected_requests": demand,
            "aggregate_budget_fits": bool(eligible) and fits(capacity, demand) and slots_fit,
            "single_node_loss": n_minus_one, "warnings": warnings,
            "limits": "CPU/memory/Pod-slot request screen only. No pass certifies scheduling, peak traffic, "
                      "IP availability, zone loss, ephemeral storage, limits/OOM safety or node autoscaling. "
                      "Reserve covers only the specified number of target-sized Pods; other releases must not overlap."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--context", help="Explicit existing kubeconfig context; only GET requests")
    source.add_argument("--snapshot-dir", type=Path, help="Offline nodes.json, pods.json, deployment.json")
    parser.add_argument("--namespace", default="staging")
    parser.add_argument("--deployment", default="visit")
    parser.add_argument("--max-replicas", type=int, default=4)
    parser.add_argument("--reserve-pods", type=int, default=1)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if args.max_replicas < 2 or args.reserve_pods < 0:
        parser.error("max replicas must be >= 2; reserve pods must be >= 0")
    try:
        if args.snapshot_dir:
            nodes, pods, deployment = [json.loads((args.snapshot_dir / name).read_text())
                                       for name in ("nodes.json", "pods.json", "deployment.json")]
        else:
            nodes = kubectl(args.context, "get", "nodes", "-o", "json")
            pods = kubectl(args.context, "get", "pods", "--all-namespaces", "-o", "json")
            deployment = kubectl(args.context, "get", "deployment", args.deployment,
                                  "--namespace", args.namespace, "-o", "json")
        report = assess(nodes, pods, deployment, args.max_replicas, args.reserve_pods)
        report["input_mode"] = "offline_snapshot" if args.snapshot_dir else "live_get"
    except (RuntimeError, OSError, ValueError, KeyError) as error:
        parser.exit(2, f"Capacity check incomplete: {type(error).__name__}; check inputs/login/RBAC. No pass recorded.\n")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({key: report[key] for key in ("aggregate_budget_fits", "warnings")}, indent=2))
    print("Budget screening only; review the complete report before enabling any test.")
    return 0 if report["aggregate_budget_fits"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
