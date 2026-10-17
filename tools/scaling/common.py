"""Read-only Kubernetes helpers; reports deliberately omit Pod env/Secret values."""
import json
import math
import re
import subprocess
from datetime import datetime, timezone
from decimal import Decimal


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def quantity(value):
    """Kubernetes quantity in base units: CPU cores or memory bytes."""
    match = re.fullmatch(r"([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)([a-zA-Z]*)", str(value))
    if not match:
        raise ValueError(f"Unsupported resource quantity: {value}")
    suffixes = {"": 1, "n": Decimal("1e-9"), "u": Decimal("1e-6"), "m": Decimal("1e-3"),
                "k": 10**3, "K": 10**3, "M": 10**6, "G": 10**9, "T": 10**12,
                "P": 10**15, "E": 10**18,
                **{s + "i": 1024**i for i, s in enumerate("KMGTPE", 1)}}
    if match[2] not in suffixes:
        raise ValueError(f"Unsupported resource suffix: {match[2]}")
    return float(Decimal(match[1]) * Decimal(suffixes[match[2]]))


def resources(requests):
    return {"cpu_m": quantity(requests.get("cpu", 0)) * 1000,
            "memory_mib": quantity(requests.get("memory", 0)) / 1024**2}


def add(*items):
    return {key: sum(item[key] for item in items) for key in ("cpu_m", "memory_mib")}


def zero():
    return {"cpu_m": 0, "memory_mib": 0}


def pod_requests(spec):
    # Kubernetes sidecar init containers remain running during later init/app phases.
    app = add(zero(), *(resources(c.get("resources", {}).get("requests", {}))
                        for c in spec.get("containers", [])))
    sidecars, peak = zero(), zero()
    for container in spec.get("initContainers", []):
        request = resources(container.get("resources", {}).get("requests", {}))
        phase = add(sidecars, request)
        peak = {key: max(peak[key], phase[key]) for key in peak}
        if container.get("restartPolicy") == "Always":
            sidecars = phase
    running = add(app, sidecars)
    effective = {key: max(running[key], peak[key]) for key in peak}
    # Pod-level resource requests override the per-container calculation when present.
    for key, field in (("cpu_m", "cpu"), ("memory_mib", "memory")):
        request = spec.get("resources", {}).get("requests", {})
        if field in request:
            effective[key] = resources({field: request[field]})[key]
    return add(effective, resources(spec.get("overhead", {})))


def pod_summary(pod):
    meta, spec, status = pod["metadata"], pod.get("spec", {}), pod.get("status", {})
    return {"name": meta["name"], "namespace": meta.get("namespace", "default"),
            "node": spec.get("nodeName"), "phase": status.get("phase", "Unknown"),
            "terminating": bool(meta.get("deletionTimestamp")),
            "ready": any(c["type"] == "Ready" and c["status"] == "True"
                         for c in status.get("conditions", [])),
            "owner_kinds": [o["kind"] for o in meta.get("ownerReferences", [])],
            "requests": pod_requests(spec),
            "images": [c["image"] for c in spec.get("containers", [])],
            "restarts": sum(c.get("restartCount", 0) for c in status.get("containerStatuses", []))}


def node_summary(node):
    return {"name": node["metadata"]["name"],
            "zone": node["metadata"].get("labels", {}).get("topology.kubernetes.io/zone", "unknown"),
            "ready": any(c["type"] == "Ready" and c["status"] == "True"
                         for c in node.get("status", {}).get("conditions", [])),
            "unschedulable": node.get("spec", {}).get("unschedulable", False),
            "taints": node.get("spec", {}).get("taints", []),
            "allocatable": resources(node["status"]["allocatable"]),
            "pod_slots": int(node["status"]["allocatable"].get("pods", 0))}


def deployment_summary(deployment):
    return {"name": deployment["metadata"]["name"],
            "uid": deployment["metadata"]["uid"],
            "generation": deployment["metadata"].get("generation"),
            "revision": deployment["metadata"].get("annotations", {}).get("deployment.kubernetes.io/revision"),
            "observed_generation": deployment.get("status", {}).get("observedGeneration"),
            "desired": deployment["spec"].get("replicas", 1),
            "ready": deployment.get("status", {}).get("readyReplicas", 0),
            "updated": deployment.get("status", {}).get("updatedReplicas", 0),
            "images": [c["image"] for c in deployment["spec"]["template"]["spec"]["containers"]]}


def kubectl(context, *args):
    try:
        result = subprocess.run(["kubectl", "--context", context, "--request-timeout=15s", *args],
                                capture_output=True, text=True, timeout=20)
    except subprocess.TimeoutExpired:
        raise RuntimeError("Kubernetes read timed out; check authentication/network") from None
    if result.returncode:
        # Do not echo command stderr: authentication plugins may include sensitive output.
        raise RuntimeError(f"kubectl read failed ({args[0]}, exit {result.returncode}); check login/RBAC/network")
    return json.loads(result.stdout)


def percentile(values, fraction):
    return sorted(values)[math.ceil(len(values) * fraction) - 1] if values else None
