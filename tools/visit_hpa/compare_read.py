"""Offline A0/A1 client-evidence checks; never proves HPA or cloud performance."""
import argparse
import csv
import datetime as dt
import json
import math
from pathlib import Path
import re


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def _integer(value):
    return type(value) is int and value >= 0


def _arm(directory):
    root = Path(directory)
    issues = []
    try:
        manifest = read_json(root / "manifest.json")
        summary = read_json(root / "summary.json")
        cleanup = read_json(root / "worker-cleanup.json")
        if (not isinstance(manifest.get("comparisonInputs"), dict)
                or not isinstance(manifest.get("targetOrigins"), list) or len(manifest["targetOrigins"]) != 2
                or not isinstance(manifest.get("targets"), dict)
                or type(manifest["targets"].get("minSamplesPerEndpoint")) is not int
                or manifest["targets"]["minSamplesPerEndpoint"] < 100):
            raise ValueError("incomplete_manifest")
        requests = [json.loads(line) for line in (root / "requests.jsonl").read_text(encoding="utf-8").splitlines()]
        iterations = [json.loads(line) for line in (root / "iterations.jsonl").read_text(encoding="utf-8").splitlines()]
        with (root / "phases.csv").open(encoding="utf-8", newline="") as stream:
            markers = list(csv.DictReader(stream))
        if manifest["scenario"] != "work-pack" or summary["scenario"] != "work-pack":
            issues.append("requires_A_work_pack")
        if summary["runId"] != manifest["runId"]:
            issues.append("run_id_mismatch")
        if summary["outcome"] not in ("COMPLETED", "COMPLETED_WITH_FAILURES"):
            issues.append("run_not_complete")
        if cleanup.get("complete") is not True or summary.get("workerCleanupComplete") is not True:
            issues.append("cleanup_incomplete")
        if not cleanup.get("workers") or not all(w.get("reaped") is True and w.get("readerJoined") is True
                                                and w.get("writerJoined") is True for w in cleanup["workers"]):
            issues.append("cleanup_rows_missing_or_incomplete")
        if summary.get("accountingValid") is not True:
            issues.append("accounting_invalid")
        planned = manifest["plan"]["plannedIterations"]
        if (not all(_integer(summary[k]) for k in ("plannedIterations", "launchedIterations", "droppedIterations",
                                                   "notScheduledAfterStop", "failedIterations"))
                or summary["plannedIterations"] != planned or summary["launchedIterations"] != planned
                or summary["droppedIterations"] != 0 or summary["notScheduledAfterStop"] != 0):
            issues.append("load_incomplete_or_dropped")
        if summary.get("clientEvidenceStatus") != "COMPLETE_CLIENT_SAMPLES":
            issues.append("samples_or_client_evidence_incomplete")
        if len(iterations) != summary["launchedIterations"] or sum(r.get("passed") is not True for r in iterations) != summary["failedIterations"]:
            issues.append("iteration_rows_missing_or_inconsistent")
        setup = [r for r in requests if r["phase"] == "setup"]
        workload = [r for r in requests if r["phase"] != "setup"]
        if (len(setup) != summary["httpRequestsDuringSetup"] or len(setup) != 5 * manifest["maxVUs"]
                or not all(r.get("businessPassed") is True for r in setup)):
            issues.append("setup_rows_missing_or_failed")
        if len(workload) != planned or len(workload) != summary["httpRequestsDuringLoad"]:
            issues.append("request_rows_missing_or_inconsistent")
        if any(r.get("businessPassed") is True and r.get("httpPassed") is not True for r in requests):
            issues.append("business_success_without_http_success")
        if any(r["method"] != "GET" or r["endpoint"] != "/api/visits/{id}/work-pack" for r in workload):
            issues.append("unexpected_workload_operation")
        if sum(r.get("businessPassed") is not True for r in workload) != summary["failedIterations"]:
            issues.append("business_failure_count_mismatch")
        # These categories are stop-now contract failures, never acceptable throughput.
        allowed_errors = {"transport_error", "server_http"}
        if any(r.get("error") not in allowed_errors for r in requests if "error" in r):
            issues.append("fatal_contract_or_cancelled_request")
        metric_phases = summary["phases"]
        if [p["phase"] for p in metric_phases] != [p["name"] for p in manifest["phases"]]:
            issues.append("phase_list_mismatch")
        for phase, metric in zip(manifest["phases"], metric_phases):
            req = [r for r in workload if r["phase"] == phase["name"]]
            it = [r for r in iterations if r["phase"] == phase["name"]]
            if phase["rps"] == 0:
                if req or it or metric.get("clientEvidenceStatus") != "NOT_APPLICABLE":
                    issues.append("cooldown_not_empty")
                continue
            if (len(req) != phase["iterations"] or len(it) != phase["iterations"]
                    or metric.get("clientEvidenceStatus") != "COMPLETE_CLIENT_SAMPLES"
                    or metric["launchedIterations"] != len(it)
                    or metric["failedIterations"] != sum(r.get("passed") is not True for r in it)):
                issues.append("phase_samples_missing")
            if (type(metric.get("iterationP95Ms")) not in (int, float)
                    or not math.isfinite(metric["iterationP95Ms"]) or metric["iterationP95Ms"] < 0):
                issues.append("iteration_metrics_missing")
            endpoints = metric["endpoints"]
            if len(endpoints) != 1:
                issues.append("endpoint_metrics_missing")
                continue
            endpoint = endpoints[0]
            if (endpoint["method"] != "GET" or endpoint["endpoint"] != "/api/visits/{id}/work-pack"
                    or endpoint["samples"] != len(req)
                    or endpoint["samples"] < manifest["targets"]["minSamplesPerEndpoint"]
                    or endpoint["businessFailures"] != sum(r.get("businessPassed") is not True for r in req)
                    or endpoint["httpFailures"] != sum(r.get("httpPassed") is not True for r in req)
                    or type(endpoint["allP95Ms"]) not in (int, float)
                    or not math.isfinite(endpoint["allP95Ms"]) or endpoint["allP95Ms"] < 0
                    or endpoint.get("targetObservation") not in ("WITHIN_DRAFT_TARGET", "OUTSIDE_DRAFT_TARGET")
                    or (any(r.get("businessPassed") is True for r in req)
                        and (type(endpoint.get("successP95Ms")) not in (int, float)
                             or not math.isfinite(endpoint["successP95Ms"]) or endpoint["successP95Ms"] < 0))):
                issues.append("endpoint_counts_or_metrics_invalid")
        marker_names = {m["phase"] for m in markers}
        if any(p["name"] + suffix not in marker_names for p in manifest["phases"] for suffix in ("-start", "-end")):
            issues.append("phase_markers_missing")
        if not markers or any(dt.datetime.fromisoformat(m["timestamp"]).utcoffset() != dt.timedelta(0) for m in markers):
            issues.append("utc_markers_missing")
        return manifest, summary, sorted(set(issues))
    except (OSError, ValueError, KeyError, TypeError, AttributeError):
        return {}, {}, ["missing_or_malformed_artifact"]


def _context_issues(context):
    try:
        if context["schemaVersion"] != 1:
            return ["context_schema"]
        a, b = context["arms"]["A0"], context["arms"]["A1"]
        required = ("coreDigest", "visitDigest", "nodesRef", "resourcesRef", "sessionCacheRef",
                    "readRoutingRef", "warmupRef", "fixtureShapeRef")
        for arm in (a, b):
            for key in required:
                value = arm["conditions"][key]
                if not isinstance(value, str) or not value.strip() or value.upper() in ("UNCONFIRMED", "UNKNOWN", "TODO"):
                    return ["conditions_unconfirmed"]
                if key.endswith("Digest") and not re.fullmatch(r"sha256:[0-9a-f]{64}", value):
                    return ["image_digest_unconfirmed"]
        if a["conditions"] != b["conditions"]:
            return ["declared_conditions_differ"]
        if a["replicaControl"] != "fixed-2" or b["replicaControl"] != "hpa-reviewed":
            return ["replica_control_incorrect"]
        review = context["review"]
        flags = ("fixturesFrozen", "noConcurrentRelease", "backgroundReviewed", "generatorHeadroomReviewed",
                 "resourceMetricsComplete")
        if not all(review.get(k) is True for k in flags) or not review.get("owner") or not review.get("resourceEvidenceRef"):
            return ["resource_review_incomplete"]
        return []
    except (KeyError, TypeError, AttributeError):
        return ["missing_or_malformed_context"]


def compare(a0, a1, context=None):
    m0, s0, errors0 = _arm(a0)
    m1, s1, errors1 = _arm(a1)
    differences = []
    for key in ("codeBaseline", "toolSha256", "targetOrigins", "maxVUs", "maxLaunchLagMs", "targets", "stop"):
        if key not in m0 or m0.get(key) != m1.get(key):
            differences.append(key)
    for key in ("codeBaseline", "toolSha256"):
        size = 40 if key == "codeBaseline" else 64
        if not isinstance(m0.get(key), str) or not re.fullmatch("[0-9a-f]{" + str(size) + "}", m0[key]):
            differences.append(key + "_unconfirmed")
    for key in ("fixtureSha256", "workloadSha256"):
        value = m0.get("comparisonInputs", {}).get(key)
        if not isinstance(value, str) or not re.fullmatch("[0-9a-f]{64}", value) or value != m1.get("comparisonInputs", {}).get(key):
            differences.append(key)
    if not m0.get("runId") or m0.get("runId") == m1.get("runId"):
        differences.append("run_ids_not_independent")
    context_issues = _context_issues(context) if context is not None else ["resource_context_not_supplied"]
    manifests_available = bool(m0 and m1)
    if not manifests_available:
        differences = []  # Missing files are evidence gaps, not invented version differences.
    client_match = not (errors0 or errors1 or differences)
    incompatible_context = any(x in context_issues for x in ("declared_conditions_differ", "replica_control_incorrect"))
    status = ("INSUFFICIENT_CLIENT_EVIDENCE" if not manifests_available else
              "NOT_COMPARABLE" if differences or incompatible_context else "INSUFFICIENT_CLIENT_EVIDENCE" if errors0 or errors1
              else "AWAITING_RESOURCE_REVIEW" if context_issues else "CLIENT_AND_DECLARED_CONDITIONS_MATCH")
    def metrics(summary):
        return [{"phase": p["phase"], "iterationP95Ms": p.get("iterationP95Ms"),
                 "iterationTargetObservation": p.get("iterationTargetObservation"),
                 "endpoints": p.get("endpoints", [])} for p in summary.get("phases", [])]
    return {"schemaVersion": 1, "status": status, "clientComparable": client_match,
            "clientIssues": {"A0": errors0, "A1": errors1}, "differences": sorted(set(differences)),
            "resourceReviewIssues": context_issues, "resourceConditionsAreOperatorDeclarations": True,
            "candidateMetrics": {"A0": metrics(s0), "A1": metrics(s1)},
            "formalAcceptance": False, "hpaMechanism": "NOT_EVALUATED", "performanceBenefit": "NOT_EVALUATED",
            "limitations": "Offline evidence consistency plus operator declarations; no cloud reads, scaling proof or performance/SLA approval."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--a0", required=True)
    parser.add_argument("--a1", required=True)
    parser.add_argument("--context", help="Private completed context; no credentials")
    parser.add_argument("--output", required=True, help="New report file; never overwrite prior evidence")
    args = parser.parse_args()
    try:
        report = compare(args.a0, args.a1, read_json(args.context) if args.context else None)
        with Path(args.output).open("x", encoding="utf-8") as stream:
            json.dump(report, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
        print(report["status"])
        return 0 if report["status"] == "CLIENT_AND_DECLARED_CONDITIONS_MATCH" else 2
    except (OSError, ValueError, KeyError, TypeError):
        print("Comparison rejected: check private input artifacts/context and a new output file.")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
