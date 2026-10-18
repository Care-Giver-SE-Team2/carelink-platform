"""Offline deployment safeguards; no AWS or Kubernetes calls."""
import base64
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


worker = load("report_worker_deploy", "deploy-report-worker.py")
iam = load("report_worker_iam", "prepare-report-worker-iam.py")
plan_guard = load("report_worker_plan", "check-report-worker-plan.py")


class WorkerDeploymentTest(unittest.TestCase):
    def render(self, *overrides, namespace="staging"):
        return subprocess.run(["helm", "template", "report-worker", str(ROOT / "charts/carelink-report-worker"),
                               "--namespace", namespace, "--set-string", "image.repository=registry.invalid/carelink/report",
                               "--set-string", "image.digest=sha256:" + "1" * 64, *overrides],
                              capture_output=True, text=True)

    def test_disabled_deploy_needs_no_credentials_or_cluster(self):
        self.assertFalse(worker.configuration("staging")["enabled"])
        result = subprocess.run([sys.executable, str(ROOT / "scripts/deploy-report-worker.py"), "deploy", "staging"],
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("no cloud or cluster calls", result.stdout)
        self.assertFalse(worker.configuration("demo")["enabled"])

    def test_secret_never_inherits_original_queue_or_topic(self):
        original = {"data": {key: base64.b64encode(b"fixture").decode() for key in worker.DATA_KEYS}}
        original["data"].update(CARELINK_EVENTS_QUEUEURL="OLD_QUEUE", CARELINK_EVENTS_TOPICARN="OLD_TOPIC",
                                AWS_ACCESS_KEY_ID="DO_NOT_COPY")
        before = json.dumps(original, sort_keys=True)
        secret = worker.runtime_secret(original, "https://queue.example.invalid/worker")
        self.assertEqual(set(secret["data"]), worker.DATA_KEYS | {"CARELINK_EVENTS_QUEUEURL"})
        self.assertEqual(base64.b64decode(secret["data"]["CARELINK_EVENTS_QUEUEURL"]).decode(),
                         "https://queue.example.invalid/worker")
        self.assertEqual(json.dumps(original, sort_keys=True), before)
        self.assertEqual(secret["metadata"]["name"], "report-worker-runtime")

    def test_incomplete_secret_stops(self):
        with self.assertRaises(ValueError):
            worker.runtime_secret({"data": {}}, "https://queue.example.invalid/worker")

    def test_fixed_worker_has_no_public_route_or_scaler(self):
        result = self.render()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("replicas: 1", result.stdout)
        self.assertIn("serviceAccountName: report-worker", result.stdout)
        self.assertIn("terminationGracePeriodSeconds: 270", result.stdout)
        for kind in ("Ingress", "Service", "ScaledObject", "HorizontalPodAutoscaler"):
            self.assertNotIn(f"kind: {kind}\n", result.stdout)

    def test_keda_owns_replicas_and_uses_inflight(self):
        result = self.render("--set", "scaling.enabled=true", "--set", "scaling.maxReplicas=2",
                             "--set-string", "queueURL=https://queue.example.invalid/worker")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn("\n  replicas:", result.stdout)
        self.assertNotIn("kind: HorizontalPodAutoscaler", result.stdout)
        self.assertIn("kind: ScaledObject", result.stdout)
        self.assertIn('scaleOnInFlight: "true"', result.stdout)
        self.assertIn("maxReplicaCount: 2", result.stdout)
        self.assertIn('autoscaling.keda.sh/paused: "true"', result.stdout)

    def test_wrong_namespace_or_unbounded_scaling_fails(self):
        self.assertNotEqual(self.render(namespace="demo").returncode, 0)
        self.assertNotEqual(self.render("--set", "scaling.maxReplicas=3").returncode, 0)


class WorkerIamTest(unittest.TestCase):
    def test_separate_limited_boundaries(self):
        policies = iam.policies("123456789012")
        keda = policies["CareLinkKedaQueueReadBoundary"]["Statement"]
        self.assertEqual(keda[0]["Action"], "sqs:GetQueueAttributes")
        consumer = policies["CareLinkReportWorkerBoundary"]["Statement"]
        self.assertIn("sqs:ChangeMessageVisibility", consumer[0]["Action"])
        self.assertNotIn("sns:Publish", consumer[0]["Action"])

    def test_no_operator_boundary_or_admin_policy_modification(self):
        docs = iam.policies("123456789012")
        statements = docs["CareLinkKedaIntegrationOperator"]["Statement"]
        actions = {a for s in statements for a in ([s["Action"]] if isinstance(s["Action"], str) else s["Action"])}
        self.assertNotIn("iam:CreatePolicy", actions)
        self.assertNotIn("iam:PutRolePermissionsBoundary", actions)
        self.assertNotIn("iam:DeleteRolePermissionsBoundary", actions)
        creates = [s for s in statements if s["Action"] == "iam:CreateRole"]
        self.assertEqual(len(creates), 2)
        for statement in creates:
            self.assertIn("iam:PermissionsBoundary", statement["Condition"]["StringEquals"])
            self.assertNotIn("*", statement["Resource"])
        for doc in docs.values():
            self.assertLessEqual(len(json.dumps(doc, separators=(",", ":"))), 6144)


class WorkerPlanTest(unittest.TestCase):
    def plan(self):
        return {"resource_changes": [{"address": a, "change": {"actions": ["create"]}}
                                     for a in sorted(plan_guard.EXPECTED)]}

    def test_only_dedicated_initial_creates(self):
        plan_guard.validate(self.plan())

    def test_existing_resource_or_destructive_action_stops(self):
        plan = self.plan()
        plan["resource_changes"].append({"address": "aws_db_instance.primary", "change": {"actions": ["delete"]}})
        with self.assertRaises(ValueError):
            plan_guard.validate(plan)
        for actions in (["delete", "create"], ["update"], ["delete"]):
            plan = self.plan()
            plan["resource_changes"][0]["change"]["actions"] = actions
            with self.subTest(actions=actions), self.assertRaises(ValueError):
                plan_guard.validate(plan)

    def test_incomplete_or_deferred_plan_stops(self):
        plan = self.plan()
        plan["resource_changes"].pop()
        with self.assertRaises(ValueError):
            plan_guard.validate(plan)
        plan = self.plan()
        plan["deferred_changes"] = [{"reason": "unknown"}]
        with self.assertRaises(ValueError):
            plan_guard.validate(plan)


if __name__ == "__main__":
    unittest.main()
