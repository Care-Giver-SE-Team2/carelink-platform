import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("platform_readiness", Path(__file__).parents[1] / "audit-platform-readiness.py")
audit = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(audit)


class ReadinessTests(unittest.TestCase):
    def test_forbidden_is_unverified_not_absent(self):
        result = subprocess.CompletedProcess([], 1, "", "AccessDeniedException: budgets:ViewBudget")
        with patch.object(audit.subprocess, "run", return_value=result):
            self.assertEqual(audit.request(["aws"], {})["status"], "unverified")

    def test_only_explicit_not_found_is_absent(self):
        result = subprocess.CompletedProcess([], 1, "", 'Error from server (NotFound): CRD missing')
        with patch.object(audit.subprocess, "run", return_value=result):
            self.assertEqual(audit.request(["kubectl"], {})["status"], "not_found")

    def test_subscriber_addresses_are_not_in_shareable_summary(self):
        result = {"status": "observed", "data": {"Subscribers": [
            {"SubscriptionType": "EMAIL", "Address": "private@example.invalid"},
            {"SubscriptionType": "SNS", "Address": "arn:aws:sns:example"},
        ]}}
        summary = audit.subscriber_summary(result)
        self.assertEqual(summary, {"status": "observed", "count": 2, "types": ["EMAIL", "SNS"]})
        self.assertNotIn("Address", json.dumps(summary))

    def test_root_or_wrong_account_stops_before_inventory(self):
        for caller in [{"Account": "123456789012", "Arn": "arn:aws:iam::123456789012:root"},
                       {"Account": "999999999999", "Arn": "arn:aws:iam::999999999999:user/test"}]:
            result = subprocess.CompletedProcess([], 0, json.dumps(caller), "")
            with patch.object(sys, "argv", ["audit", "--account-id", "123456789012", "--output", "unused.json"]), \
                    patch.object(audit.subprocess, "run", return_value=result) as run:
                with self.assertRaises(SystemExit):
                    audit.main()
                self.assertEqual(run.call_count, 1)

    def test_kubeconfig_failure_preserves_partial_report_without_raw_output(self):
        private_fragment = "private-kubeconfig-test-fragment"
        failures = [subprocess.TimeoutExpired(["aws", "eks", "update-kubeconfig"], 45,
                                              output=private_fragment, stderr=private_fragment),
                    OSError(private_fragment)]
        for failure in failures:
            with self.subTest(failure=type(failure).__name__):
                def execute(command, **kwargs):
                    if "get-caller-identity" in command:
                        body = {"Account": "123456789012", "Arn": "arn:aws:iam::123456789012:user/test"}
                    elif "describe-budgets" in command:
                        body = {"Budgets": []}
                    elif "list-addons" in command:
                        body = {"addons": ["eks-pod-identity-agent"]}
                    else:
                        self.assertIn("update-kubeconfig", command)
                        self.assertIn("--dry-run", command)
                        raise failure
                    return subprocess.CompletedProcess(command, 0, json.dumps(body), "")

                with tempfile.TemporaryDirectory() as temporary:
                    output = Path(temporary) / "report.json"
                    with patch.object(sys, "argv", ["audit", "--account-id", "123456789012", "--output", str(output)]), \
                            patch.object(audit.subprocess, "run", side_effect=execute) as run:
                        audit.main()
                    report = json.loads(output.read_text())
                    self.assertEqual(report["budgets"], {"status": "observed", "items": []})
                    self.assertEqual(report["eks_addons"]["data"]["addons"], ["eks-pod-identity-agent"])
                    self.assertEqual(report["kubernetes"]["status"], "unverified")
                    self.assertIn(type(failure).__name__, report["kubernetes"]["error"])
                    self.assertNotIn(private_fragment, output.read_text())
                    self.assertEqual(output.stat().st_mode & 0o777, 0o600)
                    self.assertEqual(run.call_count, 4)

    def test_inventory_keeps_partial_reads_and_never_runs_mutations(self):
        def execute(command, **kwargs):
            if command[0] == "aws":
                self.assertIn("carelink", command)
                if "get-caller-identity" in command:
                    body = {"Account": "123456789012", "Arn": "arn:aws:iam::123456789012:user/test"}
                elif "describe-budgets" in command or "list-addons" in command:
                    return subprocess.CompletedProcess(command, 1, "", "AccessDeniedException")
                else:
                    self.assertIn("update-kubeconfig", command)
                    self.assertIn("--dry-run", command)
                    return subprocess.CompletedProcess(command, 0, "# private local test kubeconfig", "")
            else:
                self.assertEqual(command[0], "kubectl")
                self.assertIn("get", command)
                if "crd" in command or "v1beta1.external.metrics.k8s.io" in command:
                    return subprocess.CompletedProcess(command, 1, "", "Error from server (NotFound): absent")
                if "deployment" in command:
                    body = {"metadata": {"name": "metrics-server", "namespace": "kube-system"},
                            "spec": {"replicas": 2, "template": {"spec": {"containers": [{"image": "metrics:test"}]}}},
                            "status": {"readyReplicas": 2}}
                else:
                    body = {"items": []}
            return subprocess.CompletedProcess(command, 0, json.dumps(body), "")

        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "report.json"
            with patch.object(sys, "argv", ["audit", "--account-id", "123456789012", "--output", str(output)]), \
                    patch.object(audit.subprocess, "run", side_effect=execute):
                audit.main()
            report = json.loads(output.read_text())
            self.assertEqual(report["budgets"]["status"], "unverified")
            self.assertEqual(report["kubernetes"]["scaledobjects"]["status"], "not_found")
            self.assertEqual(report["kubernetes"]["metrics_server"]["data"][0]["ready"], 2)
            self.assertEqual(output.stat().st_mode & 0o777, 0o600)


if __name__ == "__main__":
    unittest.main()
