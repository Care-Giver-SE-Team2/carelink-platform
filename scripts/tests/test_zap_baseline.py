"""Offline checks: no Docker, AWS, public requests or security scan is executed."""
import copy
import importlib.util
import json
from pathlib import Path
import re
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from xml.etree import ElementTree

spec = importlib.util.spec_from_file_location("zap_baseline", Path(__file__).resolve().parents[1] / "run-zap-baseline.py")
zap = importlib.util.module_from_spec(spec)
spec.loader.exec_module(zap)
spec = importlib.util.spec_from_file_location("zap_hooks", Path(__file__).resolve().parents[1] / "zap-baseline-hooks.py")
hooks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hooks)


class BaselineSourceTest(unittest.TestCase):
    def setUp(self):
        self.sha = "a" * 40
        self.run = {"status": "completed", "conclusion": "success", "head_branch": "main",
                    "event": "workflow_run", "path": ".github/workflows/deploy.yml",
                    "head_sha": self.sha, "repository": {"id": zap.REPOSITORY_ID},
                    "head_repository": {"id": zap.REPOSITORY_ID}}
        self.jobs = [{"name": name, "conclusion": "success"}
                     for name in ("release", "deploy", "frontend / deploy", "acceptance")]

    def test_requires_successful_accepted_same_repository_staging(self):
        self.assertEqual(zap.accepted_commit(self.run, self.jobs), self.sha)
        for change in ({"status": "in_progress"}, {"conclusion": "failure"},
                       {"head_branch": "feature/test"}, {"event": "workflow_dispatch"},
                       {"path": ".github/workflows/ci.yml"}, {"repository": {"id": 1}},
                       {"head_repository": {"id": 1}}, {"head_sha": "main"}):
            with self.subTest(change=change), self.assertRaises(ValueError):
                zap.accepted_commit({**self.run, **change}, self.jobs)

    def test_missing_skipped_or_failed_acceptance_cannot_authorize_a_scan(self):
        for status in ("failure", "skipped", "cancelled", None):
            jobs = copy.deepcopy(self.jobs)
            jobs[-1]["conclusion"] = status
            with self.subTest(status=status), self.assertRaises(ValueError):
                zap.accepted_commit(self.run, jobs)
        with self.assertRaises(ValueError):
            zap.accepted_commit(self.run, self.jobs[:-1])

    def test_scope_excludes_api_other_hosts_and_non_static_routes(self):
        scope = ElementTree.fromstring(zap.context_xml()).findtext("context/incregexes")
        for path in ("/", "/index.html", "/version.json", "/assets/index-123.js", "/assets/font_1.woff2"):
            self.assertIsNotNone(re.fullmatch(scope, zap.TARGET + path))
        for url in (zap.TARGET + "/api/auth/logout", zap.TARGET + "/admin/delete",
                    "https://example.com/", zap.TARGET + ".example.com/",
                    "http://dzfs8sv334fwf.cloudfront.net/", zap.TARGET + "/assets/../api/auth/logout",
                    zap.TARGET + "/assets/%2e%2e/api/auth/logout", zap.TARGET + "/assets/%252e%252e%252fapi",
                    zap.TARGET + "/assets/x.js?delete=1", zap.TARGET + "/?delete=1",
                    zap.TARGET + "/assets/x.js/../../api", zap.TARGET + "/assets/x..js",
                    zap.TARGET + "/assets/x\\..\\api.js", zap.TARGET + "/assets//x.js"):
            self.assertIsNone(re.fullmatch(scope, url))

    def test_version_redirect_is_rejected(self):
        with self.assertRaises(ValueError):
            zap.NoRedirect().redirect_request(None, None, 302, "", {}, "https://example.com/")

    def test_seed_get_uses_api_without_redirects_and_rejects_redirect_or_failure(self):
        for status in ("200 OK", "301 Moved", "302 Found", "307 Temporary", "308 Permanent", "403 Forbidden"):
            core = SimpleNamespace(access_url=unittest.mock.Mock(return_value=[{
                "responseHeader": "HTTP/1.1 " + status + "\r\n", "responseBody": "ok"}]))
            client = SimpleNamespace(core=core)
            hooks.zap_access_target(client, zap.TARGET + "/")
            if status == "200 OK":
                self.assertEqual(client.urlopen(zap.TARGET + "/"), "ok")
            else:
                with self.assertRaises(ValueError):
                    client.urlopen(zap.TARGET + "/")
            core.access_url.assert_called_once_with(zap.TARGET + "/", followredirects="false")
            with self.assertRaises(ValueError):
                client.urlopen("https://example.com/")

    def test_raw_spider_exclusion_is_installed_or_scan_stops(self):
        for result in ("OK", "error"):
            api = unittest.mock.Mock(return_value=result)
            client = SimpleNamespace(spider=SimpleNamespace(exclude_from_scan=api))
            if result == "OK":
                hooks.zap_started(client, zap.TARGET + "/")
            else:
                with self.assertRaises(ValueError):
                    hooks.zap_started(client, zap.TARGET + "/")
            regex = api.call_args.args[0]
            for url in (zap.TARGET + "/?delete=1", zap.TARGET + "/assets/x.js?delete=1",
                        zap.TARGET + "/assets/%2e%2e/x.js"):
                self.assertIsNotNone(re.fullmatch(regex, url))

    def test_disabled_script_stops_before_any_github_or_target_request(self):
        with patch.dict(zap.os.environ, {}, clear=True), patch.object(zap, "gh") as api:
            with patch("sys.argv", ["zap", "--run-id", "123", "--output", "/unused"]):
                with self.assertRaises(ValueError):
                    zap.main()
            api.assert_not_called()

    def execute_mock_scan(self, output, code, versions, reports=True):
        def run(command, **kwargs):
            if command[:2] == ["docker", "run"] and reports:
                (output / "report.json").write_text('{}')
                (output / "report.html").write_text('<html></html>')
            return type("Result", (), {"returncode": code})()

        metadata = {**self.run, "run_attempt": 1, "html_url": "https://github.com/example/run/123"}
        with patch.dict(zap.os.environ, {"CARELINK_ZAP_ENABLED": "true"}):
            with patch("sys.argv", ["zap", "--run-id", "123", "--output", str(output)]):
                with patch.object(zap, "gh", side_effect=[metadata, {"jobs": self.jobs}]):
                    with patch.object(zap, "public_commit", side_effect=versions):
                        with patch.object(zap.subprocess, "run", side_effect=run):
                            with patch.object(zap.subprocess, "check_output", return_value=json.dumps(
                                    [{"Id": "sha256:" + "b" * 64, "RepoDigests": ["scanner@sha256:example"]}])):
                                zap.main()

    def test_only_clean_scan_with_reports_and_unchanged_release_is_accepted(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory)
            self.execute_mock_scan(output, 0, [self.sha, self.sha])
            evidence = json.loads((output / "release-evidence.json").read_text())
            self.assertTrue(evidence["accepted"])
            self.assertTrue(evidence["completed"])
            self.assertEqual(evidence["zap_exit_code"], 0)

    def test_findings_missing_reports_and_release_change_are_not_clean_passes(self):
        for code, version, reports in ((1, self.sha, True), (2, self.sha, True),
                                       (3, self.sha, True), (0, "c" * 40, True), (0, self.sha, False)):
            with self.subTest(code=code, version=version, reports=reports):
                with tempfile.TemporaryDirectory() as directory:
                    output = Path(directory)
                    with self.assertRaises(ValueError):
                        self.execute_mock_scan(output, code, [self.sha, version], reports)
                    evidence = json.loads((output / "release-evidence.json").read_text())
                    self.assertFalse(evidence["accepted"])
