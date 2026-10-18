"""Frontend provenance and failed-upload checks without GitHub or AWS calls."""
from importlib.util import module_from_spec, spec_from_file_location
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]


def load(name):
    spec = spec_from_file_location(name, ROOT / "scripts" / (name + ".py"))
    module = module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


release = load("frontend-release")
publisher = load("publish-frontend")


class FrontendReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.site = self.directory / "site"
        (self.site / "assets").mkdir(parents=True)
        (self.site / "index.html").write_text('<script src="/assets/main-123.js"></script>')
        (self.site / "assets/main-123.js").write_text("console.log('test')")
        self.sha = "a" * 40
        (self.directory / "release.json").write_text(json.dumps({"commit": self.sha, "files": release.files(self.directory)}))

    def test_wrong_commit_changed_missing_and_extra_files_are_rejected(self):
        release.verify(self.directory, self.sha)
        with self.assertRaises(ValueError):
            release.verify(self.directory, "b" * 40)
        asset = self.site / "assets/main-123.js"
        original = asset.read_text()
        asset.write_text("tampered")
        with self.assertRaises(ValueError):
            release.verify(self.directory)
        asset.unlink()
        with self.assertRaises(ValueError):
            release.verify(self.directory)
        asset.write_text(original)
        (self.site / "extra.json").write_text("{}")
        with self.assertRaises(ValueError):
            release.verify(self.directory)

    def test_hidden_secret_and_symlink_are_rejected(self):
        hidden = self.site / ".env"
        hidden.write_text("not-a-real-secret")
        with self.assertRaises(ValueError):
            release.files(self.directory)
        hidden.unlink()
        (self.site / "linked.js").symlink_to(self.site / "assets/main-123.js")
        with self.assertRaises(ValueError):
            release.files(self.directory)

    def test_untrusted_ci_run_is_rejected_before_download(self):
        fake = self.directory / "gh"
        fake.write_text('''#!/usr/bin/env python3
import json, sys
if sys.argv[1] == "api":
 print(json.dumps({"status":"completed","conclusion":"success","head_branch":"main","path":".github/workflows/ci.yml","event":"pull_request","repository":{"id":1408273257},"head_repository":{"id":1408273257}}))
else:
 raise RuntimeError("An untrusted run must never be downloaded")
''')
        fake.chmod(0o700)
        env = dict(os.environ, PATH=f"{self.directory}:{os.environ['PATH']}",
                   GH_REPO="Care-Giver-SE-Team2/carelink-platform", FRONTEND_RUN_ID="123")
        result = subprocess.run(["bash", "scripts/prepare-frontend-release.sh", str(self.directory / "download")],
                                cwd=ROOT, env=env, text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("must never be downloaded", result.stderr)

    def test_assets_precede_index_and_a_failed_upload_does_not_publish_index(self):
        # nosniff requires correct MIME types for the newly self-hosted font files too.
        (self.site / "assets/font.woff2").write_bytes(b"fixture-font")
        (self.site / "assets/theme.css").write_text("body { color: black; }")
        (self.directory / "release.json").write_text(json.dumps({"commit": self.sha, "files": release.files(self.directory)}))
        account = "123456789012"
        bucket = f"carelink-frontend-{account}-staging"
        config = {"Distribution": {"DomainName": "example.cloudfront.net", "DistributionConfig": {
            "DefaultCacheBehavior": {"TargetOriginId": "frontend"},
            "Origins": {"Items": [{"Id": "frontend", "DomainName": bucket + ".s3.ap-southeast-1.amazonaws.com"}]}}}}
        for fail in (False, True):
            with self.subTest(fail=fail):
                calls = []
                def fake_aws(*args):
                    calls.append(args)
                    if args[0] == "sts": return json.dumps({"Account": account})
                    if args[:2] == ("cloudfront", "get-distribution"): return json.dumps(config)
                    if args[0] == "s3" and fail: raise subprocess.CalledProcessError(1, "aws")
                    if args[:2] == ("cloudfront", "create-invalidation"): return '{"Invalidation":{"Id":"I123"}}'
                    return ""
                with patch.dict(os.environ, AWS_ACCOUNT_ID=account), patch.object(publisher, "aws", fake_aws):
                    if fail:
                        with self.assertRaises(subprocess.CalledProcessError):
                            publisher.publish(self.directory, bucket, "E123")
                    else:
                        publisher.publish(self.directory, bucket, "E123")
                uploads = [args[3] for args in calls if args[0] == "s3"]
                self.assertTrue(uploads[0].startswith(f"s3://{bucket}/assets/"))
                if not fail:
                    for suffix, mimes in [(".woff2", {"font/woff2"}), (".css", {"text/css"}),
                                          (".js", {"text/javascript", "application/javascript"})]:
                        upload = next(args for args in calls if args[0] == "s3" and args[3].endswith(suffix))
                        self.assertIn(upload[upload.index("--content-type") + 1], mimes)
                self.assertEqual(f"s3://{bucket}/index.html" in uploads, not fail)
                self.assertEqual(any(args[:2] == ("cloudfront", "create-invalidation") for args in calls), not fail)


if __name__ == "__main__":
    unittest.main()
