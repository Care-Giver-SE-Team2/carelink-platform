import csv
import json
import sys
import tempfile
import threading
import unittest
import urllib.request
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from common import pod_requests, quantity, kubectl
from capacity import assess
from collect import csv_row
from probe import NoRedirect, observe
from report import build


def container(cpu="100m", memory="512Mi", **kwargs):
    return {"name": "app", "image": "registry.invalid/visit@test", "resources": {
        "requests": {"cpu": cpu, "memory": memory}}, **kwargs}


def fixture():
    nodes = {"items": [{"metadata": {"name": name, "labels": {"topology.kubernetes.io/zone": zone}},
                        "status": {"conditions": [{"type": "Ready", "status": "True"}],
                                   "allocatable": {"cpu": "1930m", "memory": "7079Mi", "pods": "29"}}}
                       for name, zone in (("node-a", "a"), ("node-b", "b"))]}
    pods = {"items": [{"metadata": {"name": f"visit-{i}", "namespace": "staging", "ownerReferences": [{"kind": "ReplicaSet"}]},
                       "spec": {"nodeName": node, "containers": [container()]},
                       "status": {"phase": "Running", "conditions": [{"type": "Ready", "status": "True"}]}}
                      for i, node in enumerate(("node-a", "node-b"))]}
    deployment = {"metadata": {"name": "visit", "uid": "fixture-uid", "generation": 1},
                  "spec": {"replicas": 2, "template": {"spec": {"containers": [container()]}}},
                  "status": {"readyReplicas": 2}}
    return nodes, pods, deployment


class RequestBudgetTests(unittest.TestCase):
    def test_quantity_cpu_and_binary_decimal_memory(self):
        self.assertAlmostEqual(quantity("75m"), .075)
        self.assertAlmostEqual(quantity("10n"), 1e-8)
        self.assertEqual(quantity("1Gi"), 1024**3)
        self.assertEqual(quantity("1e9"), 10**9)
        self.assertEqual(quantity("1G"), 10**9)
        with self.assertRaises(ValueError):
            quantity("invalid")

    def test_init_sidecars_and_overhead_are_not_omitted(self):
        spec = {"containers": [container("250m", "512Mi")],
                "initContainers": [container("100m", "128Mi", restartPolicy="Always"),
                                   container("800m", "1Gi")], "overhead": {"cpu": "50m", "memory": "16Mi"}}
        self.assertEqual(pod_requests(spec), {"cpu_m": 950, "memory_mib": 1168})

    def test_pod_level_request_override(self):
        self.assertEqual(pod_requests({"containers": [container()], "resources": {
            "requests": {"cpu": "1", "memory": "1Gi"}}}), {"cpu_m": 1000, "memory_mib": 1024})

    def test_pending_demand_and_reserve_can_fail_budget(self):
        nodes, pods, deployment = fixture()
        pending = {"metadata": {"name": "pending"}, "spec": {"containers": [container("4", "15Gi")]},
                   "status": {"phase": "Pending"}}
        pods["items"].append(pending)
        result = assess(nodes, pods, deployment, 4, 1)
        self.assertFalse(result["aggregate_budget_fits"])
        self.assertEqual(len(result["pending_pods"]), 1)
        self.assertTrue(all(not s["aggregate_budget_fits"] for s in result["single_node_loss"]))

    def test_terminal_pods_excluded_daemonset_not_migrated(self):
        nodes, pods, deployment = fixture()
        pods["items"].append({"metadata": {"name": "finished"}, "spec": {"containers": [container("10", "100Gi")]},
                              "status": {"phase": "Succeeded"}})
        pods["items"].append({"metadata": {"name": "daemon", "ownerReferences": [{"kind": "DaemonSet"}]},
                              "spec": {"nodeName": "node-a", "containers": [container("500m", "1Gi")]},
                              "status": {"phase": "Running"}})
        result = assess(nodes, pods, deployment, 4, 1)
        self.assertTrue(result["aggregate_budget_fits"])
        lost_a = result["single_node_loss"][0]
        self.assertEqual(lost_a["projected_requests"]["cpu_m"], 500)
        self.assertEqual(result["nodes"][0]["requested"]["cpu_m"], 600)

    def test_pod_slot_limit_not_just_cpu_memory(self):
        nodes, pods, deployment = fixture()
        for node in nodes["items"]:
            node["status"]["allocatable"]["pods"] = "2"
        self.assertFalse(assess(nodes, pods, deployment, 4, 1)["aggregate_budget_fits"])

    def test_missing_cpu_request_warns(self):
        nodes, pods, deployment = fixture()
        del deployment["spec"]["template"]["spec"]["containers"][0]["resources"]["requests"]["cpu"]
        result = assess(nodes, pods, deployment, 4, 1)
        self.assertTrue(any("without a CPU request" in w for w in result["warnings"]))


class EvidenceTests(unittest.TestCase):
    def test_kubectl_timeout_is_reportable_without_auth_plugin_output(self):
        import subprocess
        with patch("common.subprocess.run", side_effect=subprocess.TimeoutExpired("fixture", 20)):
            with self.assertRaisesRegex(RuntimeError, "timed out"):
                kubectl("fixture-context", "get", "pods", "-o", "json")

    def test_missing_metrics_not_written_as_zero(self):
        row = csv_row({"timestamp": "2026-10-17T00:00:00Z", "deployment": {"ready": 2},
                       "pods": [{"phase": "Running", "terminating": False, "usage": None}]})
        self.assertEqual(row["cpu_m_known_pods"], "")
        self.assertEqual(row["pods_with_metrics"], 0)
        self.assertEqual(row["active_pods"], 1)

    def test_report_flags_release_change_missing_metrics_and_absent_hpa(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            (folder / "samples.jsonl").write_text(json.dumps({"timestamp": "2026-10-17T00:00:00Z",
                "release_changed": True, "deployment": {"desired": 2, "ready": 2},
                "pods": [{"phase": "Running", "terminating": False, "usage": None}]}) + "\n")
            output = build(folder)
            self.assertIn("No HPA was observed", output)
            self.assertIn("lack Metrics API", output)
            self.assertIn("Deployment image/revision/identity changed", output)
            self.assertNotIn("<script src", output)

    def test_probe_rejects_redirect_html_and_invalid_json(self):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                if self.path == "/redirect":
                    self.send_response(302)
                    self.send_header("Location", "/good")
                    self.end_headers()
                    return
                self.send_response(200)
                self.send_header("Content-Type", "text/html" if self.path == "/html" else "application/json")
                self.end_headers()
                self.wfile.write(b'{"ok":true}' if self.path == "/good" else b'<html>login</html>')

            def log_message(self, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            opener = urllib.request.build_opener(NoRedirect())
            base = f"http://127.0.0.1:{server.server_port}"
            self.assertTrue(observe(opener, base + "/good", "SESSION=fixture", 2)["success"])
            for path, category in (("/redirect", "redirect"), ("/html", "not_json"), ("/bad", "invalid_json")):
                row = observe(opener, base + path, "SESSION=fixture", 2)
                self.assertFalse(row["success"])
                self.assertEqual(row["category"], category)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
