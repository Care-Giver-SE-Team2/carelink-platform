"""D1: real loopback slow I/O, owned CLI processes and native interrupt events."""
import contextlib
import ctypes
import datetime as dt
import http.server
import json
import os
from pathlib import Path
import signal
import socket
import socketserver
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
import urllib.request

from test_load import load, config

TOOL = Path(load.__file__).resolve()
EVIDENCE = os.environ.get("VISIT_HPA_TEST_EVIDENCE")


def evidence(name, **fields):
    if EVIDENCE:
        with Path(EVIDENCE).open("a", encoding="utf-8") as stream:
            stream.write(json.dumps({"case": name, **fields}) + "\n")


def alive(pid):
    if os.name != "nt":
        try:
            os.kill(pid, 0)
            return True
        except ProcessLookupError:
            return False
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    kernel.OpenProcess.restype = ctypes.c_void_p
    kernel.GetExitCodeProcess.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_ulong)]
    kernel.CloseHandle.argtypes = [ctypes.c_void_p]
    handle = kernel.OpenProcess(0x1000, False, pid)
    if not handle:
        return False
    try:
        code = ctypes.c_ulong()
        return bool(kernel.GetExitCodeProcess(handle, ctypes.byref(code))) and code.value == 259
    finally:
        kernel.CloseHandle(handle)


def interrupt_cli(process):
    if os.name != "nt":
        os.kill(process.pid, signal.SIGINT)
        return
    # Target owns a fresh hidden console. A separate helper attaches only to that
    # console, ignores its own CTRL_C, broadcasts the native event, then detaches.
    code = (
        "import ctypes,sys,time; k=ctypes.WinDLL('kernel32',use_last_error=True); "
        "k.FreeConsole(); "
        "assert k.AttachConsole(int(sys.argv[1])), ctypes.get_last_error(); "
        "assert k.SetConsoleCtrlHandler(None,True); "
        "assert k.GenerateConsoleCtrlEvent(0,0), ctypes.get_last_error(); "
        "time.sleep(.15); k.FreeConsole()"
    )
    result = subprocess.run([sys.executable, "-c", code, str(process.pid)],
                            capture_output=True, timeout=4, creationflags=subprocess.CREATE_NO_WINDOW)
    if result.returncode:
        raise AssertionError("native CTRL_C injection failed: " + result.stderr.decode(errors="replace"))


class DeadlineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="visit-d1-")
        self.root = Path(self.temp.name).resolve()
        assert self.root.parent == Path(tempfile.gettempdir()).resolve()
        self.release = threading.Event()
        self.entered = threading.Condition()
        self.slow_count = 0
        self.sessions, self.requests, self.processes, self.clients = {}, [], [], []
        self.mode = "normal"
        self.enter_slow_on_setup = False
        owner = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def handle(self):
                with contextlib.suppress(BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
                    super().handle()

            def respond(self, value, headers=None):
                raw = json.dumps(value).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(raw)))
                for k, v in (headers or {}).items():
                    self.send_header(k, v)
                self.end_headers()
                with contextlib.suppress(OSError):
                    self.wfile.write(raw)

            def do_POST(self):
                self.rfile.read(int(self.headers["Content-Length"]))
                session = "test-session-" + str(len(owner.sessions))
                owner.sessions[session] = 0
                self.respond({"id": 71100, "roles": ["CAREGIVER"]},
                             {"Set-Cookie": "SESSION=" + session + "; Path=/"})

            def do_GET(self):
                owner.requests.append((self.path, time.monotonic()))
                if self.path == "/legacy-drip":
                    self.send_response(200)
                    self.send_header("Content-Length", "12")
                    self.end_headers()
                    with contextlib.suppress(OSError):
                        for _ in range(12):
                            self.wfile.write(b"x"); self.wfile.flush(); time.sleep(.03)
                    return
                if self.path == "/api/auth/csrf":
                    return self.respond(None, {"Set-Cookie": "XSRF-TOKEN=test-csrf; Path=/"})
                if self.path == "/api/auth/me" and not owner.enter_slow_on_setup:
                    return self.respond({"id": 71100, "roles": ["CAREGIVER"]})
                session = next((part.strip().split("=", 1)[1] for part in self.headers.get("Cookie", "").split(";")
                                if part.strip().startswith("SESSION=")), None)
                count = owner.sessions.get(session, 0)
                if "/work-pack" in self.path:
                    owner.sessions[session] = count + 1
                slow = owner.enter_slow_on_setup or count > 0
                if slow and owner.mode != "normal":
                    with owner.entered:
                        owner.slow_count += 1
                        owner.entered.notify_all()
                    if owner.mode == "headers":
                        owner.release.wait(10)
                    elif owner.mode == "race":
                        owner.release.wait(10)
                    elif owner.mode == "oversize":
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.send_header("Content-Length", str(8 * 1024 * 1024 + 1))
                        self.end_headers()
                        with contextlib.suppress(OSError):
                            self.wfile.write(b"x" * (8 * 1024 * 1024 + 1))
                        return
                    elif owner.mode in ("drip", "endless", "chunked", "short-body"):
                        raw = json.dumps({"visit": {"id": 1, "elderId": 73100, "status": "SCHEDULED",
                                                  "version": 0}, "tasks": [], "execution": {}}).encode()
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        if owner.mode == "drip":
                            self.send_header("Content-Length", str(len(raw)))
                        if owner.mode == "short-body":
                            self.send_header("Content-Length", str(len(raw) + 500))
                        if owner.mode == "chunked":
                            self.send_header("Transfer-Encoding", "chunked")
                        self.end_headers()
                        with contextlib.suppress(OSError):
                            if owner.mode == "short-body":
                                self.wfile.write(raw); self.wfile.flush()
                                owner.release.wait(10)
                            else:
                                index = 0
                                while not owner.release.is_set():
                                    byte = raw[index:index + 1] if owner.mode == "drip" else b" "
                                    if owner.mode == "drip" and not byte:
                                        break
                                    self.wfile.write(b"1\r\n" + byte + b"\r\n" if owner.mode == "chunked" else byte)
                                    self.wfile.flush()
                                    index += 1
                                    owner.release.wait(.03)
                        return
                self.respond({"visit": {"id": 1, "elderId": 73100, "status": "SCHEDULED", "version": 0},
                              "tasks": [], "execution": {}})

        self.server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.server.daemon_threads = True
        self.thread = threading.Thread(target=lambda: self.server.serve_forever(poll_interval=.02), daemon=True)
        self.thread.start()
        self.env = patch.dict(os.environ, {"HPA_CG1_USERNAME": "test-user-private",
                                         "HPA_CG_PASSWORD": "test-password-private"})
        self.env.start()

    def tearDown(self):
        self.release.set()
        for process in self.processes:
            if process.poll() is None:
                process.kill()
            process.communicate(timeout=4)
        for client in self.clients:
            if client.worker:
                result = client.worker.close(force=True)
                self.assertTrue(result["reaped"])
        self.server.shutdown(); self.server.server_close(); self.thread.join(2)
        self.env.stop()
        self.temp.cleanup()

    def cfg(self, **updates):
        c = config()
        c.update(coreBase=f"http://127.0.0.1:{self.server.server_port}",
                 visitBase=f"http://127.0.0.1:{self.server.server_port}",
                 socketTimeoutSeconds=2, requestDeadlineSeconds=.5, stopDrainSeconds=.3)
        c.update(updates)
        return c

    def cli(self, c):
        cfg = self.root / "config.json"
        cfg.write_text(json.dumps(c), encoding="utf-8")
        out = self.root / "output"
        options = {}
        if os.name == "nt":
            si = subprocess.STARTUPINFO()
            si.dwFlags |= subprocess.STARTF_USESHOWWINDOW
            si.wShowWindow = 0
            options.update(creationflags=subprocess.CREATE_NEW_CONSOLE, startupinfo=si)
        process = subprocess.Popen([sys.executable, str(TOOL), "--config", str(cfg),
                                    "--output", str(out), "--execute"],
                                   stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   text=True, encoding="utf-8", **options)
        self.processes.append(process)
        return process, out

    def wait_slow(self, count=1):
        with self.entered:
            self.assertTrue(self.entered.wait_for(lambda: self.slow_count >= count, timeout=5),
                            "CLI never reached controlled in-flight request")

    def finish(self, process, out, expected=2):
        stdout, stderr = process.communicate(timeout=6)
        self.assertEqual(process.returncode, expected, stderr + stdout)
        summary = load.read_json(out / "summary.json")
        cleanup = load.read_json(out / "worker-cleanup.json")
        self.assertTrue(cleanup["complete"])
        self.assertTrue(summary["workerCleanupComplete"])
        self.assertTrue(summary["accountingValid"])
        self.assertEqual(summary["timing"]["inFlightRemaining"], 0)
        for item in cleanup["workers"]:
            self.assertFalse(alive(item["pid"]), "owned worker still alive")
        rows = [json.loads(line) for line in (out / "requests.jsonl").read_text().splitlines()]
        return summary, rows

    def slow_cli(self, mode="headers", vus=1, **updates):
        self.mode = mode
        c = self.cfg(maxVUs=vus, requestDeadlineSeconds=3, **updates)
        c["phases"] = [{"name": "pilot", "rps": 20, "durationSeconds": 3}]
        return self.cli(c)

    def test_legacy_socket_timeout_reproduction(self):
        start = time.monotonic()
        with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(
                f"http://127.0.0.1:{self.server.server_port}/legacy-drip", timeout=.1) as response:
            self.assertEqual(len(response.read()), 12)
        elapsed = time.monotonic() - start
        self.assertGreater(elapsed, .3)
        evidence(self.id(), socketTimeoutSeconds=.1, elapsedMs=round(elapsed * 1000, 3),
                 observed="legacy socket timeout does not bound request")

    def test_total_deadline_terminates_continuous_drip_three_cli_runs(self):
        for trial in range(3):
            with self.subTest(trial=trial):
                self.mode = "drip"
                c = self.cfg(socketTimeoutSeconds=.15, requestDeadlineSeconds=.5)
                process, out = self.cli(c)
                summary, rows = self.finish(process, out)
                failed = [r for r in rows if r.get("error") == "request_deadline"]
                self.assertEqual(len(failed), 1)
                self.assertEqual((failed[0]["status"], failed[0]["businessPassed"]), (200, False))
                self.assertLess(failed[0]["deadlineObservedMs"], 1100)
                self.assertIn("request_deadline", summary["stopReasons"])
                evidence(self.id(), trial=trial, observedMs=failed[0]["deadlineObservedMs"],
                         elapsedMs=failed[0]["elapsedMs"], cleanup=True)
                self.release.set()
                self.reset_run()

    def reset_run(self):
        # Keep earlier artifacts; use a fresh private run directory for each CLI.
        self.root = self.root / ("next-" + str(time.monotonic_ns()))
        self.root.mkdir()
        self.release = threading.Event()
        self.slow_count = 0

    def test_headers_endless_chunked_and_incomplete_body_are_bounded(self):
        for mode in ("headers", "endless", "chunked", "short-body"):
            with self.subTest(mode=mode):
                self.mode = mode
                process, out = self.cli(self.cfg())
                summary, rows = self.finish(process, out)
                self.assertTrue(any(r.get("error") == "request_deadline" for r in rows))
                self.assertEqual(summary["failedIterations"], 1)
                evidence(self.id(), mode=mode, cleanup=True)
                self.release.set()
                self.reset_run()

    def stop_case(self, kind, vus=1, setup=False):
        self.enter_slow_on_setup = setup
        process, out = self.slow_cli(vus=vus)
        self.wait_slow(vus if not setup else 1)
        before = time.monotonic()
        if kind == "file":
            (out / "STOP").touch()
        else:
            interrupt_cli(process)
        summary, rows = self.finish(process, out)
        elapsed = time.monotonic() - before
        self.assertLess(elapsed, 2.5, "stop/drain/reap exceeded explicit test tolerance")
        self.assertIn("operator_stop_file" if kind == "file" else "operator_interrupt", summary["stopReasons"])
        self.assertTrue(any(r.get("error") == "stop_drain_deadline" for r in rows))
        self.assertEqual(summary["launchedIterations"], 0 if setup else vus)
        expected = 1 if setup else vus * 2  # setup work-pack + admitted workload work-pack
        self.assertEqual(sum("/work-pack" in p for p, _ in self.requests), 0 if setup else expected)
        stopped = summary["timing"]["stopDetectedUtc"]
        self.assertTrue(all(r["startedUtc"] <= stopped for r in rows))
        evidence(self.id(), kind=kind, vus=vus, setup=setup, stopToExitMs=round(elapsed * 1000, 3),
                 timing=summary["timing"], cleanup=True)
        return summary

    def test_stop_inflight_one_vu_three_cli_runs(self):
        for trial in range(3):
            self.stop_case("file")
            self.requests.clear(); self.reset_run()

    def test_stop_inflight_multiple_vus_has_one_shared_drain(self):
        summary = self.stop_case("file", vus=3)
        self.assertEqual(summary["timing"]["inFlightAtStop"], 3)

    def test_native_ctrl_c_inflight_three_cli_runs(self):
        for trial in range(3):
            self.stop_case("signal")
            self.requests.clear(); self.reset_run()

    def test_stop_during_setup(self):
        self.stop_case("file", setup=True)

    def test_ctrl_c_during_setup(self):
        self.stop_case("signal", setup=True)

    def test_stop_during_cooldown_with_no_inflight(self):
        c = self.cfg()
        c["phases"] = [{"name": "pilot", "rps": 1, "durationSeconds": .2},
                       {"name": "cooldown", "rps": 0, "durationSeconds": 5}]
        process, out = self.cli(c)
        limit = time.monotonic() + 5
        while time.monotonic() < limit:
            if (out / "phases.csv").exists() and "cooldown-start" in (out / "phases.csv").read_text():
                break
            time.sleep(.01)
        else:
            self.fail("CLI did not enter cooldown")
        (out / "STOP").touch()
        summary, _ = self.finish(process, out)
        self.assertEqual(summary["timing"]["inFlightAtStop"], 0)
        self.assertLess(summary["timing"]["stopToSupervisorEndMs"], 1500)

    def test_completion_stop_race_three_cli_runs_counts_once(self):
        for trial in range(3):
            self.mode = "race"
            process, out = self.cli(self.cfg(requestDeadlineSeconds=3))
            self.wait_slow()
            (out / "STOP").touch()
            self.release.set()
            summary, rows = self.finish(process, out)
            self.assertEqual(summary["launchedIterations"], 1)
            self.assertEqual(len([r for r in rows if r["phase"] != "setup"]), 1)
            self.assertIn(summary["failedIterations"], (0, 1))
            evidence(self.id(), trial=trial, failed=summary["failedIterations"], accounting=True)
            self.reset_run()

    def test_worker_crash_fails_closed(self):
        process, out = self.slow_cli()
        self.wait_slow()
        pid = load.read_json(out / "worker-pids.json")["workers"][0]["pid"]
        self.assertTrue(alive(pid))
        os.kill(pid, signal.SIGTERM)
        summary, rows = self.finish(process, out)
        self.assertIn("http_worker_exited", summary["stopReasons"])
        self.assertEqual(summary["failedIterations"], 1)

    def test_ctrl_c_on_normal_request_then_repeat_stop_is_idempotent(self):
        process, out = self.slow_cli(mode="race")
        self.wait_slow()
        interrupt_cli(process)
        (out / "STOP").touch()
        self.release.set()
        summary, _ = self.finish(process, out)
        self.assertEqual(len(summary["stopReasons"]), len(set(summary["stopReasons"])))

    def test_dns_connect_and_assertion_block_fault_injection(self):
        for fault, injection in (
            ("dns", "import socket; socket.getaddrinfo = lambda *a, **k: time.sleep(10)"),
            ("connect", "import socket; socket.socket.connect = lambda *a, **k: time.sleep(10)"),
            ("assertion", "assert_wire_response = lambda *a, **k: time.sleep(10)"),
        ):
            with self.subTest(fault=fault):
                copied = self.root / ("fault-" + fault + ".py")
                source = TOOL.read_text(encoding="utf-8")
                source = source.replace('if __name__ == "__main__":',
                                        injection + '\n\nif __name__ == "__main__":')
                copied.write_text(source, encoding="utf-8")
                rows = []
                c = self.cfg()
                load.validate(c)
                client = load.Client(c, c["actors"][0], rows.append)
                self.clients.append(client)
                with patch.object(load, "__file__", str(copied)):
                    with self.assertRaisesRegex(load.Failure, "request_deadline"):
                        client.request("coreBase", "/api/auth/me", "setup", "/api/auth/me",
                                       check={"kind": "identity", "userId": 71100})
                self.assertFalse(rows[0]["businessPassed"])
                self.assertLess(rows[0]["deadlineObservedMs"], 1100)
                self.assertTrue(client.worker.cleanup["reaped"])
                evidence(self.id(), fault=fault, observedMs=rows[0]["deadlineObservedMs"])

    def test_tls_handshake_stall_on_local_tcp_is_bounded(self):
        release = threading.Event()
        accepted = threading.Event()

        class RawHandler(socketserver.BaseRequestHandler):
            def handle(self):
                accepted.set()
                release.wait(6)

        server = socketserver.ThreadingTCPServer(("127.0.0.1", 0), RawHandler)
        server.daemon_threads = True
        thread = threading.Thread(target=lambda: server.serve_forever(poll_interval=.02), daemon=True)
        thread.start()
        try:
            c = self.cfg(coreBase=f"https://127.0.0.1:{server.server_address[1]}",
                         visitBase=f"https://127.0.0.1:{server.server_address[1]}")
            load.validate(c)
            rows = []
            client = load.Client(c, c["actors"][0], rows.append)
            self.clients.append(client)
            with self.assertRaisesRegex(load.Failure, "request_deadline"):
                client.request("coreBase", "/api/auth/csrf", "setup", "/api/auth/csrf", csrf=True)
            self.assertTrue(accepted.is_set())
            self.assertLess(rows[0]["deadlineObservedMs"], 1100)
            evidence(self.id(), observedMs=rows[0]["deadlineObservedMs"], tlsHandshakeBlocked=True)
        finally:
            release.set()
            server.shutdown(); server.server_close(); thread.join(2)

    def test_rejected_config_has_no_worker_or_dns(self):
        c = self.cfg(coreBase="https://no-network.example.invalid", visitBase="https://no-network.example.invalid")
        with patch.object(socket, "getaddrinfo", side_effect=AssertionError("DNS")), \
                patch.object(load.subprocess, "Popen", side_effect=AssertionError("worker")):
            load.validate(c)
            with self.assertRaises(ValueError):
                load.run(c, self.root / "rejected")
        self.assertFalse((self.root / "rejected").exists())

    def test_timing_legacy_mapping_and_reject_ambiguous_or_invalid_fields(self):
        c = self.cfg()
        for key in ("socketTimeoutSeconds", "requestDeadlineSeconds", "stopDrainSeconds"):
            del c[key]
        c["timeoutSeconds"] = .2
        plan = load.validate(c)
        self.assertEqual(plan["socketTimeoutSeconds"], .2)
        self.assertNotIn("maximumRequestSeconds", plan)
        self.assertTrue(plan["warnings"])
        self.assertGreater(plan["requestDeadlineSeconds"], .2)
        for field in ("socketTimeoutSeconds", "requestDeadlineSeconds", "stopDrainSeconds"):
            for value in (float("nan"), float("inf"), True, 0, -1):
                bad = self.cfg(**{field: value})
                with self.assertRaises(ValueError):
                    load.validate(bad)
        c = self.cfg(timeoutSeconds=.1)
        with self.assertRaisesRegex(ValueError, "conflicting"):
            load.validate(c)

    def test_output_redaction_and_header_success_is_not_business_success(self):
        self.mode = "drip"
        process, out = self.cli(self.cfg())
        summary, rows = self.finish(process, out)
        failed = [r for r in rows if r["phase"] != "setup"][0]
        self.assertTrue(failed["httpPassed"])
        self.assertFalse(failed["businessPassed"])
        endpoint = summary["phases"][0]["endpoints"][0]
        self.assertEqual((endpoint["httpFailures"], endpoint["businessFailures"]), (0, 1))
        for file in out.iterdir():
            if file.is_file():
                content = file.read_text()
                for secret in ("test-user-private", "test-password-private", "test-session-", "test-csrf"):
                    self.assertNotIn(secret, content)


    def test_healthy_multi_vu_reuses_sessions_without_extra_login(self):
        c = self.cfg(maxVUs=2, requestDeadlineSeconds=2)
        c["phases"] = [{"name": "pilot", "rps": 5, "durationSeconds": 1}]
        process, out = self.cli(c)
        summary, rows = self.finish(process, out, expected=0)
        self.assertEqual((summary["launchedIterations"], summary["failedIterations"],
                          summary["droppedIterations"]), (5, 0, 0))
        self.assertEqual((summary["httpRequestsDuringSetup"], summary["httpRequestsDuringLoad"]), (10, 5))
        self.assertEqual(len(self.sessions), 2)
        evidence(self.id(), setupHttp=10, businessGets=5, isolatedSessions=2, cleanup=True)

    def startup_fault_client(self):
        copied = self.root / "startup-stall.py"
        injection = "\n".join(['if sys.argv[1:] == ["--http-worker"]:', '    time.sleep(8)',
                                '', 'if __name__ == "__main__":'])
        source = TOOL.read_text(encoding="utf-8").replace('if __name__ == "__main__":', injection)
        copied.write_text(source, encoding="utf-8")
        c = self.cfg()
        load.validate(c)
        rows = []
        client = load.Client(c, c["actors"][0], rows.append)
        self.clients.append(client)
        return client, copied, rows

    def test_worker_bootstrap_has_separate_bounded_budget(self):
        client, copied, rows = self.startup_fault_client()
        with patch.object(load, "__file__", str(copied)), patch.object(load, "WORKER_START_SECONDS", .3):
            with self.assertRaisesRegex(load.Failure, "worker_start_deadline"):
                client.request("coreBase", "/api/auth/me", "setup", "/api/auth/me")
        self.assertEqual(rows, [])  # No HTTP was admitted.
        self.assertTrue(client.worker.cleanup["reaped"])
        self.assertEqual(self.requests, [])

    def test_stop_during_worker_bootstrap_does_not_wait_start_deadline(self):
        client, copied, rows = self.startup_fault_client()
        out = self.root / "stop-startup"
        out.mkdir()
        client.control = load.StopControl(out, .3)
        client.worker_registered = lambda worker: (out / "STOP").touch()
        begin = time.monotonic()
        with patch.object(load, "__file__", str(copied)):
            with self.assertRaisesRegex(load.Failure, "stopped_during_worker_start"):
                client.request("coreBase", "/api/auth/me", "setup", "/api/auth/me")
        self.assertLess(time.monotonic() - begin, 2.5)
        self.assertEqual(rows, [])
        self.assertTrue(client.worker.cleanup["reaped"])

    def test_response_size_limit_survives_transport_change(self):
        self.mode = "oversize"
        process, out = self.cli(self.cfg(requestDeadlineSeconds=5))
        summary, rows = self.finish(process, out)
        self.assertIn("response_too_large", summary["stopReasons"])
        self.assertEqual(summary["failedIterations"], 1)

    def test_parent_crash_closes_owned_worker_without_success_summary(self):
        process, out = self.slow_cli()
        self.wait_slow()
        pid = load.read_json(out / "worker-pids.json")["workers"][0]["pid"]
        process.kill(); process.communicate(timeout=4)
        limit = time.monotonic() + 3
        while alive(pid) and time.monotonic() < limit:
            time.sleep(.02)
        self.assertFalse(alive(pid))
        self.assertFalse((out / "summary.json").exists())
        evidence(self.id(), summaryMissing=True, interpretation="INCOMPLETE", ownedWorkerExited=True)


if __name__ == "__main__":
    unittest.main()
