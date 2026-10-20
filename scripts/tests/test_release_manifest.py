"""Release CLI and real Helm rendering; all cluster calls use local stubs."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
REGISTRY = "123456789012.dkr.ecr.ap-southeast-1.amazonaws.com"
SERVICES = ("core", "visit", "report", "notification")


class ReleaseManifestTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.records = {
            service: {"service": service, "image": f"{REGISTRY}/carelink/{service}",
                      "tag": str(index) * 40, "digest": "sha256:" + str(index) * 64}
            for index, service in enumerate(SERVICES, 1)
        }
        self.manifest = self.write("release.json", {"schema_version": 1, "services": self.records})

    def write(self, name, value):
        path = self.directory / name
        path.write_text(json.dumps(value))
        return path

    def cli(self, *args):
        return subprocess.run([sys.executable, str(ROOT / "scripts/release-manifest.py"),
                               "--registry", REGISTRY, *map(str, args)], capture_output=True, text=True)

    def deploy(self, environment="staging", **overrides):
        env = {key: value for key, value in os.environ.items()
               if key not in ("IMAGE_TAG", "RELEASE_MANIFEST", "DRY_RUN", "KUBE_CONTEXT")}
        env.update(IMAGE_REGISTRY=REGISTRY, RELEASE_MANIFEST=str(self.manifest), DRY_RUN="1")
        env.update(overrides)
        return subprocess.run(["bash", "scripts/deploy.sh", "all", environment], cwd=ROOT,
                              env=env, capture_output=True, text=True)

    def stub(self, name, body):
        path = self.directory / name
        path.write_text("#!/usr/bin/env bash\nset -eu\n" + body)
        path.chmod(0o700)

    def test_initial_release_uses_publisher_record_format(self):
        args = []
        for service, record in self.records.items():
            args.extend(["--record", self.write(f"{service}.json", record)])
        target = self.directory / "initial.json"
        result = self.cli("build", *args, "--output", target)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(target.read_text())["services"], self.records)

    def test_incremental_visit_keeps_other_images_and_previous_release(self):
        before = self.manifest.read_bytes()
        visit = dict(self.records["visit"], tag="a" * 40, digest="sha256:" + "a" * 64)
        record = self.write("visit.json", visit)
        target = self.directory / "next.json"
        result = self.cli("build", "--base", self.manifest, "--record", record, "--output", target)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(target.read_text())["services"], dict(self.records, visit=visit))
        self.assertEqual(self.manifest.read_bytes(), before)
        result = self.cli("build", "--base", self.manifest, "--record", record, "--output", self.manifest)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(self.manifest.read_bytes(), before)

    def test_incomplete_or_duplicate_release_has_no_output(self):
        record = self.write("visit.json", self.records["visit"])
        target = self.directory / "invalid.json"
        for extra in ([], ["--base", self.manifest, "--record", record]):
            result = self.cli("build", "--record", record, *extra, "--output", target)
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(target.exists())

    def test_invalid_last_record_prevents_all_cluster_calls(self):
        self.stub("kubectl", 'echo called >> "$CALL_LOG"\nexit 99\n')
        self.stub("helm", 'echo called >> "$CALL_LOG"\nexit 99\n')
        log = self.directory / "calls"
        for changes in ({"digest": "latest"}, {"image": "other.invalid/carelink/notification"},
                        {"service": "visit"}, {"tag": "short"}):
            records = dict(self.records, notification=dict(self.records["notification"], **changes))
            self.write("release.json", {"schema_version": 1, "services": records})
            result = self.deploy(DRY_RUN="0", KUBE_CONTEXT="test-context", CALL_LOG=str(log),
                                 PATH=f"{self.directory}:{os.environ['PATH']}")
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse(log.exists())

    def test_same_release_renders_each_digest_in_both_environments(self):
        for environment in ("staging", "demo"):
            result = self.deploy(environment)
            self.assertEqual(result.returncode, 0, result.stderr)
            for record in self.records.values():
                self.assertIn(f'image: "{record["image"]}@{record["digest"]}"', result.stdout)
            # HPA.scaleTargetRef also contains kind: Deployment; count only objects.
            self.assertEqual(result.stdout.count("\nkind: Deployment\n"), 4)

    def test_conflicting_tag_is_rejected(self):
        result = self.deploy(IMAGE_TAG="a" * 40)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not both", result.stderr)

    def test_selected_context_and_stop_before_callers_when_core_fails(self):
        keys = ("DB_URL DB_USER DB_PASSWORD SPRING_DATA_REDIS_HOST SPRING_DATA_REDIS_PORT "
                "SPRING_DATA_REDIS_PASSWORD SPRING_DATA_REDIS_SSL_ENABLED "
                "CARELINK_EVENTS_TOPICARN CARELINK_EVENTS_QUEUEURL CARELINK_WEBHOOKS_ENCRYPTIONKEY")
        self.stub("kubectl", 'echo "kubectl $*" >> "$CALL_LOG"\n'
                  'if [[ "$*" == *"get configmap"* ]]; then echo public-ca; else\n'
                  f"printf '%s\\n' {keys}\nfi\n")
        self.stub("helm", 'echo "helm $*" >> "$CALL_LOG"\n'
                  'if [ "$1" = template ]; then echo desired; else exit 17; fi\n')
        log = self.directory / "calls"
        result = self.deploy(DRY_RUN="0", KUBE_CONTEXT="test-context", CALL_LOG=str(log),
                             PATH=f"{self.directory}:{os.environ['PATH']}")
        self.assertNotEqual(result.returncode, 0)
        calls = log.read_text().splitlines()
        helm = [call for call in calls if call.startswith("helm upgrade")]
        self.assertEqual(len(helm), 1)
        self.assertTrue(all("--context test-context" in call for call in calls if call.startswith("kubectl")))
        for service, call in zip(("core",), helm):
            self.assertIn(f"upgrade --install {service}", call)
            self.assertIn("--kube-context test-context", call)
            self.assertIn(f'image.digest={self.records[service]["digest"]}', call)
        self.assertTrue(all(call.startswith("kubectl") for call in calls[:5]))


if __name__ == "__main__":
    unittest.main()
