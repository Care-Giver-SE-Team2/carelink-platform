"""Real Git histories prove reuse cannot miss failed/intervening changes."""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("ci_release", Path(__file__).resolve().parents[1] / "ci-release.py")
ci = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ci)
REGISTRY = "123456789012.dkr.ecr.ap-southeast-1.amazonaws.com"


class IncrementalReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        previous = os.getcwd()
        os.chdir(self.repo)
        self.addCleanup(os.chdir, previous)
        self.git("init", "-q")
        self.git("config", "user.name", "Release test")
        self.git("config", "user.email", "test@example.invalid")
        for s in ci.SERVICES:
            self.write(f"services/{s}/pom.xml", "pom")
            self.write(f"services/{s}/src/app.java", s)
        self.write("pom.xml", "parent")
        self.write("libs/shared/code.java", "shared")
        self.write("frontend/index.html", "frontend")
        self.base = self.commit()
        self.inputs = self.root / "inputs"
        self.inputs.mkdir()
        self.bundle(self.inputs / "baseline", self.base)

    def git(self, *args):
        return subprocess.check_output(["git", *args], stderr=subprocess.DEVNULL).decode().strip()

    def write(self, name, value):
        path = self.repo / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)

    def commit(self):
        self.git("add", ".")
        self.git("commit", "-qm", "fixture")
        return self.git("rev-parse", "HEAD")

    def bundle(self, directory, sha):
        site = directory / "frontend/site"
        site.mkdir(parents=True)
        (site / "index.html").write_text(self.git("show", sha + ":frontend/index.html"))
        (site / "version.json").write_text(json.dumps({"commit": sha}) + "\n")
        (directory / "frontend/release.json").write_text(json.dumps({"commit": sha, "files": ci.frontend.files(directory / "frontend")}))
        records = {s: {"service": s, "tag": sha, "image": f"{REGISTRY}/carelink/{s}",
                       "digest": "sha256:" + str(i) * 64} for i, s in enumerate(ci.SERVICES, 1)}
        (directory / "release.json").write_text(json.dumps({"schema_version": 1, "services": records}))
        (directory / "inputs.json").write_text(json.dumps({"commit": sha, "fingerprints": ci.fingerprints(sha),
                                                          "image_commits": {s: sha for s in ci.SERVICES}}))

    def make_plan(self, sha, baseline=True):
        old = ci.fingerprints(self.base) if baseline else {}
        current = ci.fingerprints(sha)
        document = {"commit": sha, "baseline": self.base if baseline else None, "fingerprints": current,
                    "services": [s for s in ci.SERVICES if old.get(s) != current[s]],
                    "frontend": old.get("frontend") != current["frontend"]}
        (self.inputs / "plan.json").write_text(json.dumps(document))
        return document

    def assemble(self, sha, plan):
        downloads = []
        def download(run, name, destination):
            downloads.append(name)
            if name.startswith("frontend-dist-"):
                self.bundle(self.root / "built", sha)
                shutil.copytree(self.root / "built/frontend", destination)
            else:
                s = next(s for s in ci.SERVICES if name == f"ecr-reference-{s}-{sha}")
                destination.mkdir()
                record = {"service": s, "tag": sha, "image": f"{REGISTRY}/carelink/{s}", "digest": "sha256:" + "f" * 64}
                (destination / "image-record.json").write_text(json.dumps(record))
        with patch.dict(os.environ, GITHUB_RUN_ID="123"), patch.object(ci, "download", download):
            ci.assemble(self.inputs, self.root / "result", REGISTRY)
        return json.loads((self.root / "result/release.json").read_text()), downloads

    def test_frontend_only_reuses_all_images_and_records_latest_public_commit(self):
        self.write("frontend/index.html", "new frontend")
        sha = self.commit()
        plan = self.make_plan(sha)
        self.assertEqual(plan["services"], [])
        records, downloads = self.assemble(sha, plan)
        self.assertEqual(downloads, ["frontend-dist-" + sha])
        self.assertTrue(all(r["tag"] == self.base for r in records["services"].values()))
        ci.verify(self.root / "result", sha, REGISTRY)

    def test_intervening_failed_service_change_is_not_lost_on_next_push(self):
        self.write("services/visit/src/app.java", "failed previous main")
        self.commit()
        self.write("services/report/src/app.java", "next main")
        sha = self.commit()
        plan = self.make_plan(sha)
        self.assertEqual(plan["services"], ["visit", "report"])
        self.assertFalse(plan["frontend"])
        records, downloads = self.assemble(sha, plan)
        self.assertEqual(len(downloads), 2)
        self.assertEqual(records["services"]["core"]["tag"], self.base)
        self.assertEqual(records["services"]["visit"]["tag"], sha)
        self.assertEqual(json.loads((self.root / "result/frontend/site/version.json").read_text())["commit"], sha)
        self.assertEqual((self.root / "result/frontend/site/index.html").read_text(), "frontend")

    def test_shared_library_and_other_service_pom_invalidate_all_backends(self):
        for path in ("libs/shared/code.java", "services/visit/pom.xml"):
            self.write(path, "changed")
            sha = self.commit()
            self.assertEqual(self.make_plan(sha)["services"], list(ci.SERVICES))

    def test_missing_baseline_requires_every_component(self):
        plan = self.make_plan(self.base, baseline=False)
        self.assertEqual(plan["services"], list(ci.SERVICES))
        self.assertTrue(plan["frontend"])

    def test_omitted_frontend_build_is_rejected_before_copy_or_download(self):
        self.write("frontend/index.html", "changed")
        sha = self.commit()
        plan = self.make_plan(sha)
        plan["frontend"] = False
        (self.inputs / "plan.json").write_text(json.dumps(plan))
        with patch.object(ci, "download") as download, self.assertRaises(ValueError):
            ci.assemble(self.inputs, self.root / "result", REGISTRY)
        download.assert_not_called()

    def test_stale_backend_cannot_be_hidden_by_current_release_marker(self):
        self.write("services/visit/src/app.java", "changed")
        sha = self.commit()
        directory = self.inputs / "baseline"
        proof = json.loads((directory / "inputs.json").read_text())
        proof.update(commit=sha, fingerprints=ci.fingerprints(sha))
        (directory / "inputs.json").write_text(json.dumps(proof))
        with self.assertRaisesRegex(ValueError, "image source differs"):
            ci.verify(directory, sha, REGISTRY)

    def test_tampered_frontend_asset_is_rejected(self):
        (self.inputs / "baseline/frontend/site/index.html").write_text("tampered")
        with self.assertRaises(ValueError):
            ci.verify(self.inputs / "baseline", self.base, REGISTRY)

    def test_unrelated_or_failed_ci_cannot_supply_a_baseline(self):
        runs = {"workflow_runs": [{"status": "completed", "conclusion": "failure"},
                                   {"status": "completed", "conclusion": "success", "event": "pull_request"}]}
        with patch.object(ci, "gh", return_value=json.dumps(runs)), patch.object(ci, "download") as download:
            self.assertIsNone(ci.baseline(self.root / "old", self.base, REGISTRY))
        download.assert_not_called()

    def test_chart_change_does_not_rebuild_source_identical_images(self):
        self.write("charts/carelink-service/values.yaml", "new resources")
        plan = self.make_plan(self.commit())
        self.assertEqual(plan["services"], [])
        self.assertFalse(plan["frontend"])

    def test_main_planning_uses_successful_baseline_not_previous_push(self):
        self.write("services/visit/src/app.java", "failed previous push")
        before = self.commit()
        self.write("frontend/index.html", "next push")
        sha = self.commit()
        directory = self.root / "main-plan"
        with patch.dict(os.environ, EVENT="push", GITHUB_REF="refs/heads/main", BASE=before,
                        GITHUB_OUTPUT=str(self.root / "outputs")), patch.object(ci, "baseline", return_value=self.base):
            ci.plan(directory, REGISTRY)
        plan = json.loads((directory / "plan.json").read_text())
        self.assertEqual(plan["commit"], sha)
        self.assertEqual(plan["services"], ["visit"])
        self.assertTrue(plan["frontend"])

    def test_expired_complete_artifact_falls_back_to_full_build(self):
        self.write("README.md", "next main")
        sha = self.commit()
        metadata = {"id": 123, "status": "completed", "conclusion": "success", "head_branch": "main",
                    "path": ".github/workflows/ci.yml", "event": "push", "head_sha": self.base,
                    "repository": {"id": 1408273257}, "head_repository": {"id": 1408273257}}
        responses = [json.dumps({"workflow_runs": [metadata]}), json.dumps({"artifacts": [
            {"name": "verified-release-" + self.base, "expired": True}]})]
        with patch.dict(os.environ, EVENT="push", GITHUB_REF="refs/heads/main",
                        GITHUB_OUTPUT=str(self.root / "outputs")), patch.object(ci, "gh", side_effect=responses), \
                patch.object(ci, "download") as download:
            ci.plan(self.root / "expired-plan", REGISTRY)
        download.assert_not_called()
        plan = json.loads((self.root / "expired-plan/plan.json").read_text())
        self.assertEqual(plan["commit"], sha)
        self.assertEqual(plan["services"], list(ci.SERVICES))
        self.assertTrue(plan["frontend"])


if __name__ == "__main__":
    unittest.main()
