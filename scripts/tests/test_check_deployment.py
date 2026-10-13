"""Read-only preflight error boundaries; no AWS or Kubernetes access."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[1] / "check-deployment.py"
spec = importlib.util.spec_from_file_location("check_deployment", SOURCE)
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


class DeploymentCheckTest(unittest.TestCase):
    def setUp(self):
        self.account = "123456789012"
        self.endpoint = "https://expected.invalid"
        self.digest = "sha256:" + "b" * 64
        self.rows = "\n".join(f"{s}\t{'a' * 40}\t{self.digest}" for s in ("core", "visit", "report", "notification"))
        self.args = [str(SOURCE), "staging", "--registry", f"{self.account}.dkr.ecr.ap-southeast-1.amazonaws.com",
                     "--manifest", "release.json", "--context", "runtime", "--profile", "carelink"]

    def response(self, command, label):
        if label == "Release manifest validation":
            return self.rows
        if label == "AWS login":
            return json.dumps({"Account": self.account})
        if label == "EKS cluster":
            return json.dumps({"cluster": {"status": "ACTIVE", "endpoint": self.endpoint}})
        if label == "Kubernetes context":
            return json.dumps({"clusters": [{"cluster": {"server": self.endpoint}}]})
        if label.startswith("ECR image"):
            return json.dumps({"imageDetails": [{"imageDigest": self.digest}]})
        return ""

    def invoke(self, response):
        with patch.object(check, "run", side_effect=response) as calls, patch("sys.argv", self.args), \
                contextlib.redirect_stdout(io.StringIO()):
            check.main()
        return calls

    def test_success_checks_four_images_and_reaches_selected_context(self):
        calls = self.invoke(self.response)
        images = [call.args[0] for call in calls.call_args_list if call.args[1].startswith("ECR image")]
        self.assertEqual(len(images), 4)
        self.assertTrue(all("--profile" in command and "carelink" in command for command in images))
        kube = calls.call_args_list[-1].args[0]
        self.assertEqual(kube[:4], ["kubectl", "--context", "runtime", "--request-timeout=15s"])
        self.assertEqual(kube[4:], ["--namespace", "staging", "get", "deployments", "-o", "name"])

    def test_wrong_account_or_context_or_digest_stops_before_api_access(self):
        cases = {
            "AWS login": {"Account": "999999999999"},
            "Kubernetes context": {"clusters": [{"cluster": {"server": "https://wrong.invalid"}}]},
            "ECR image notification": {"imageDetails": [{"imageDigest": "sha256:" + "c" * 64}]},
        }
        for failure, value in cases.items():
            seen = []
            def response(command, label):
                seen.append(label)
                return json.dumps(value) if label == failure else self.response(command, label)
            with self.subTest(failure=failure), self.assertRaises(ValueError):
                self.invoke(response)
            self.assertNotIn("Kubernetes API access", seen)

    def test_error_categories_do_not_expose_arbitrary_output(self):
        for stderr, expected in [
            ("An error occurred (ResourceNotFoundException) SECRET", "ResourceNotFoundException"),
            ("An error occurred (AccessDeniedException) SECRET", "AccessDeniedException"),
            ("Forbidden SECRET", "Kubernetes authorization"),
            ("Unable to connect to the server SECRET", "Connection failed"),
        ]:
            with patch.object(check.subprocess, "run", return_value=subprocess.CompletedProcess([], 1, "", stderr)):
                with self.assertRaises(ValueError) as error:
                    check.run(["aws", "eks"], "EKS")
                self.assertIn(expected, str(error.exception))
                self.assertNotIn("SECRET", str(error.exception))

    def test_timeout_has_bounded_diagnostic(self):
        with patch.object(check.subprocess, "run", side_effect=subprocess.TimeoutExpired(["kubectl"], 30)):
            with self.assertRaisesRegex(ValueError, "timed out"):
                check.run(["kubectl"], "Kubernetes API access")


if __name__ == "__main__":
    unittest.main()
