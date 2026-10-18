"""Bounded CG03 arrival-rate workload. Python >=3.10, standard library only.

No deployment, database access, logout, automatic retry, or redirect following.
Validation is offline; network requests require the explicit --execute option.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import csv
import datetime as dt
import hashlib
import http.cookiejar
import json
import math
import os
from pathlib import Path
import queue
import re
import signal
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def utc():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def write_json(path, value):
    Path(path).write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def require(condition, code):
    if not condition:
        raise ValueError(code)


def number(value, low, high):
    return type(value) in (int, float) and math.isfinite(value) and low <= value <= high


def origin(value):
    p = urllib.parse.urlsplit(value)
    require(p.scheme in ("http", "https") and p.hostname and not p.username and not p.password,
            "invalid_origin")
    require(p.path in ("", "/") and not p.query and not p.fragment, "origin_must_not_have_path")
    # Accessing port also validates it; localhost aliases other than these require approval.
    port = p.port or (443 if p.scheme == "https" else 80)
    host = p.hostname.lower()
    authority = f"[{host}]" if ":" in host else host
    if port != (443 if p.scheme == "https" else 80):
        authority += f":{port}"
    return f"{p.scheme}://{authority}", host in ("localhost", "127.0.0.1", "::1")


def validate(c, execute=False):
    require(c.get("schemaVersion") == 1, "unsupported_schema")
    require(re.fullmatch(r"[a-zA-Z0-9_-]{1,64}", c.get("runId", "")), "invalid_run_id")
    require(c.get("scenario") in ("work-pack", "check-in"), "invalid_scenario")
    core, core_local = origin(c["coreBase"])
    visit, visit_local = origin(c["visitBase"])
    a, b = urllib.parse.urlsplit(core), urllib.parse.urlsplit(visit)
    require(a.hostname == b.hostname and a.scheme == b.scheme, "shared_session_requires_same_host_and_scheme")
    if not (core_local and visit_local):
        require(core == visit and a.scheme == "https", "remote_requires_single_https_origin")
    require(type(c["maxVUs"]) is int and 1 <= c["maxVUs"] <= 200, "invalid_max_vus")
    require(number(c["timeoutSeconds"], 0.1, 30), "invalid_timeout")
    require(number(c["maxLaunchLagMs"], 1, 5000), "invalid_launch_lag")
    require(type(c["maxIterations"]) is int and 1 <= c["maxIterations"] <= 1000000, "invalid_iteration_cap")
    require(isinstance(c["phases"], list) and 1 <= len(c["phases"]) <= 20, "invalid_phases")
    total = 0
    names = set()
    for p in c["phases"]:
        require(re.fullmatch(r"[a-zA-Z0-9_-]{1,40}", p["name"]) and p["name"] not in names, "invalid_phase_name")
        names.add(p["name"])
        require(number(p["rps"], 0, 1000) and number(p["durationSeconds"], 0.1, 1800), "invalid_phase_bounds")
        p["iterations"] = math.ceil(p["rps"] * p["durationSeconds"])
        total += p["iterations"]
    require(sum(p["durationSeconds"] for p in c["phases"]) <= 3600, "duration_exceeds_one_hour")
    require(0 < total <= c["maxIterations"], "iteration_cap_exceeded")
    actors = c["actors"]
    require(isinstance(actors, list) and len(actors) > 0, "missing_actors")
    visits = set()
    aliases = set()
    for actor in actors:
        require(re.fullmatch(r"[a-zA-Z0-9_-]{1,40}", actor["alias"]) and actor["alias"] not in aliases, "invalid_alias")
        aliases.add(actor["alias"])
        require(type(actor["userId"]) is int and actor["userId"] > 0, "invalid_user_id")
        for key in ("usernameEnv", "passwordEnv"):
            require(re.fullmatch(r"[A-Z][A-Z0-9_]{1,100}", actor[key]), "invalid_credential_env_name")
        require(len(actor["visits"]) > 0, "missing_visit_pool")
        for v in actor["visits"]:
            for key in ("visitId", "elderId"):
                require(type(v[key]) is int and v[key] > 0, "invalid_fixture_id")
            require(v["visitId"] not in visits, "duplicate_visit_in_pool")
            visits.add(v["visitId"])
            require(type(v["expectedVersion"]) is int and v["expectedVersion"] >= 0, "invalid_expected_version")
            require(v["expectedStatus"] in ("SCHEDULED", "IN_PROGRESS", "COMPLETED"), "invalid_expected_state")
            if c["scenario"] == "check-in":
                require(v["expectedStatus"] == "SCHEDULED", "write_pool_must_be_scheduled")
    require(len(actors) <= c["maxVUs"], "max_vus_must_cover_all_actors")
    if c["scenario"] == "check-in":
        require(len(visits) >= total, "insufficient_fresh_visits_for_planned_arrivals")
    stop = c["stop"]
    require(number(stop["errorWindowSeconds"], 1, 300), "invalid_error_window")
    require(number(stop["latencyWindowSeconds"], 1, 300), "invalid_latency_window")
    require(number(stop["errorRate"], 0, 1) and number(stop["p95Ms"], 1, 120000), "invalid_stop_threshold")
    for key in ("consecutiveWindows", "minSamples"):
        require(type(stop[key]) is int and 1 <= stop[key] <= 10000, "invalid_stop_sample_setting")
    targets = c["targets"]
    require(number(targets["httpP95Ms"], 1, 120000) and number(targets["iterationP95Ms"], 1, 240000), "invalid_target_latency")
    require(number(targets.get("checkinHttpP95Ms", targets["httpP95Ms"]), 1, 120000), "invalid_write_target_latency")
    require(number(targets["errorRate"], 0, 1), "invalid_target_error_rate")
    require(type(targets["minSamplesPerEndpoint"]) is int and targets["minSamplesPerEndpoint"] >= 1, "invalid_target_sample_count")
    approval = c.get("approval", {})
    approved = (approval.get("confirmed") is True and approval.get("environment")
                and approval.get("windowUtc") and approval.get("owner")
                and approval.get("deploymentRef") not in (None, "", "UNCONFIRMED")
                and set(approval.get("allowedOrigins", [])) == {core, visit})
    if execute and not (core_local and visit_local):
        require(bool(approved), "remote_target_not_confirmed")
    c["coreBase"], c["visitBase"] = core, visit
    return {"plannedIterations": total, "freshVisits": len(visits), "localhost": core_local and visit_local,
            "remoteExecutionConfirmed": bool(approved), "maximumRequestSeconds": c["timeoutSeconds"]}


class Failure(Exception):
    def __init__(self, code, fatal=True):
        super().__init__(code)
        self.code, self.fatal = code, fatal


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


class Client:
    def __init__(self, config, actor, record):
        self.c, self.actor, self.record = config, actor, record
        self.jar = http.cookiejar.CookieJar()
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(),
                                                urllib.request.HTTPCookieProcessor(self.jar))

    def request(self, base, path, phase, endpoint, body=None, check=None, csrf=False):
        headers = {"Accept": "application/json"}
        if body is not None:
            token = next((x.value for x in self.jar if x.name == "XSRF-TOKEN"), None)
            if not token:
                raise Failure("missing_csrf")
            headers.update({"Content-Type": "application/json", "X-XSRF-TOKEN": urllib.parse.unquote(token)})
        request = urllib.request.Request(self.c[base] + path, headers=headers,
                                         data=None if body is None else json.dumps(body).encode())
        row = {"kind": "http", "phase": phase, "endpoint": endpoint, "method": request.get_method(),
               "startedUtc": utc(), "status": 0, "httpPassed": False, "businessPassed": False}
        begin = time.perf_counter()
        try:
            try:
                response = self.http.open(request, timeout=self.c["timeoutSeconds"])
            except urllib.error.HTTPError as e:
                response = e
            with response:
                row["status"] = response.status
                if response.status != 200:
                    fatal = response.status in (401, 403) or 300 <= response.status < 400 or response.status < 500
                    raise Failure("authentication_or_contract_http" if fatal else "server_http", fatal)
                row["httpPassed"] = True
                raw = response.read(8 * 1024 * 1024 + 1)
                if len(raw) > 8 * 1024 * 1024:
                    raise Failure("response_too_large")
                if csrf:
                    value = None
                else:
                    content_type = response.headers.get_content_type()
                    if content_type != "application/json" and not content_type.endswith("+json"):
                        raise Failure("non_json_response")
                    try:
                        value = json.loads(raw)
                    except (ValueError, UnicodeError):
                        raise Failure("invalid_json")
                if check:
                    check(value)
                row["businessPassed"] = True
                return value
        except Failure as e:
            row["error"] = e.code
            raise
        except (OSError, urllib.error.URLError, TimeoutError):
            row["error"] = "transport_error"
            raise Failure("transport_error", False)
        finally:
            row["elapsedMs"] = round((time.perf_counter() - begin) * 1000, 3)
            self.record(row)

    def login(self):
        username = os.environ.get(self.actor["usernameEnv"])
        password = os.environ.get(self.actor["passwordEnv"])
        if not username or not password:
            raise Failure("missing_private_credential_env")
        self.request("coreBase", "/api/auth/csrf", "setup", "/api/auth/csrf", csrf=True)
        self.request("coreBase", "/api/auth/login", "setup", "/api/auth/login",
                     {"username": username, "password": password}, self.identity)
        self.request("coreBase", "/api/auth/csrf", "setup", "/api/auth/csrf", csrf=True)
        self.request("coreBase", "/api/auth/me", "setup", "/api/auth/me", check=self.identity)

    def identity(self, value):
        if not isinstance(value, dict) or value.get("id") != self.actor["userId"] or "CAREGIVER" not in value.get("roles", []):
            raise Failure("wrong_identity")

    @staticmethod
    def pack(value, fixture):
        if not isinstance(value, dict):
            raise Failure("wrong_work_pack")
        v = value.get("visit", {})
        if (v.get("id"), v.get("elderId"), v.get("status"), v.get("version")) != (
                fixture["visitId"], fixture["elderId"], fixture["expectedStatus"], fixture["expectedVersion"]):
            raise Failure("wrong_visit_or_state")
        if not isinstance(value.get("tasks"), list) or not isinstance(value.get("execution"), dict):
            raise Failure("incomplete_work_pack")

    def work_pack(self, fixture, phase, writing=False):
        def check(value):
            self.pack(value, fixture)
            # Execution tasks are materialized by check-in. Before it, an empty tasks
            # list is legitimate; the server's allowedActions validates plan/window.
            if writing and "CHECK_IN" not in value["execution"].get("allowedActions", []):
                raise Failure("visit_not_checkin_eligible")
        return self.request("visitBase", f"/api/visits/{fixture['visitId']}/work-pack", phase,
                            "/api/visits/{id}/work-pack", check=check)

    def iteration(self, fixture, phase, writing, stop):
        self.work_pack(fixture, phase, writing)
        if not writing:
            return
        if stop.is_set():
            raise Failure("stopped_before_write", False)
        def check(value):
            if not isinstance(value, dict) or value.get("visitId") != fixture["visitId"] or value.get("replayed") is not False or value.get("savedState") != "IN_PROGRESS" or value.get("visitVersion") != fixture["expectedVersion"] + 1:
                raise Failure("checkin_not_a_fresh_success")
        self.request("visitBase", f"/api/visits/{fixture['visitId']}/check-in", phase,
                     "/api/visits/{id}/check-in",
                     {"expectedVersion": fixture["expectedVersion"], "clientRequestId": str(uuid.uuid4()),
                      "locationSource": "MANUAL_LOCATION_NOTE", "locationNote": "HPA-SYNTHETIC-" + self.c["runId"]}, check)
        # A POST timeout is ambiguous: never retry it. On success, assert the saved state separately.
        self.work_pack({**fixture, "expectedStatus": "IN_PROGRESS", "expectedVersion": fixture["expectedVersion"] + 1}, phase)


def percentile(values, q=.95):
    return sorted(values)[max(0, math.ceil(q * len(values)) - 1)] if values else None


class WindowStop:
    """Non-overlapping wall-clock windows; no traffic is not counted as success."""
    def __init__(self, config, start):
        self.c = config
        self.start = start
        self.next = {"errors": start + config["errorWindowSeconds"], "latency": start + config["latencyWindowSeconds"]}
        self.streak = {"errors": 0, "latency": 0}

    def evaluate(self, now, rows):
        for metric, window in (("errors", self.c["errorWindowSeconds"]), ("latency", self.c["latencyWindowSeconds"])):
            while now >= self.next[metric]:
                end = self.next[metric]
                samples = [r for r in rows if end - window <= r["finishedMono"] < end]
                enough = len(samples) >= self.c["minSamples"]
                bad = enough and (sum(not r["passed"] for r in samples) / len(samples) > self.c["errorRate"] if metric == "errors"
                                  else percentile([r["elapsedMs"] for r in samples]) > self.c["p95Ms"])
                self.streak[metric] = self.streak[metric] + 1 if bad else 0
                self.next[metric] += window
                if self.streak[metric] >= self.c["consecutiveWindows"]:
                    return "consecutive_" + metric + "_windows"
        return None


def summarize(http, iterations, phases, targets):
    result = []
    for phase in phases:
        h = [r for r in http if r["phase"] == phase["name"]]
        it = [r for r in iterations if r["phase"] == phase["name"]]
        endpoints = []
        for method, path in sorted({(r["method"], r["endpoint"]) for r in h}):
            rows = [r for r in h if (r["method"], r["endpoint"]) == (method, path)]
            passed = [r["elapsedMs"] for r in rows if r["businessPassed"]]
            error_rate = sum(not r["businessPassed"] for r in rows) / len(rows)
            enough = len(rows) >= targets["minSamplesPerEndpoint"]
            target_ms = targets.get("checkinHttpP95Ms", targets["httpP95Ms"]) if path.endswith("/check-in") else targets["httpP95Ms"]
            endpoints.append({"method": method, "endpoint": path, "samples": len(rows),
                              "httpFailures": sum(not r["httpPassed"] for r in rows),
                              "businessFailures": sum(not r["businessPassed"] for r in rows), "errorRate": error_rate,
                              "allP95Ms": percentile([r["elapsedMs"] for r in rows]), "successP95Ms": percentile(passed),
                              "targetObservation": "INSUFFICIENT_SAMPLES" if not enough else (
                                  "WITHIN_DRAFT_TARGET" if error_rate <= targets["errorRate"] and passed and percentile(passed) <= target_ms else "OUTSIDE_DRAFT_TARGET")})
        enough_iterations = len(it) >= targets["minSamplesPerEndpoint"]
        within_iteration_target = bool(it) and sum(not r["passed"] for r in it) / len(it) <= targets["errorRate"] and percentile([r["elapsedMs"] for r in it]) <= targets["iterationP95Ms"]
        result.append({"phase": phase["name"], "targetRps": phase["rps"], "durationSeconds": phase["durationSeconds"],
                       "plannedIterations": phase["iterations"], "launchedIterations": len(it),
                       "droppedIterations": phase.get("dropped", 0), "notScheduledAfterStop": phase.get("notScheduledAfterStop", 0),
                       "actualLaunchRps": len(it) / phase.get("emissionSeconds", phase["durationSeconds"]) if phase.get("emissionSeconds", phase["durationSeconds"]) > 0 else None,
                       "emissionSeconds": phase.get("emissionSeconds"), "failedIterations": sum(not r["passed"] for r in it),
                       "iterationErrorRate": sum(not r["passed"] for r in it) / len(it) if it else None,
                       "iterationP95Ms": percentile([r["elapsedMs"] for r in it]),
                       "iterationTargetObservation": "INSUFFICIENT_SAMPLES" if not enough_iterations else "WITHIN_DRAFT_TARGET" if within_iteration_target else "OUTSIDE_DRAFT_TARGET",
                       "launchLagP95Ms": percentile([r["launchLagMs"] for r in it]), "endpoints": endpoints})
    return result


def run(c, output):
    c = json.loads(json.dumps(c))
    plan = validate(c, execute=True)
    for phase in c["phases"]:
        phase.update(dropped=0, notScheduledAfterStop=phase["iterations"], emissionSeconds=0)
    out = Path(output)
    out.mkdir(parents=True, exist_ok=False)  # Preserve earlier evidence; never append across runs.
    stop, lock = threading.Event(), threading.Lock()
    http, iterations, reasons = [], [], []
    started = time.monotonic()
    window = WindowStop(c["stop"], started)
    samples = (out / "requests.jsonl").open("w", encoding="utf-8")
    it_file = (out / "iterations.jsonl").open("w", encoding="utf-8")
    markers = (out / "phases.csv").open("w", newline="", encoding="utf-8")
    writer = csv.writer(markers)
    writer.writerow(["timestamp", "phase"])
    def mark(name):
        writer.writerow([utc(), name]); markers.flush()
    def halt(code):
        with lock:
            if code not in reasons:
                reasons.append(code)
        stop.set()
    previous_handler = None
    if threading.current_thread() is threading.main_thread():
        previous_handler = signal.getsignal(signal.SIGINT)
        def interrupt_handler(signum, frame):
            # Avoid lock acquisition in a signal handler; an HTTP callback might hold it.
            reasons.append("operator_interrupt")
            stop.set()
        signal.signal(signal.SIGINT, interrupt_handler)
    def record(row):
        with lock:
            http.append(row)
            samples.write(json.dumps(row) + "\n"); samples.flush()
    def check_stop():
        if (out / "STOP").exists():
            halt("operator_stop_file")
        with lock:
            reason = window.evaluate(time.monotonic(), iterations)
        if reason:
            halt(reason)
    def wait_until(deadline):
        while not stop.is_set():
            check_stop()
            left = deadline - time.monotonic()
            if left <= 0:
                return
            stop.wait(min(left, .1))
    write_json(out / "manifest.json", {"schemaVersion": 1, "runId": c["runId"], "startedUtc": utc(),
               "scenario": c["scenario"], "codeBaseline": c.get("codeBaseline", "UNCONFIRMED"),
               "targetOrigins": [c["coreBase"], c["visitBase"]],
               "approval": {k: c.get("approval", {}).get(k) for k in ("confirmed", "environment", "windowUtc", "owner", "deploymentRef", "allowedOrigins")},
               "plan": plan, "maxVUs": c["maxVUs"], "phases": c["phases"], "targets": c["targets"], "stop": c["stop"],
               "configSha256": hashlib.sha256(json.dumps(c, sort_keys=True).encode()).hexdigest(),
               "toolSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               "formalAcceptance": False, "sensitiveBodiesAndCookiesRecorded": False})
    writing = c["scenario"] == "check-in"
    lanes = {}
    futures = []
    counters = {i: 0 for i in range(len(c["actors"]))}
    # Round-robin fixture allocation prevents one actor monopolising the write phase.
    write_pool = []
    if writing:
        for index in range(max(len(a["visits"]) for a in c["actors"])):
            write_pool.extend((i, a["visits"][index]) for i, a in enumerate(c["actors"]) if index < len(a["visits"]))
    pool_index = 0
    def task(client, fixture, phase, lag, lane_queue):
        row = {"kind": "iteration", "phase": phase, "startedUtc": utc(), "launchLagMs": round(lag, 3), "passed": False}
        begin = time.monotonic()
        try:
            if stop.is_set():
                raise Failure("stopped_before_iteration", False)
            client.iteration(fixture, phase, writing, stop)
            row["passed"] = True
        except Failure as e:
            row["error"] = e.code
            if e.fatal:
                halt(e.code)
        except Exception:
            row["error"] = "unexpected_tool_error"
            halt("unexpected_tool_error")
        finally:
            row["elapsedMs"] = round((time.monotonic() - begin) * 1000, 3)
            row["finishedMono"] = time.monotonic()
            with lock:
                iterations.append(row)
                it_file.write(json.dumps({k: v for k, v in row.items() if k != "finishedMono"}) + "\n"); it_file.flush()
            lane_queue.put(client)
    mark("setup-start")
    try:
        for i, actor in enumerate(c["actors"]):
            lanes[i] = queue.Queue()
        for n in range(c["maxVUs"]):
            i = n % len(c["actors"])
            check_stop()
            if stop.is_set():
                break
            client = Client(c, c["actors"][i], record)
            client.login()
            # Genuine visit authorization across the same Redis session, not a fabricated Cookie.
            client.work_pack(c["actors"][i]["visits"][0], "setup", writing)
            lanes[i].put(client)
        mark("setup-end")
        # Windows begin with the workload, excluding login/setup latency and failures.
        window = WindowStop(c["stop"], time.monotonic())
        with ThreadPoolExecutor(max_workers=c["maxVUs"]) as executor:
            epoch = time.monotonic()
            for phase in c["phases"]:
                emission_start = time.monotonic()
                phase["dropped"], phase["notScheduledAfterStop"] = 0, 0
                mark(phase["name"] + "-start")
                if stop.is_set():
                    phase["notScheduledAfterStop"] = phase["iterations"]
                    phase["emissionSeconds"] = 0
                    mark(phase["name"] + "-skipped")
                    continue
                for n in range(phase["iterations"]):
                    deadline = epoch + n / phase["rps"]
                    wait_until(deadline)
                    if stop.is_set():
                        phase["notScheduledAfterStop"] = phase["iterations"] - n
                        break
                    lag = (time.monotonic() - deadline) * 1000
                    if writing:
                        i, fixture = write_pool[pool_index]
                        pool_index += 1  # Never recycle even a dropped write slot.
                    else:
                        i = n % len(c["actors"])
                        actor = c["actors"][i]
                        fixture = actor["visits"][counters[i] % len(actor["visits"])]
                        counters[i] += 1
                    if lag > c["maxLaunchLagMs"]:
                        phase["dropped"] += 1
                        continue
                    try:
                        client = lanes[i].get_nowait()
                    except queue.Empty:
                        phase["dropped"] += 1
                        continue
                    futures.append(executor.submit(task, client, fixture, phase["name"], lag, lanes[i]))
                epoch += phase["durationSeconds"]
                wait_until(epoch)
                phase["emissionSeconds"] = round(time.monotonic() - emission_start, 6)
                mark(phase["name"] + "-end")
            mark("load-stopped-draining")
            for future in futures:
                future.result()
            check_stop()
    except KeyboardInterrupt:
        halt("operator_interrupt")
    except Failure as e:
        halt("setup_" + e.code)
    except Exception:
        halt("unexpected_tool_error")
    finally:
        mark("drain-complete")
        phase_metrics = summarize(http, iterations, c["phases"], c["targets"])
        failed = sum(not r["passed"] for r in iterations)
        dropped = sum(p.get("dropped", 0) for p in c["phases"])
        outcome = "STOPPED" if stop.is_set() else "COMPLETED_WITH_FAILURES" if failed else "COMPLETED_WITH_DROPS" if dropped else "COMPLETED"
        summary = {"runId": c["runId"], "finishedUtc": utc(), "outcome": outcome, "stopReasons": reasons,
                   "scenario": c["scenario"], "plannedIterations": plan["plannedIterations"],
                   "launchedIterations": len(iterations), "failedIterations": failed, "droppedIterations": dropped,
                   "notScheduledAfterStop": sum(p.get("notScheduledAfterStop", p["iterations"]) for p in c["phases"]),
                   "httpRequestsDuringLoad": sum(r["phase"] != "setup" for r in http),
                   "httpRequestsDuringSetup": sum(r["phase"] == "setup" for r in http),
                   "targetsAreDraft": not bool(c.get("approval", {}).get("confirmed")),
                   "formalAcceptance": False, "phases": phase_metrics,
                   "limitations": ["Client-side elapsed time; no HPA/resource observations collected by this tool.",
                                   "No retry/logout/reset; timed-out writes require reconciliation before cleanup.",
                                   "Read work-pack endpoint also writes access audit records.",
                                   "Fixed-account backend test does not cover account switching, UI freshness or FM03 caching.",
                                   "Threads/urllib: verify generator headroom and dropped iterations before increasing rate."]}
        write_json(out / "summary.json", summary)
        with (out / "summary.csv").open("w", newline="", encoding="utf-8") as f:
            fields = ["phase", "method", "endpoint", "samples", "httpFailures", "businessFailures", "errorRate", "allP95Ms", "successP95Ms", "targetObservation"]
            w = csv.DictWriter(f, fieldnames=fields); w.writeheader()
            for p in phase_metrics:
                for endpoint in p["endpoints"]:
                    w.writerow({"phase": p["phase"], **endpoint})
        samples.close(); it_file.close(); markers.close()
        if previous_handler is not None:
            signal.signal(signal.SIGINT, previous_handler)
    print(json.dumps({k: summary[k] for k in ("runId", "outcome", "launchedIterations", "failedIterations", "droppedIterations", "stopReasons")}))
    return 0 if outcome == "COMPLETED" else 2


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True)
    parser.add_argument("--output", help="New private run directory; must not already exist")
    parser.add_argument("--execute", action="store_true", help="Send requests; without this flag only offline validation occurs")
    args = parser.parse_args()
    try:
        config = read_json(args.config)
        plan = validate(config, execute=args.execute)
        if not args.execute:
            print(json.dumps({"status": "OFFLINE_VALID", **plan}))
            return 0
        require(bool(args.output), "output_directory_required")
        return run(config, args.output)
    except (ValueError, KeyError, TypeError, OSError):
        # Do not echo config values, response bodies, URLs with credentials or passwords.
        print("Configuration rejected; check schema, bounds, fresh visits, approval, and a new output directory.")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
