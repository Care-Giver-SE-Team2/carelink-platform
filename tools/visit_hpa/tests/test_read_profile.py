"""D2/D5 offline models and loopback fixtures only. Synthetic pair files are NOT cloud evidence."""
import copy
import csv
from fractions import Fraction
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import random
import socket
import tempfile
import unittest
from unittest.mock import patch

import test_load as old

load, ROOT = old.load, old.ROOT
spec = importlib.util.spec_from_file_location("visit_compare", ROOT / "compare_read.py")
compare_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(compare_module)


def joint():
    return load.read_json(ROOT / "example.joint.json")


def context():
    conditions = {"coreDigest": "sha256:" + "1" * 64, "visitDigest": "sha256:" + "2" * 64,
                  "nodesRef": "synthetic-test-nodes", "resourcesRef": "synthetic-test-resources",
                  "sessionCacheRef": "synthetic-test-cache", "readRoutingRef": "synthetic-test-routing",
                  "warmupRef": "synthetic-test-warmup", "fixtureShapeRef": "synthetic-test-data"}
    return {"schemaVersion": 1,
            "arms": {"A0": {"conditions": copy.deepcopy(conditions), "replicaControl": "fixed-2"},
                     "A1": {"conditions": copy.deepcopy(conditions), "replicaControl": "hpa-reviewed"}},
            "review": {"owner": "SYNTHETIC UNIT TEST", "resourceEvidenceRef": "NO REAL RESOURCE EVIDENCE",
                       "fixturesFrozen": True, "noConcurrentRelease": True, "backgroundReviewed": True,
                       "generatorHeadroomReviewed": True, "resourceMetricsComplete": True}}


class BudgetTests(unittest.TestCase):
    def test_template_exact_stages_counts_and_default_refusal(self):
        c = joint()
        plan = load.validate(c)
        self.assertEqual([(p["rps"], p["durationSeconds"]) for p in c["phases"]],
                         [(1, 180), (2, 120), (4, 120), (5, 120), (5, 240), (0, 480)])
        self.assertEqual((c["scenario"], c["maxVUs"], c["maxIterations"]), ("work-pack", 8, 2700))
        self.assertEqual((plan["plannedIterations"], plan["readBudget"]["durationSeconds"]), (2700, 1260))
        self.assertFalse(c["approval"]["confirmed"])
        self.assertFalse(plan["readBudget"]["inputsReviewed"])
        self.assertTrue(c["coreBase"].endswith(".invalid"))
        self.assertEqual(c["actors"][0]["visits"][0]["expectedTaskCount"], 0)

    def test_budget_separates_business_setup_other_and_wire_shift(self):
        b = load.validate(joint())["readBudget"]
        self.assertEqual((b["plannedBusinessGets"], b["twoArmBusinessGets"], b["setupHttpRequests"],
                          b["setupWorkPackGets"], b["plannedHttpRequestsIncludingSetup"]), (2700, 5400, 40, 8, 2740))
        self.assertEqual((b["plannedBusinessPeakPerWindow"], b["nominalHeadroomBeforeSetup"],
                          b["headroomAfterSetupBeforeOtherTraffic"]), (1500, 500, 460))
        self.assertEqual((b["shiftedBusinessPeakPerWindow"], b["modeledRequestsBeforeOtherTraffic"],
                          b["modeledHeadroomBeforeOtherTraffic"]), (1552, 1592, 408))
        self.assertEqual((b["extraProbeRps"], b["automaticRetries"]), (0, 0))
        self.assertIsNone(b["modeledRemainingRequests"])

    def test_exact_half_open_rolling_windows_match_brute_force(self):
        randomizer = random.Random(73)
        for _ in range(30):
            times = sorted(Fraction(randomizer.randrange(50), 10) for _ in range(15))
            width = Fraction(randomizer.randrange(1, 30), 10)
            brute = max(sum(start <= t < start + width for t in times) for start in times)
            self.assertEqual(load.rolling_peak(times, width), brute)
        self.assertEqual(load.rolling_peak([Fraction(0), Fraction(300)], Fraction(300)), 1)

    def test_old_8rps_higher_vus_and_caps_are_rejected(self):
        for key, value in (("maxVUs", 9), ("maxIterations", 2701), ("scenario", "check-in"),
                           ("readProfile", "unknown-profile")):
            c = joint(); c[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                load.validate(c)
        c = joint(); c["phases"][3]["rps"] = 8
        with self.assertRaises(ValueError):
            load.validate(c)

    def test_waf_rule_and_other_request_budget_validation(self):
        for key, value in (("windowSeconds", 300.0), ("windowSeconds", 60), ("limitRequests", 10000),
                           ("otherRequestsPerWindow", True), ("otherRequestsPerWindow", -1),
                           ("sourceIpReviewed", "true")):
            c = joint(); c["wafBudget"][key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                load.validate(c)
        c = joint(); c["wafBudget"].update(otherRequestsPerWindow=408, sourceIpReviewed=True)
        with self.assertRaisesRegex(ValueError, "exhausted"):
            load.validate(c)
        c["wafBudget"]["otherRequestsPerWindow"] = 100
        self.assertEqual(load.validate(c)["readBudget"]["modeledRemainingRequests"], 308)

    def test_remote_profile_requires_review_before_dns_or_worker(self):
        c = joint()
        c["approval"].update(confirmed=True, environment="synthetic", windowUtc="synthetic",
                             deploymentRef="synthetic", allowedOrigins=[c["coreBase"]])
        with patch.object(socket, "getaddrinfo", side_effect=AssertionError("DNS")), \
                patch.object(load.subprocess, "Popen", side_effect=AssertionError("worker")):
            with self.assertRaisesRegex(ValueError, "waf_source"):
                load.validate(c, execute=True)
        c["wafBudget"].update(sourceIpReviewed=True, otherRequestsPerWindow=100)
        with patch.object(socket, "getaddrinfo", side_effect=AssertionError("DNS")):
            self.assertTrue(load.validate(c, execute=True)["remoteExecutionConfirmed"])

    def test_offline_profile_never_resolves_or_opens(self):
        with patch.object(socket, "getaddrinfo", side_effect=AssertionError("DNS")), \
                patch.object(load.subprocess, "Popen", side_effect=AssertionError("worker")), \
                patch.object(load.urllib.request.OpenerDirector, "open", side_effect=AssertionError("HTTP")):
            self.assertEqual(load.validate(joint())["plannedIterations"], 2700)


    def test_profile_cannot_lower_minimum_sample_floor(self):
        c = joint(); c["targets"]["minSamplesPerEndpoint"] = 1
        with self.assertRaisesRegex(ValueError, "100_samples"):
            load.validate(c)

    def test_optional_task_count_fixture_is_strict_integer(self):
        for value in (True, 0.0, -1, None):
            c = joint(); c["actors"][0]["visits"][0]["expectedTaskCount"] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                load.validate(c)

    def test_fingerprints_ignore_runid_but_detect_data_workload_and_threshold_drift(self):
        c = joint(); load.validate(c)
        original = load.comparison_inputs(c)
        c["runId"] = "another-run"
        self.assertEqual(load.comparison_inputs(c), original)
        for field in ("visitId", "elderId", "expectedVersion", "expectedTaskCount"):
            altered = copy.deepcopy(c); altered["actors"][0]["visits"][0][field] += 1
            self.assertNotEqual(load.comparison_inputs(altered)["fixtureSha256"], original["fixtureSha256"])
        for field, value in (("maxVUs", 7), ("socketTimeoutSeconds", 7), ("maxLaunchLagMs", 300)):
            altered = copy.deepcopy(c); altered[field] = value
            self.assertNotEqual(load.comparison_inputs(altered)["workloadSha256"], original["workloadSha256"])
        altered = copy.deepcopy(c); altered["targets"]["httpP95Ms"] += 1
        self.assertNotEqual(load.comparison_inputs(altered)["workloadSha256"], original["workloadSha256"])


class AssertionMetricTests(unittest.TestCase):
    def pack(self):
        return {"visit": {"id": 1, "elderId": 1, "status": "SCHEDULED", "version": 0},
                "tasks": [], "execution": {}}

    def fixture(self):
        return {"visitId": 1, "elderId": 1, "expectedStatus": "SCHEDULED", "expectedVersion": 0}

    def test_identity_rejects_bool_float_missing_roles_and_role_strings(self):
        good = {"id": 1, "roles": ["CAREGIVER"]}
        load.assert_wire_response(good, {"kind": "identity", "userId": 1})
        for value in ({"id": True, "roles": ["CAREGIVER"]}, {"id": 1.0, "roles": ["CAREGIVER"]},
                      {"id": 1, "roles": "CAREGIVER"}, {"id": 1, "roles": ["FAMILY"]},
                      {"id": 1}, {"id": 1, "roles": [True, "CAREGIVER"]}, None):
            with self.subTest(value=value), self.assertRaises(load.Failure):
                load.assert_wire_response(value, {"kind": "identity", "userId": 1})

    def test_workpack_numeric_types_and_missing_structures_are_fatal(self):
        for key, values in (("id", (True, 1.0, None, 2)), ("elderId", (True, 1.0, None, 2)),
                            ("version", (False, 0.0, None, 1)), ("status", (None, "IN_PROGRESS"))):
            for value in values:
                body = self.pack(); body["visit"][key] = value
                with self.subTest(key=key, value=value), self.assertRaises(load.Failure):
                    load.Client.pack(body, self.fixture())
        for key, value in (("visit", None), ("tasks", None), ("tasks", [True]), ("execution", None),
                           ("execution", {"allowedActions": "CHECK_IN"})):
            body = self.pack(); body[key] = value
            with self.subTest(key=key), self.assertRaises(load.Failure):
                load.Client.pack(body, self.fixture())

    def test_empty_tasks_legitimate_and_optional_count_frozen(self):
        load.Client.pack(self.pack(), self.fixture())
        fixture = {**self.fixture(), "expectedTaskCount": 0}
        load.Client.pack(self.pack(), fixture)
        body = self.pack(); body["tasks"] = [{"id": 10}]
        with self.assertRaisesRegex(load.Failure, "wrong_task_count"):
            load.Client.pack(body, fixture)
        load.Client.pack(body, {**fixture, "expectedTaskCount": 1})

    def test_first_checkin_boolean_version_is_not_an_integer(self):
        check = {"kind": "checkin", "fixture": self.fixture()}
        good = {"visitId": 1, "visitVersion": 1, "replayed": False, "savedState": "IN_PROGRESS"}
        load.assert_wire_response(good, check)
        for field, value in (("visitId", True), ("visitVersion", True), ("visitVersion", 1.0),
                             ("replayed", 0), ("replayed", True)):
            body = {**good, field: value}
            with self.subTest(field=field, value=value), self.assertRaises(load.Failure):
                load.assert_wire_response(body, check)

    def test_complete_samples_outside_target_are_distinct_from_valid_load(self):
        c = old.config()
        c["phases"] = [{"name": "pilot", "rps": 100, "durationSeconds": 1}]
        load.validate(c)
        phase = c["phases"][0]; phase.update(emissionSeconds=1, dropped=0, notScheduledAfterStop=0)
        http = [{"phase": "pilot", "method": "GET", "endpoint": "/api/visits/{id}/work-pack", "elapsedMs": 1500,
                 "httpPassed": True, "businessPassed": True} for _ in range(100)]
        iterations = [{"phase": "pilot", "elapsedMs": 1501, "passed": True, "launchLagMs": 1} for _ in range(100)]
        result = load.summarize(http, iterations, c["phases"], c["targets"])[0]
        self.assertEqual(result["clientEvidenceStatus"], "COMPLETE_CLIENT_SAMPLES")
        self.assertEqual(result["endpoints"][0]["targetObservation"], "OUTSIDE_DRAFT_TARGET")
        phase["dropped"] = 1
        self.assertEqual(load.summarize(http, iterations, c["phases"], c["targets"])[0]["clientEvidenceStatus"], "INCOMPLETE_LOAD")

    def test_partial_200_deadline_and_drain_not_double_counted(self):
        c = old.config(); load.validate(c)
        phase = c["phases"][0]; phase.update(dropped=0, notScheduledAfterStop=0)
        rows = [{"phase": "pilot", "method": "GET", "endpoint": "/api/visits/{id}/work-pack",
                 "elapsedMs": 500, "httpPassed": True, "businessPassed": False, "error": code}
                for code in ("request_deadline", "stop_drain_deadline")]
        its = [{"phase": "pilot", "elapsedMs": 500, "passed": False, "launchLagMs": 0, "error": r["error"]} for r in rows]
        metric = load.summarize(rows, its, c["phases"], c["targets"])[0]
        endpoint = metric["endpoints"][0]
        self.assertEqual((endpoint["httpFailures"], endpoint["businessFailures"],
                          endpoint["requestDeadlineFailures"], endpoint["cancelledRequests"]), (0, 2, 1, 1))
        self.assertIsNone(endpoint["successP95Ms"])
        self.assertIsNone(metric["iterationSuccessP95Ms"])
        self.assertEqual(metric["clientEvidenceStatus"], "INSUFFICIENT_SAMPLES")

    def test_cooldown_not_applicable_but_active_empty_is_missing_load(self):
        c = old.config(); load.validate(c)
        phases = [{"name": "cool", "rps": 0, "durationSeconds": 1, "iterations": 0},
                  {"name": "active", "rps": 1, "durationSeconds": 1, "iterations": 1}]
        results = load.summarize([], [], phases, c["targets"])
        self.assertEqual(results[0]["clientEvidenceStatus"], "NOT_APPLICABLE")
        self.assertEqual(results[0]["iterationTargetObservation"], "NOT_APPLICABLE")
        self.assertEqual(results[1]["clientEvidenceStatus"], "INCOMPLETE_LOAD")


class PairTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="visit-d25-pair-")
        self.root = Path(self.temp.name).resolve()
        assert self.root.parent == Path(tempfile.gettempdir()).resolve()
        self.addCleanup(self.temp.cleanup)
        self.a0, self.a1 = self.root / "A0", self.root / "A1"
        self.synthetic_arm(self.a0, "synthetic-unit-A0")
        self.synthetic_arm(self.a1, "synthetic-unit-A1")

    def synthetic_arm(self, out, runid):
        """Build invented rows to test checker logic. Never exported as real/cloud observations."""
        out.mkdir()
        c = joint(); plan = load.validate(c)
        for p in c["phases"]:
            p.update(dropped=0, notScheduledAfterStop=0, emissionSeconds=p["durationSeconds"])
        rows, iterations = [], []
        for p in c["phases"]:
            for _ in range(p["iterations"]):
                rows.append({"phase": p["name"], "method": "GET", "endpoint": "/api/visits/{id}/work-pack",
                             "elapsedMs": 4, "httpPassed": True, "businessPassed": True})
                iterations.append({"phase": p["name"], "elapsedMs": 5, "passed": True, "launchLagMs": 0})
        metrics = load.summarize(rows, iterations, c["phases"], c["targets"])
        setup = [{"phase": "setup", "businessPassed": True, "httpPassed": True} for _ in range(40)]
        manifest = {"scenario": "work-pack", "runId": runid, "plan": plan, "phases": c["phases"],
                    "codeBaseline": c["codeBaseline"], "toolSha256": hashlib.sha256((ROOT / "load.py").read_bytes()).hexdigest(),
                    "targetOrigins": [c["coreBase"], c["visitBase"]], "maxVUs": 8, "maxLaunchLagMs": 200,
                    "targets": c["targets"], "stop": c["stop"], "comparisonInputs": load.comparison_inputs(c)}
        summary = {"scenario": "work-pack", "runId": runid, "outcome": "COMPLETED", "phases": metrics,
                   "plannedIterations": 2700, "launchedIterations": 2700, "failedIterations": 0,
                   "droppedIterations": 0, "notScheduledAfterStop": 0, "workerCleanupComplete": True,
                   "accountingValid": True, "clientEvidenceStatus": "COMPLETE_CLIENT_SAMPLES",
                   "httpRequestsDuringSetup": 40, "httpRequestsDuringLoad": 2700}
        load.write_json(out / "manifest.json", manifest); load.write_json(out / "summary.json", summary)
        load.write_json(out / "worker-cleanup.json", {"complete": True,
                        "workers": [{"reaped": True, "readerJoined": True, "writerJoined": True}]})
        (out / "requests.jsonl").write_text("".join(json.dumps(r) + "\n" for r in setup + rows), encoding="utf-8")
        (out / "iterations.jsonl").write_text("".join(json.dumps(r) + "\n" for r in iterations), encoding="utf-8")
        with (out / "phases.csv").open("w", newline="", encoding="utf-8") as stream:
            writer = csv.writer(stream); writer.writerow(["timestamp", "phase"])
            for p in c["phases"]:
                for suffix in ("-start", "-end"):
                    writer.writerow(["2026-10-19T00:00:00+00:00", p["name"] + suffix])

    def change(self, file, mutate):
        value = load.read_json(file); mutate(value); load.write_json(file, value)

    def test_matching_client_files_still_require_resource_review(self):
        report = compare_module.compare(self.a0, self.a1)
        self.assertEqual(report["status"], "AWAITING_RESOURCE_REVIEW")
        self.assertTrue(report["clientComparable"])
        self.assertFalse(report["formalAcceptance"])
        self.assertEqual(report["hpaMechanism"], "NOT_EVALUATED")

    def test_matching_declarations_do_not_prove_hpa_or_performance(self):
        report = compare_module.compare(self.a0, self.a1, context())
        self.assertEqual(report["status"], "CLIENT_AND_DECLARED_CONDITIONS_MATCH")
        self.assertFalse(report["formalAcceptance"])
        self.assertEqual(report["performanceBenefit"], "NOT_EVALUATED")

    def test_version_data_workload_drift_and_same_runid_rejected(self):
        for field, value in (("codeBaseline", "b" * 40), ("toolSha256", "b" * 64), ("maxVUs", 7),
                             ("runId", "synthetic-unit-A0")):
            original = load.read_json(self.a1 / "manifest.json")
            changed = copy.deepcopy(original); changed[field] = value
            load.write_json(self.a1 / "manifest.json", changed)
            with self.subTest(field=field):
                self.assertEqual(compare_module.compare(self.a0, self.a1, context())["status"], "NOT_COMPARABLE")
            load.write_json(self.a1 / "manifest.json", original)
        for key in ("fixtureSha256", "workloadSha256"):
            original = load.read_json(self.a1 / "manifest.json")
            changed = copy.deepcopy(original); changed["comparisonInputs"][key] = "b" * 64
            load.write_json(self.a1 / "manifest.json", changed)
            self.assertFalse(compare_module.compare(self.a0, self.a1)["clientComparable"])
            load.write_json(self.a1 / "manifest.json", original)

    def test_missing_raw_samples_cleanup_or_markers_is_not_comparable(self):
        for name in ("requests.jsonl", "iterations.jsonl", "worker-cleanup.json", "phases.csv"):
            file = self.a1 / name
            original = file.read_bytes()
            file.write_bytes(b"")
            with self.subTest(name=name):
                result = compare_module.compare(self.a0, self.a1, context())
                self.assertFalse(result["clientComparable"])
                self.assertEqual(result["status"], "INSUFFICIENT_CLIENT_EVIDENCE")
            file.write_bytes(original)

    def test_stopped_dropped_and_insufficient_samples_not_hidden(self):
        for field, value in (("outcome", "STOPPED"), ("droppedIterations", 1), ("accountingValid", False),
                             ("clientEvidenceStatus", "INSUFFICIENT_SAMPLES")):
            original = load.read_json(self.a1 / "summary.json")
            changed = copy.deepcopy(original); changed[field] = value
            load.write_json(self.a1 / "summary.json", changed)
            with self.subTest(field=field):
                self.assertEqual(compare_module.compare(self.a0, self.a1, context())["status"],
                                 "INSUFFICIENT_CLIENT_EVIDENCE")
            load.write_json(self.a1 / "summary.json", original)

    def test_cache_warmup_images_nodes_and_review_flags_must_match(self):
        for key in ("coreDigest", "visitDigest", "nodesRef", "resourcesRef", "sessionCacheRef",
                    "readRoutingRef", "warmupRef", "fixtureShapeRef"):
            ctx = context()
            ctx["arms"]["A1"]["conditions"][key] = "sha256:" + "3" * 64 if key.endswith("Digest") else "changed"
            with self.subTest(key=key):
                self.assertEqual(compare_module.compare(self.a0, self.a1, ctx)["status"], "NOT_COMPARABLE")
        ctx = context(); ctx["review"]["resourceMetricsComplete"] = False
        self.assertEqual(compare_module.compare(self.a0, self.a1, ctx)["status"], "AWAITING_RESOURCE_REVIEW")


    def test_malformed_comparison_fields_and_nonfinite_metrics_fail_closed(self):
        for value in (None, [], "bad"):
            original = load.read_json(self.a1 / "manifest.json")
            changed = copy.deepcopy(original); changed["comparisonInputs"] = value
            load.write_json(self.a1 / "manifest.json", changed)
            self.assertFalse(compare_module.compare(self.a0, self.a1, context())["clientComparable"])
            load.write_json(self.a1 / "manifest.json", original)
        original = load.read_json(self.a1 / "summary.json")
        changed = copy.deepcopy(original); changed["phases"][0]["endpoints"][0]["allP95Ms"] = float("nan")
        load.write_json(self.a1 / "summary.json", changed)
        self.assertFalse(compare_module.compare(self.a0, self.a1, context())["clientComparable"])
        changed = copy.deepcopy(original); changed["phases"][0]["iterationP95Ms"] = None
        load.write_json(self.a1 / "summary.json", changed)
        self.assertFalse(compare_module.compare(self.a0, self.a1, context())["clientComparable"])

    def test_comparison_has_no_network_or_subprocess(self):
        with patch.object(socket, "getaddrinfo", side_effect=AssertionError("DNS")), \
                patch.object(load.subprocess, "Popen", side_effect=AssertionError("worker")):
            self.assertTrue(compare_module.compare(self.a0, self.a1, context())["clientComparable"])


class ReadLoopbackTests(unittest.TestCase):
    def harness(self):
        h = old.LoopbackTests(methodName="test_real_http_login_cookies_csrf_and_read_success")
        h.setUp(); self.addCleanup(h.doCleanups)
        return h

    def test_two_scaled_arms_exact_get_counts_and_low_sample_labels(self):
        h = self.harness()
        summaries, dirs = [], []
        for arm in ("A0", "A1"):
            c = joint(); c.update(coreBase=h.cfg()["coreBase"], visitBase=h.cfg()["visitBase"], maxVUs=2,
                                  runId="loopback-" + arm)
            c["phases"] = [{"name": name, "rps": rps, "durationSeconds": 1}
                           for name, rps in (("baseline", 1), ("step-2", 2), ("step-4", 4), ("step-5", 5), ("hold", 5))]
            c["phases"].append({"name": "cooldown", "rps": 0, "durationSeconds": .2})
            c["approval"]["confirmed"] = True  # still does not confirm draft SLA thresholds.
            out = Path(h.temp.name) / arm
            self.assertEqual(load.run(c, out), 0)
            summary = load.read_json(out / "summary.json")
            self.assertEqual((summary["plannedIterations"], summary["httpRequestsDuringLoad"],
                              summary["httpRequestsDuringSetup"], summary["droppedIterations"]), (17, 17, 10, 0))
            self.assertEqual(summary["clientEvidenceStatus"], "INSUFFICIENT_SAMPLES")
            self.assertTrue(summary["targetsAreDraft"])
            self.assertEqual(summary["phases"][-1]["clientEvidenceStatus"], "NOT_APPLICABLE")
            self.assertTrue(summary["workerCleanupComplete"])
            with (out / "summary.csv").open(encoding="utf-8", newline="") as stream:
                csv_rows = list(csv.DictReader(stream))
            self.assertEqual(len(csv_rows), 5)
            self.assertTrue(all(json.loads(row["errorCategories"]) == {} for row in csv_rows))
            self.assertTrue(all(int(row["requestDeadlineFailures"]) == 0 for row in csv_rows))
            summaries.append(summary); dirs.append(out)
        report = compare_module.compare(*dirs)
        self.assertEqual(report["status"], "INSUFFICIENT_CLIENT_EVIDENCE")
        self.assertEqual(report["differences"], [])
        evidence_dir = os.environ.get("VISIT_HPA_D25_EVIDENCE_DIR")
        if evidence_dir:
            path = Path(evidence_dir); path.mkdir(parents=True, exist_ok=True)
            for arm, summary in zip(("A0", "A1"), summaries):
                load.write_json(path / (arm + "-loopback-summary.json"), summary)
            load.write_json(path / "loopback-comparison.json", report)

    def test_bool_version_rejected_over_real_loopback_http(self):
        h = self.harness(); h.versions[1] = False
        out = Path(h.temp.name) / "bool"
        self.assertEqual(load.run(h.cfg(), out), 2)
        summary = load.read_json(out / "summary.json")
        self.assertEqual(summary["launchedIterations"], 0)
        self.assertIn("setup_wrong_visit_or_state", summary["stopReasons"])


    def test_invalid_json_content_type_200_stops_before_load(self):
        h = self.harness()
        handler = h.server.RequestHandlerClass
        original = handler.respond
        def malformed(instance, code, value=None, headers=None, content_type="application/json"):
            if "/work-pack" not in instance.path:
                return original(instance, code, value, headers, content_type)
            instance.send_response(200)
            instance.send_header("Content-Type", "application/json")
            instance.end_headers()
            instance.wfile.write(b"{invalid")
        out = Path(h.temp.name) / "malformed-json"
        with patch.object(handler, "respond", malformed):
            self.assertEqual(load.run(h.cfg(), out), 2)
        summary = load.read_json(out / "summary.json")
        self.assertEqual(summary["launchedIterations"], 0)
        self.assertIn("invalid_json", summary["stopReasons"])

    def test_b_task_materialization_not_mistaken_for_a_frozen_task_count(self):
        h = self.harness()
        c = h.cfg(); c["scenario"] = "check-in"
        c["phases"] = [{"name": "pilot", "rps": 1, "durationSeconds": 1}]
        c["actors"][0]["visits"][0]["expectedTaskCount"] = 0
        out = Path(h.temp.name) / "B-regression"
        self.assertEqual(load.run(c, out), 0)
        self.assertEqual(load.read_json(out / "summary.json")["httpRequestsDuringLoad"], 3)

    def test_incorrect_expected_task_count_stops_before_load(self):
        h = self.harness()
        c = h.cfg(); c["actors"][0]["visits"][0]["expectedTaskCount"] = 1
        out = Path(h.temp.name) / "task-count"
        self.assertEqual(load.run(c, out), 2)
        self.assertEqual(load.read_json(out / "summary.json")["launchedIterations"], 0)


if __name__ == "__main__":
    unittest.main()
