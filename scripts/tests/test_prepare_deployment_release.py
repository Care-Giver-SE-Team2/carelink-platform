"""Exercise the publication gate with a fake gh executable, never GitHub API calls."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SERVICES = ("core", "visit", "report", "notification")
REGISTRY = "123456789012.dkr.ecr.ap-southeast-1.amazonaws.com"


class PrepareReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.output = self.directory / "release.json"
        self.metadata = {
            "status": "completed", "conclusion": "success", "head_branch": "main",
            "path": ".github/workflows/ci.yml", "event": "push", "head_sha": "a" * 40,
            "repository": {"id": 1408273257}, "head_repository": {"id": 1408273257},
        }
        self.records = {s: {"service": s, "tag": "a" * 40, "image": f"{REGISTRY}/carelink/{s}",
                            "digest": "sha256:" + str(i) * 64} for i, s in enumerate(SERVICES, 1)}
        fake = self.directory / "gh"
        fake.write_text('''#!/usr/bin/env python3
import json, os, pathlib, sys
root = pathlib.Path(os.environ["FIXTURE"])
args = sys.argv[1:]
with (root / "calls").open("a") as log: log.write(json.dumps(args) + "\\n")
if args[0] == "api":
    if "/artifacts?" in args[1]: print('{"artifacts":[]}')
    else: print((root / "metadata.json").read_text())
elif args[:2] == ["run", "download"]:
    name = args[args.index("--name") + 1]
    service = next(s for s in ("core", "visit", "report", "notification") if name.startswith("ecr-reference-" + s + "-"))
    destination = pathlib.Path(args[args.index("--dir") + 1])
    destination.mkdir(parents=True)
    (destination / "image-record.json").write_text((root / (service + ".json")).read_text())
else:
    sys.exit(99)
''')
        fake.chmod(0o700)

    def run_prepare(self, **changes):
        (self.directory / "metadata.json").write_text(json.dumps(self.metadata))
        for service, record in self.records.items():
            (self.directory / f"{service}.json").write_text(json.dumps(record))
        env = dict(os.environ, PATH=f"{self.directory}:{os.environ['PATH']}", FIXTURE=str(self.directory),
                   GH_REPO="Care-Giver-SE-Team2/carelink-platform", IMAGE_REGISTRY=REGISTRY,
                   **{s.upper() + "_RUN_ID": str(i) for i, s in enumerate(SERVICES, 1)})
        env.update(changes)
        return subprocess.run(["bash", "scripts/prepare-deployment-release.sh", str(self.output)],
                              cwd=ROOT, env=env, text=True, capture_output=True)

    def test_success_builds_complete_release_from_four_selected_runs(self):
        result = self.run_prepare()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(self.output.read_text())["services"], self.records)
        calls = [json.loads(line) for line in (self.directory / "calls").read_text().splitlines()]
        downloads = [call for call in calls if call[0] == "run"]
        self.assertEqual([call[2] for call in downloads], ["1", "2", "3", "4"])

    def test_untrusted_or_failed_run_is_rejected_before_artifact_download(self):
        for changes in ({"conclusion": "failure"}, {"head_branch": "feature/change"}, {"event": "pull_request"},
                        {"path": ".github/workflows/other.yml"}, {"head_repository": {"id": 999}}):
            with self.subTest(changes=changes):
                original = self.metadata.copy()
                self.metadata.update(changes)
                (self.directory / "calls").unlink(missing_ok=True)
                result = self.run_prepare()
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(self.output.exists())
                self.assertNotIn('"download"', (self.directory / "calls").read_text())
                self.metadata = original

    def test_record_commit_must_match_its_ci_run(self):
        self.records["notification"]["tag"] = "b" * 40
        result = self.run_prepare()
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.output.exists())

    def test_run_id_is_not_interpreted_as_a_command(self):
        result = self.run_prepare(CORE_RUN_ID="$(touch unexpected-file)")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.directory / "calls").exists())

    def test_automatic_release_without_complete_artifact_stops_before_download(self):
        result = self.run_prepare(INCREMENTAL_RELEASE="true", CI_RUN_ID="1", RELEASE_SHA="a" * 40)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("requires a complete verified release", result.stderr)
        self.assertFalse(self.output.exists())
        self.assertNotIn('"download"', (self.directory / "calls").read_text())

    def test_old_single_run_manual_rollback_uses_legacy_image_records(self):
        result = self.run_prepare(CI_RUN_ID="1", VISIT_RUN_ID="1", REPORT_RUN_ID="1", NOTIFICATION_RUN_ID="1")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(self.output.read_text())["services"], self.records)

    def test_failed_preflight_prevents_deployment(self):
        fake = self.directory / "python3"
        fake.write_text('#!/bin/sh\nprintf "%s\\n" "$*" > "$FIXTURE/preflight"\nexit 42\n')
        fake.chmod(0o700)
        env = dict(os.environ, PATH=f"{self.directory}:{os.environ['PATH']}", FIXTURE=str(self.directory),
                   RELEASE_MANIFEST="release.json", IMAGE_REGISTRY=REGISTRY, KUBE_CONTEXT="runtime",
                   IMAGE_TAG="", DRY_RUN="0", AWS_PROFILE="carelink")
        for profile in ("carelink", None):
            with self.subTest(profile=profile):
                if profile is None:
                    env.pop("AWS_PROFILE", None)
                    env.pop("AWS_DEFAULT_PROFILE", None)
                result = subprocess.run(["/bin/bash", "scripts/deploy-release.sh", "staging"], cwd=ROOT,
                                        env=env, text=True, capture_output=True)
                self.assertEqual(result.returncode, 42, result.stderr)
                command = (self.directory / "preflight").read_text()
                if profile:
                    self.assertIn("--profile carelink", command)
                else:
                    self.assertNotIn("--profile", command)


if __name__ == "__main__":
    unittest.main()
