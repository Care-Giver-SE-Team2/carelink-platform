"""Offline semantics and real loopback HTTP tests, never cloud requests."""
import http.server
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import threading
import time
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("visit_load", ROOT / "load.py")
load = importlib.util.module_from_spec(spec)
spec.loader.exec_module(load)


def config():
    c = load.read_json(ROOT / "example.local.json")
    c["phases"] = [{"name": "pilot", "rps": 2, "durationSeconds": 1}]
    c["maxVUs"] = 1
    return c


class GuardTests(unittest.TestCase):
    def test_offline_validation_does_not_open_network(self):
        with patch.object(load.urllib.request.OpenerDirector, "open", side_effect=AssertionError("network")):
            self.assertEqual(load.validate(config())["plannedIterations"], 2)

    def test_unapproved_remote_blocked_before_network(self):
        c = config(); c["coreBase"] = c["visitBase"] = "https://test.example.invalid"
        self.assertFalse(load.validate(c)["localhost"])
        with patch.object(load.urllib.request.OpenerDirector, "open", side_effect=AssertionError("network")):
            with self.assertRaisesRegex(ValueError, "remote_target_not_confirmed"):
                load.run(c, "must-not-create-this-directory")

    def test_remote_requires_exact_approved_origin(self):
        c = config(); c["coreBase"] = c["visitBase"] = "https://test.example.invalid"
        c["approval"] = {"confirmed": True, "environment": "isolated-test", "windowUtc": "agreed", "owner": "team", "deploymentRef": "sha", "allowedOrigins": [c["coreBase"]]}
        self.assertTrue(load.validate(c, execute=True)["remoteExecutionConfirmed"])
        c["approval"]["allowedOrigins"] = ["https://different.example.invalid"]
        with self.assertRaises(ValueError): load.validate(c, execute=True)

    def test_rejects_credentials_paths_and_mixed_session_host(self):
        for url in ("https://user:secret@example.invalid", "http://127.0.0.1/api", "http://127.0.0.1?token=secret"):
            with self.subTest(url=url), self.assertRaises(ValueError): load.origin(url)
        c = config(); c["visitBase"] = "http://localhost:18081"
        with self.assertRaises(ValueError): load.validate(c)

    def test_write_pool_is_finite_and_unique(self):
        c = config(); c["scenario"] = "check-in"
        with self.assertRaisesRegex(ValueError, "insufficient_fresh"):
            load.validate(c)
        c["actors"][0]["visits"] *= 2
        with self.assertRaisesRegex(ValueError, "duplicate_visit"):
            load.validate(c)

    def test_rejects_nonfinite_negative_and_excessive_load(self):
        for rate in (float("nan"), float("inf"), -1, 1001):
            c = config(); c["phases"][0]["rps"] = rate
            with self.assertRaises(ValueError): load.validate(c)
        c = config(); c["maxIterations"] = 1
        with self.assertRaises(ValueError): load.validate(c)

    def test_two_bad_windows_stop_and_empty_window_resets(self):
        c = {"errorWindowSeconds": 1, "latencyWindowSeconds": 2, "errorRate": .05, "p95Ms": 5000, "minSamples": 1, "consecutiveWindows": 2}
        w = load.WindowStop(c, 0)
        row = lambda t: {"finishedMono": t, "passed": False, "elapsedMs": 10}
        self.assertIsNone(w.evaluate(1, [row(.5)]))
        self.assertEqual(w.evaluate(2, [row(.5), row(1.5)]), "consecutive_errors_windows")
        w = load.WindowStop(c, 0)
        self.assertIsNone(w.evaluate(1, [row(.5)]))
        self.assertIsNone(w.evaluate(2, [row(.5)]))
        self.assertIsNone(w.evaluate(3, [row(.5), row(2.5)]))

    def test_latency_windows_stop(self):
        c = {"errorWindowSeconds": 1, "latencyWindowSeconds": 1, "errorRate": 1, "p95Ms": 20, "minSamples": 1, "consecutiveWindows": 2}
        w = load.WindowStop(c, 0)
        rows = [{"finishedMono": .5, "passed": True, "elapsedMs": 30}, {"finishedMono": 1.5, "passed": True, "elapsedMs": 30}]
        self.assertEqual(w.evaluate(2, rows), "consecutive_latency_windows")

    def test_small_samples_are_not_sla_pass_and_failed_http_not_hidden(self):
        c = config(); load.validate(c)
        rows = [{"phase": "pilot", "method": "GET", "endpoint": "work", "elapsedMs": ms, "httpPassed": ok, "businessPassed": ok} for ms, ok in ((4, True), (5000, False))]
        group = load.summarize(rows, [], c["phases"], c["targets"])[0]["endpoints"][0]
        self.assertEqual((group["allP95Ms"], group["successP95Ms"], group["httpFailures"]), (5000, 4, 1))
        self.assertEqual(group["targetObservation"], "INSUFFICIENT_SAMPLES")


class LoopbackTests(unittest.TestCase):
    def setUp(self):
        self.mode = "normal"
        self.posts = []
        self.gets = []
        self.versions = {1: 0, 2: 0}
        self.delay = 0
        owner = self
        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *args): pass
            def respond(self, code, value=None, headers=None, content_type="application/json"):
                self.send_response(code)
                self.send_header("Content-Type", content_type)
                for k, v in (headers or {}).items(): self.send_header(k, v)
                self.end_headers()
                try: self.wfile.write(json.dumps(value).encode() if value is not None else b"")
                except OSError: pass  # An intentional client timeout closes the loopback socket.
            def do_GET(self):
                owner.gets.append(self.path)
                if self.path == "/api/auth/csrf": return self.respond(200, headers={"Set-Cookie": "XSRF-TOKEN=csrf-secret; Path=/"})
                if self.path == "/api/auth/me": return self.respond(200, {"id": 71100, "roles": ["CAREGIVER"]})
                if "SESSION=session-secret" not in self.headers.get("Cookie", ""):
                    return self.respond(401)
                ident = int(self.path.split("/")[3])
                time.sleep(owner.delay)
                if owner.mode == "redirect": return self.respond(302, headers={"Location": "http://example.invalid/never-follow"})
                if owner.mode == "unauthorized": return self.respond(403)
                if owner.mode == "server": return self.respond(500)
                if owner.mode == "html": return self.respond(200, content_type="text/html")
                version = owner.versions[ident]
                return self.respond(200, {"visit": {"id": ident if owner.mode != "wrong" else 999, "elderId": 73100,
                    "version": version, "status": "SCHEDULED" if version == 0 else "IN_PROGRESS"}, "tasks": [] if version == 0 else [{"id": 5}], "execution": {"allowedActions": ["CHECK_IN"]}})
            def do_POST(self):
                body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                owner.posts.append((self.path, body))
                if self.path == "/api/auth/login": return self.respond(200, {"id": 71100, "roles": ["CAREGIVER"]}, {"Set-Cookie": "SESSION=session-secret; Path=/"})
                if self.headers.get("X-XSRF-TOKEN") != "csrf-secret": return self.respond(403)
                ident = int(self.path.split("/")[3]); owner.versions[ident] += 1
                if owner.mode == "post-timeout": time.sleep(.3)
                return self.respond(200, {"visitId": ident, "visitVersion": 1, "savedState": "IN_PROGRESS", "replayed": owner.mode == "replay"})
        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True); self.thread.start()
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup); self.addCleanup(self.server.server_close); self.addCleanup(self.server.shutdown)
        self.env = patch.dict(os.environ, {"HPA_CG1_USERNAME": "username-secret", "HPA_CG_PASSWORD": "password-secret"})
        self.env.start(); self.addCleanup(self.env.stop)

    def cfg(self):
        c = config(); c["coreBase"] = c["visitBase"] = f"http://127.0.0.1:{self.server.server_port}"
        return c

    def execute(self, c):
        out = Path(self.temp.name) / "run"
        code = load.run(c, out)
        return code, load.read_json(out / "summary.json"), out

    def test_real_http_login_cookies_csrf_and_read_success(self):
        code, s, out = self.execute(self.cfg())
        self.assertEqual((code, s["launchedIterations"], s["failedIterations"]), (0, 2, 0))
        for file in out.iterdir():
            data = file.read_text()
            for secret in ("username-secret", "password-secret", "session-secret", "csrf-secret"):
                self.assertNotIn(secret, data)
        with self.assertRaises(FileExistsError): load.run(self.cfg(), out)

    def test_first_checkins_are_unique_and_postverified(self):
        c = self.cfg(); c["scenario"] = "check-in"
        c["actors"][0]["visits"].append({**c["actors"][0]["visits"][0], "visitId": 2})
        code, s, _ = self.execute(c)
        self.assertEqual((code, s["failedIterations"], self.versions), (0, 0, {1: 1, 2: 1}))
        writes = [b for p, b in self.posts if p.endswith("/check-in")]
        self.assertEqual(len({b["clientRequestId"] for b in writes}), 2)
        self.assertEqual(s["httpRequestsDuringLoad"], 6)

    def test_redirect_auth_html_wrong_visit_and_replay_fail_closed(self):
        # Failures here intentionally happen during setup, proving no load is issued.
        for mode in ("redirect", "unauthorized", "html", "wrong"):
            self.mode = mode
            with self.subTest(mode=mode):
                out = Path(self.temp.name) / mode
                self.assertEqual(load.run(self.cfg(), out), 2)
                self.assertEqual(load.read_json(out / "summary.json")["launchedIterations"], 0)
        self.assertFalse(any("never-follow" in p for p in self.gets))

    def test_replayed_checkin_is_not_new_throughput(self):
        self.mode = "replay"
        c = self.cfg(); c["scenario"] = "check-in"; c["phases"][0]["rps"] = 1
        code, s, _ = self.execute(c)
        self.assertEqual(code, 2)
        self.assertIn("checkin_not_a_fresh_success", s["stopReasons"])
        self.assertEqual(s["failedIterations"], 1)

    def test_slow_generator_drops_without_queue_or_retries(self):
        self.delay = .08
        c = self.cfg(); c["phases"][0].update(rps=50, durationSeconds=.4)
        code, s, _ = self.execute(c)
        self.assertEqual(code, 2)
        self.assertGreater(s["droppedIterations"], 0)
        self.assertEqual(s["plannedIterations"], s["launchedIterations"] + s["droppedIterations"])
        self.assertEqual(s["httpRequestsDuringLoad"], s["launchedIterations"])

    def test_operator_stop_file_prevents_load(self):
        out = Path(self.temp.name) / "run"
        original = load.Client.login
        def stop_after_login(client):
            original(client); (out / "STOP").touch()
        with patch.object(load.Client, "login", stop_after_login):
            code = load.run(self.cfg(), out)
        s = load.read_json(out / "summary.json")
        self.assertEqual((code, s["launchedIterations"], s["notScheduledAfterStop"]), (2, 0, 2))

    def test_auth_failure_during_load_stops_remaining_arrivals(self):
        original = load.Client.iteration
        def expire(client, *args):
            self.mode = "unauthorized"
            return original(client, *args)
        with patch.object(load.Client, "iteration", expire):
            code, s, _ = self.execute(self.cfg())
        self.assertEqual((code, s["launchedIterations"], s["failedIterations"], s["notScheduledAfterStop"]), (2, 1, 1, 1))
        self.assertIn("authentication_or_contract_http", s["stopReasons"])

    def test_server_failures_trigger_runtime_window_stop(self):
        c = self.cfg(); c["phases"][0].update(rps=4, durationSeconds=2.5)
        c["stop"].update(errorWindowSeconds=1, latencyWindowSeconds=1, minSamples=1)
        original = load.Client.iteration
        def fail(client, *args):
            self.mode = "server"
            return original(client, *args)
        with patch.object(load.Client, "iteration", fail):
            code, s, _ = self.execute(c)
        self.assertEqual(code, 2)
        self.assertIn("consecutive_errors_windows", s["stopReasons"])
        self.assertGreater(s["notScheduledAfterStop"], 0)

    def test_timed_out_write_not_retried_or_counted_success(self):
        self.mode = "post-timeout"
        c = self.cfg(); c.update(scenario="check-in", socketTimeoutSeconds=.1)
        c["phases"][0]["rps"] = 1
        code, s, _ = self.execute(c)
        self.assertEqual((code, s["failedIterations"], self.versions[1]), (2, 1, 1))
        self.assertEqual(len([p for p, b in self.posts if p.endswith("/check-in")]), 1)
        self.assertEqual(s["httpRequestsDuringLoad"], 2)


if __name__ == "__main__": unittest.main()
