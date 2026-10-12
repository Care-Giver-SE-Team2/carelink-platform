"""Config-boundary and CLI tests; fake credentials only, no AWS or Kubernetes calls."""
import base64
import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

SOURCE = Path(__file__).resolve().parents[1] / "sync-runtime-config.py"
spec = importlib.util.spec_from_file_location("sync_runtime_config", SOURCE)
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


class RuntimeConfigTest(unittest.TestCase):
    def setUp(self):
        self.data = {
            "environment": "staging", "mysql_host": "mysql.example.invalid", "redis_host": "redis.example.invalid",
            "service_data_secret_arns": {
                name: f"arn:aws:secretsmanager:ap-southeast-1:123456789012:secret:carelink/staging/test-01/{name}-data-AbCd12"
                for name in sync.SERVICES
            },
            "service_databases": {name: {"schema": f"carelink_{name}", "user": name} for name in sync.SERVICES},
        }
        self.messages = {
            "environment": "staging", "aws_account_id": "123456789012", "region": "ap-southeast-1",
            "cluster_name": "carelink-runtime", "topic_arn": "arn:aws:sns:ap-southeast-1:123456789012:carelink-staging-events",
            "queue_urls": {name: f"https://sqs.ap-southeast-1.amazonaws.com/123456789012/carelink-staging-{name}"
                           for name in sync.SERVICES},
        }
        self.secrets = {}
        for name, arn in self.data["service_data_secret_arns"].items():
            values = {
                "DB_URL": f"jdbc:mysql://mysql.example.invalid:3306/carelink_{name}?sslMode=VERIFY_IDENTITY",
                "DB_USER": name, "DB_PASSWORD": f"fake-password-{name}",
                "SPRING_DATA_REDIS_HOST": "redis.example.invalid", "SPRING_DATA_REDIS_PORT": "6379",
                "SPRING_DATA_REDIS_PASSWORD": "fake-redis-password", "SPRING_DATA_REDIS_SSL_ENABLED": "true",
            }
            self.secrets[arn] = {"ARN": arn, "SecretString": json.dumps(values)}

    def build(self):
        return sync.build_manifest(self.data, self.messages, "staging", b"public-ca", self.secrets.__getitem__)

    def fake_json(self, command, environment):
        if command[0] == "terraform":
            return self.data if command[-1] == "data_connections" else self.messages
        if "get-caller-identity" in command:
            return {"Account": "123456789012"}
        if "describe-cluster" in command:
            return {"cluster": {"status": "ACTIVE", "endpoint": "https://expected.example.invalid"}}
        if command[0] == "kubectl":
            return {"clusters": [{"cluster": {"server": "https://expected.example.invalid"}}]}
        return self.secrets[command[-1]]

    def invoke(self, runner):
        with patch.object(sync, "run_json", side_effect=self.fake_json), \
                patch.object(sync.Path, "read_bytes", return_value=b"public-ca"), \
                patch.object(sync.subprocess, "run", side_effect=runner) as calls, \
                patch("sys.argv", [str(SOURCE), "staging", "--profile", "carelink", "--context", "carelink-runtime"]):
            sync.main()
        return calls

    def test_each_service_gets_only_its_credentials_and_required_event_endpoints(self):
        manifest = self.build()
        for secret in manifest["items"][2:]:
            name = secret["metadata"]["name"].removesuffix("-runtime")
            values = {key: base64.b64decode(value).decode() for key, value in secret["data"].items()}
            self.assertEqual(values["DB_PASSWORD"], f"fake-password-{name}")
            self.assertEqual("CARELINK_EVENTS_TOPICARN" in values, name != "notification")
            self.assertEqual("CARELINK_EVENTS_QUEUEURL" in values, name in ("report", "notification"))
            self.assertNotIn("AWS_ACCESS_KEY_ID", values)

    def test_reject_mismatched_environment_and_admin_secret(self):
        self.messages["environment"] = "demo"
        with self.assertRaises(ValueError):
            self.build()
        self.messages["environment"] = "staging"
        self.data["service_data_secret_arns"]["core"] = self.data["service_data_secret_arns"]["core"].replace("core-data", "database-admin")
        with self.assertRaises(ValueError):
            self.build()

    def test_failure_in_last_secret_prevents_every_kubernetes_write(self):
        arn = self.data["service_data_secret_arns"]["notification"]
        del self.secrets[arn]
        writes = []
        with self.assertRaises(KeyError):
            self.invoke(lambda *args, **kwargs: writes.append(args))
        self.assertEqual(writes, [])

    def test_reject_static_aws_keys_or_disabled_tls(self):
        arn = self.data["service_data_secret_arns"]["report"]
        for key, value in (("AWS_ACCESS_KEY_ID", "fake-key"), ("SPRING_DATA_REDIS_SSL_ENABLED", "false")):
            original = copy.deepcopy(self.secrets[arn])
            values = json.loads(original["SecretString"])
            values[key] = value
            self.secrets[arn]["SecretString"] = json.dumps(values)
            with self.assertRaises(ValueError):
                self.build()
            self.secrets[arn] = original

    def test_apply_uses_stdin_and_server_side_without_credential_output(self):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            calls = self.invoke(lambda command, **kwargs: subprocess.CompletedProcess(command, 0, "", ""))
        self.assertEqual(calls.call_count, 3)  # namespace, server dry-run, apply
        for call in calls.call_args_list:
            command = call.args[0]
            self.assertIn("--server-side", command)
            self.assertNotIn("fake-password", " ".join(command))
            self.assertEqual(command[command.index("-f") + 1], "-")
        self.assertIn("--dry-run=server", calls.call_args_list[1].args[0])
        self.assertEqual(json.loads(calls.call_args_list[2].kwargs["input"])["items"][0]["kind"], "ConfigMap")
        self.assertNotIn("fake-password", output.getvalue())

    def test_failed_server_validation_does_not_apply_any_secrets(self):
        operations = []
        def runner(command, **kwargs):
            operations.append(command)
            return subprocess.CompletedProcess(command, 1 if "--dry-run=server" in command else 0, "", "")
        with self.assertRaises(ValueError):
            self.invoke(runner)
        self.assertEqual(len(operations), 2)
        self.assertIn("--dry-run=server", operations[-1])


if __name__ == "__main__":
    unittest.main()
