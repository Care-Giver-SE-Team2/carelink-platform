"""Reject stale/untrusted CI releases and image reuse without source provenance."""
import copy
import importlib.util
from pathlib import Path
import unittest


def load(name):
    path = Path(__file__).resolve().parents[1] / (name + ".py")
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


select = load("select-ci-release")
codebuild = load("run-codebuild-deployment")


class AutomaticReleaseTest(unittest.TestCase):
    def setUp(self):
        self.sha = "a" * 40
        self.metadata = {"status": "completed", "conclusion": "success", "head_branch": "main",
                         "path": ".github/workflows/ci.yml", "event": "push", "head_sha": self.sha,
                         "repository": {"id": 1408273257}, "head_repository": {"id": 1408273257}}

    def test_successful_current_main_is_selected(self):
        self.assertEqual(select.select(self.metadata, self.sha, True), self.sha)

    def test_superseded_automatic_release_is_rejected_but_manual_rollback_is_allowed(self):
        with self.assertRaises(ValueError):
            select.select(self.metadata, "b" * 40, True)
        self.assertEqual(select.select(self.metadata, "b" * 40, False), self.sha)

    def test_failed_pr_fork_and_other_workflows_cannot_deploy(self):
        for changes in ({"conclusion": "failure"}, {"status": "in_progress"}, {"event": "pull_request"},
                        {"head_branch": "feature/x"}, {"path": ".github/workflows/other.yml"},
                        {"head_repository": {"id": 1}}, {"repository": {"id": 1}}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                select.select({**self.metadata, **changes}, self.sha, True)


class CodeBuildBundleTest(unittest.TestCase):
    def setUp(self):
        self.sha, self.account = "a" * 40, "123456789012"
        self.bucket = f"carelink-tfstate-{self.account}-ap-southeast-1"
        self.manifest = {"services": {s: {"service": s, "tag": self.sha,
            "image": f"{self.account}.dkr.ecr.ap-southeast-1.amazonaws.com/carelink/{s}",
            "digest": "sha256:" + "b" * 64} for s in ("core", "visit", "report", "notification")}}

    def validate(self, **kwargs):
        codebuild.validate(kwargs.get("manifest", self.manifest), self.sha, self.account,
                           kwargs.get("bucket", self.bucket), "carelink-staging-deploy")

    def test_complete_same_commit_bundle_is_accepted(self):
        self.validate()

    def test_private_source_cannot_be_uploaded_to_the_public_frontend_bucket(self):
        with self.assertRaises(ValueError):
            self.validate(bucket=f"carelink-frontend-{self.account}-staging")

    def test_partial_mixed_commit_and_other_account_images_are_rejected(self):
        for change in ("partial", "commit", "account", "digest"):
            manifest = copy.deepcopy(self.manifest)
            if change == "partial":
                del manifest["services"]["core"]
            elif change == "commit":
                manifest["services"]["core"]["tag"] = "c" * 40
            elif change == "account":
                manifest["services"]["core"]["image"] = "other.example/core"
            else:
                manifest["services"]["core"]["digest"] = "latest"
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.validate(manifest=manifest)

    def test_verified_image_sources_are_required_for_incremental_bundles(self):
        self.manifest["services"]["visit"]["tag"] = "c" * 40
        proof = {"commit": self.sha, "fingerprints": {s: "d" * 64 for s in (*self.manifest["services"], "frontend")},
                 "image_commits": {s: r["tag"] for s, r in self.manifest["services"].items()}}
        codebuild.validate(self.manifest, self.sha, self.account, self.bucket, "carelink-staging-deploy", proof)
        proof["image_commits"]["visit"] = self.sha
        with self.assertRaises(ValueError):
            codebuild.validate(self.manifest, self.sha, self.account, self.bucket, "carelink-staging-deploy", proof)
