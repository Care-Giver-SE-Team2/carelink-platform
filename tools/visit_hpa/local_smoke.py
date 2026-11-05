"""Disposable, loopback-only production core/visit/Redis/MySQL workload validation.

Requires already-built JARs, Java 25 and cached mysql:8.4 / redis:7.4-alpine.
Creates only labelled disposable containers; no downloads or shared DB cleanup.
"""
import argparse
import datetime as dt
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time
import urllib.parse
import urllib.request
import uuid

import load


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--core-jar", required=True)
    parser.add_argument("--visit-jar", required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--docker-context", default="desktop-linux")
    parser.add_argument("--output", required=True, help="New private directory; never commit its contents")
    args = parser.parse_args()
    root = Path(args.output).resolve()
    load.require(not root.exists(), "new_output_directory_required")
    jars = {s: Path(getattr(args, s + "_jar")).resolve(strict=True) for s in ("core", "visit")}
    for port in (13306, 16379, 18080, 18081):
        with socket.socket() as sock:
            load.require(sock.connect_ex(("127.0.0.1", port)) != 0, "local_port_already_used")
    root.mkdir(parents=True)
    private = root / "runtime-private"; private.mkdir(mode=0o700)
    runid = "hpa-" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S") + "-" + secrets.token_hex(3)
    dbpass, password = secrets.token_hex(24), "LocalHpa-" + secrets.token_hex(12)
    dc = ["docker", "--context", args.docker_context]
    def docker(*command, sql=None, check=True):
        p = subprocess.run(dc + list(command), input=sql, capture_output=True, text=True, encoding="utf-8", errors="replace")
        if check and p.returncode:
            raise RuntimeError("local_docker_operation_failed:" + command[0])
        return p.stdout.strip()
    before = docker("ps", "-a", "--format", "{{.ID}} {{.Names}} {{.State}}").splitlines()
    containers, processes, logs = {}, {}, []
    checks, runs, network = [], [], None
    fixture = []
    result = {"runId": runid, "status": "RUNNING", "scope": "localhost-only; events disabled; no HPA", "checks": checks,
              "applicationJars": {s: hashlib.sha256(j.read_bytes()).hexdigest() for s, j in jars.items()}}
    load.write_json(root / "local-validation.json", result)
    def query(sql, db="carelink_visit"):
        load.require(db in ("carelink", "carelink_visit"), "invalid_db")
        return docker("exec", "-i", containers["mysql"], "sh", "-c",
                      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot --batch --raw -N ' + db, sql=sql)
    def start_service(service):
        port = 18080 if service == "core" else 18081
        database = "carelink" if service == "core" else "carelink_visit"
        user = "carelink" if service == "core" else "visit"
        config = f"""server.address=127.0.0.1
server.port={port}
spring.datasource.url=jdbc:mysql://127.0.0.1:13306/{database}?connectionTimeZone=Asia/Singapore&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.username={user}
spring.datasource.password={dbpass}
spring.data.redis.host=127.0.0.1
spring.data.redis.port=16379
spring.session.data.redis.namespace=carelink-hpa:{runid}:session
carelink.core-api.base-url=http://127.0.0.1:18080
carelink.visit-api.base-url=http://127.0.0.1:18081
carelink.events.topic-arn=false
carelink.events.queue-url=false
carelink.caregiver.credential-storage={private.as_posix()}/credentials
carelink.caregiver.evidence-storage={private.as_posix()}/evidence
spring.servlet.multipart.location={private.as_posix()}/multipart/{service}
carelink.caregiver.expiry-scan-cron=-
carelink.roster.schedule-cron=-
carelink.missed-check-in.enabled=false
carelink.missed-check-in.scan-initial-delay=PT24H
carelink.escalation.scan-initial-delay=PT24H
carelink.roster.uncovered-scan-initial-delay=PT24H
carelink.roster.leave-reminder-initial-delay=PT24H
carelink.rerostering.scan-initial-delay=PT24H
carelink.spot-checks.reminder-scan-initial-delay=PT24H
"""
        properties = private / (service + ".properties")
        properties.write_text(config, encoding="utf-8"); properties.chmod(0o600)
        env = {k: v for k, v in os.environ.items() if not k.startswith(("AWS_", "SPRING_", "CARELINK_", "DB_")) and k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")}
        env.update(AWS_EC2_METADATA_DISABLED="true", SPRING_PROFILES_ACTIVE="default", APP_COMMIT="09d6a3f-local-hpa-smoke")
        stdout, stderr = (root / (service + ".log")).open("w", encoding="utf-8"), (root / (service + "-error.log")).open("w", encoding="utf-8")
        logs.extend([stdout, stderr])
        processes[service] = subprocess.Popen([args.java, "-Xmx384m", "-Duser.timezone=Asia/Singapore", "-jar", str(jars[service]),
                                               "--spring.config.additional-location=" + properties.as_uri()],
                                              cwd=root, env=env, stdout=stdout, stderr=stderr)
    def ready(port):
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), load.NoRedirect())
        for _ in range(180):
            if any(p.poll() is not None for p in processes.values()):
                raise RuntimeError("local_service_exited")
            try:
                with opener.open(f"http://127.0.0.1:{port}/actuator/health", timeout=2) as r:
                    if json.load(r)["status"] == "UP": return
            except Exception: pass
            time.sleep(1)
        raise RuntimeError("local_service_not_ready")
    def run_config(c, name):
        load.write_json(private / (name + ".json"), c)
        code = load.run(c, root / name)
        summary = load.read_json(root / name / "summary.json")
        runs.append({"name": name, "outcome": summary["outcome"], "iterations": summary["launchedIterations"],
                     "httpRequests": summary["httpRequestsDuringLoad"], "failed": summary["failedIterations"], "dropped": summary["droppedIterations"]})
        load.require(code == 0, "local_load_failed")
    try:
        for image in ("mysql:8.4", "redis:7.4-alpine"):
            docker("image", "inspect", image)
        network = docker("network", "create", "--label", "carelink.visit-hpa=" + runid, runid)
        envfile = private / "mysql.env"; envfile.write_text("MYSQL_ROOT_PASSWORD=" + dbpass + "\n", encoding="ascii"); envfile.chmod(0o600)
        sqlfile = private / "init.sql"
        sqlfile.write_text(f"CREATE DATABASE carelink CHARACTER SET utf8mb4;\nCREATE DATABASE carelink_visit CHARACTER SET utf8mb4;\nCREATE USER 'carelink'@'%' IDENTIFIED BY '{dbpass}';\nCREATE USER 'visit'@'%' IDENTIFIED BY '{dbpass}';\nGRANT ALL ON carelink.* TO 'carelink'@'%';\nGRANT ALL ON carelink_visit.* TO 'visit'@'%';\n", encoding="utf-8")
        containers["mysql"] = docker("run", "-d", "--pull=never", "--name", runid + "-mysql", "--label", "carelink.visit-hpa=" + runid,
            "--network", runid, "-p", "127.0.0.1:13306:3306", "--tmpfs", "/var/lib/mysql:rw,size=1073741824", "--env-file", str(envfile),
            "--mount", f"type=bind,source={sqlfile},target=/docker-entrypoint-initdb.d/01.sql,readonly", "mysql:8.4", "--default-time-zone=+08:00")
        containers["redis"] = docker("run", "-d", "--pull=never", "--name", runid + "-redis", "--label", "carelink.visit-hpa=" + runid,
            "--network", runid, "-p", "127.0.0.1:16379:6379", "redis:7.4-alpine", "redis-server", "--save", "", "--appendonly", "no")
        load.write_json(private / "owned-resources.json", {"runId": runid, "network": network, "containers": containers})
        for _ in range(60):
            try:
                if query("SELECT 1;", "carelink") == "1": break
            except RuntimeError: pass
            time.sleep(1)
        else: raise RuntimeError("local_mysql_not_ready")
        for service in ("core", "visit"): start_service(service)
        load.write_json(private / "child-pids.json", {s: p.pid for s, p in processes.items()})
        for port in (18080, 18081): ready(port)
        checks.append("production_core_visit_real_redis_ready")
        load.require(query("SELECT COUNT(*) FROM app_user;", "carelink") == "0" and query("SELECT COUNT(*) FROM visit;") == "0", "refuse_nonempty_fixture_database")
        sql = ["START TRANSACTION;", f"INSERT INTO app_user(id,username,password_hash,display_name,enabled) VALUES(71001,'hpa-manager','{{noop}}{password}','Synthetic HPA manager',true);", "INSERT INTO user_role(user_id,role) VALUES(71001,'MANAGER');"]
        for i in range(4):
            uid, cg, elder = 71100 + i, 72100 + i, 73100 + i
            sql += [f"INSERT INTO app_user(id,username,password_hash,display_name,enabled) VALUES({uid},'hpa-cg-{i}','{{noop}}{password}','Synthetic HPA caregiver {i}',true);",
                    f"INSERT INTO user_role(user_id,role) VALUES({uid},'CAREGIVER');",
                    f"INSERT INTO caregiver(id,user_id,full_name,status,sector,dialects) VALUES({cg},{uid},'Synthetic HPA caregiver {i}','AVAILABLE','North','English');",
                    f"INSERT INTO elder(id,full_name,sector,preferred_dialects,address) VALUES({elder},'Synthetic HPA elder {i}','North','English','Local synthetic address');",
                    f"INSERT INTO elder_primary_caregiver(elder_id,caregiver_id) VALUES({elder},{cg});"]
        query("\n".join(sql + ["COMMIT;"]), "carelink")
        jar = http.cookiejar.CookieJar()
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), load.NoRedirect(), urllib.request.HTTPCookieProcessor(jar))
        def api(path, body=None, expected=200):
            headers = {"Accept": "application/json"}
            if body is not None:
                headers.update({"Content-Type": "application/json", "X-XSRF-TOKEN": urllib.parse.unquote(next(x.value for x in jar if x.name == "XSRF-TOKEN"))})
            with opener.open(urllib.request.Request("http://127.0.0.1:18080" + path, headers=headers, data=None if body is None else json.dumps(body).encode()), timeout=15) as r:
                load.require(r.status == expected, "fixture_http_status")
                raw = r.read(); return json.loads(raw) if raw else None
        api("/api/auth/csrf")
        identity = api("/api/auth/login", {"username": "hpa-manager", "password": password})
        load.require(identity["id"] == 71001 and "MANAGER" in identity["roles"], "wrong_fixture_manager")
        api("/api/auth/csrf")
        now = dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
        start = now.replace(second=0, microsecond=0)
        start += dt.timedelta(minutes=(-start.minute) % 30)
        if start < now: start += dt.timedelta(minutes=30)
        load.require(start.date() == now.date() and start.hour <= 21, "local_fixture_needs_same_day_window_before_2130")
        for i in range(4):
            plan = api("/api/care-plans", {"elderId": 73100 + i}, 201)
            pub = api(f"/api/care-plans/{plan['id']}/publish", {"startDate": start.date().isoformat(), "nodes": [{"groupName": "Personal care", "name": "Personal care", "visits": [{"day": start.strftime("%a").upper(), "startTime": start.strftime("%H:%M:%S"), "minutes": 90}], "evidenceType": "NONE"}]})
            load.require(pub["status"] == "PUBLISHED", "fixture_not_published")
            rows = json.loads(query(f"SELECT JSON_ARRAYAGG(JSON_OBJECT('id',id,'status',status,'version',version,'caregiverId',caregiver_id,'scheduledStart',scheduled_start)) FROM visit WHERE care_plan_id={plan['id']};"))
            load.require(len(rows) == 2 and all(r["caregiverId"] == 72100 + i and r["status"] == "SCHEDULED" for r in rows), "invalid_real_roster")
            today, future = sorted(rows, key=lambda r: r["scheduledStart"])
            load.require(today["scheduledStart"][:10] == now.date().isoformat(), "wrong_fixture_day")
            fixture.append({"visitId": today["id"], "futureVisitId": future["id"], "elderId": 73100 + i, "expectedVersion": 0, "expectedStatus": "SCHEDULED"})
        checks.append("four_independent_pairs_four_http_published_plans_eight_real_visits")
        base = load.read_json(Path(__file__).with_name("example.local.json"))
        base.update(runId=runid + "-read", maxVUs=4, maxIterations=100,
                    phases=[{"name": "baseline", "rps": 1, "durationSeconds": 4}, {"name": "step-2", "rps": 2, "durationSeconds": 4}, {"name": "cooldown", "rps": 0, "durationSeconds": 1}])
        base["actors"] = []
        for i, v in enumerate(fixture):
            envname = f"LOCAL_HPA_CG{i}_USERNAME"
            os.environ[envname] = f"hpa-cg-{i}"
            base["actors"].append({"alias": f"synthetic-cg-{i}", "userId": 71100 + i, "usernameEnv": envname, "passwordEnv": "LOCAL_HPA_PASSWORD", "visits": [{k: v[k] for k in ("visitId", "elderId", "expectedVersion", "expectedStatus")} ]})
        os.environ["LOCAL_HPA_PASSWORD"] = password
        run_config(base, "read-before")
        writing = json.loads(json.dumps(base)); writing.update(runId=runid + "-write", scenario="check-in", phases=[{"name": "fresh-checkins", "rps": 2, "durationSeconds": 2}])
        run_config(writing, "checkin")
        checks.append("four_fresh_checkins_replayed_false_and_http_state_recheck")
        load.require(query("SELECT COUNT(*) FROM visit_check_in_record;") == "4", "wrong_checkin_count")
        load.require(query("SELECT COUNT(*) FROM caregiver_command_receipt WHERE action_code='CHECK_IN';") == "4", "wrong_receipt_count")
        for v in fixture:
            load.require(query(f"SELECT CONCAT(status,':',version) FROM visit WHERE id={v['visitId']};") == "IN_PROGRESS:1", "saved_state_mismatch")
            load.require(query(f"SELECT CONCAT(status,':',version) FROM visit WHERE id={v['futureVisitId']};") == "SCHEDULED:0", "future_control_changed")
        checks.append("database_four_unique_receipts_checkins_and_four_untouched_future_controls")
        after = json.loads(json.dumps(base)); after["runId"] = runid + "-read-after"
        for actor in after["actors"]:
            actor["visits"][0].update(expectedStatus="IN_PROGRESS", expectedVersion=1)
        run_config(after, "read-after")
        checks.append("repeatable_reads_before_and_after_write")
        load.write_json(private / "fixtures.json", fixture)
        result.update(status="PASS", runs=runs, formalHpaOrSlaAcceptance=False)
    except Exception as e:
        # Local logs remain private; only stable error categories are shareable.
        result.update(status="FAIL", errorType=type(e).__name__, errorCategory=str(e) if isinstance(e, (ValueError, RuntimeError)) else "inspect_private_runtime_logs")
        raise
    finally:
        for p in processes.values():
            if p.poll() is None:
                p.terminate()
                try: p.wait(timeout=15)
                except subprocess.TimeoutExpired: p.kill(); p.wait(timeout=5)
        for f in logs: f.close()
        cleanup = {"ownedContainersRemoved": True, "ownedNetworkRemoved": True}
        for service, ident in containers.items():
            label = docker("inspect", "--format", '{{index .Config.Labels "carelink.visit-hpa"}}', ident)
            name = docker("inspect", "--format", "{{.Name}}", ident)
            if label != runid or name != "/" + runid + "-" + service:
                cleanup["ownedContainersRemoved"] = False
                continue
            docker("rm", "-f", "-v", ident)
        if network:
            label = docker("network", "inspect", "--format", '{{index .Labels "carelink.visit-hpa"}}', network)
            if label == runid: docker("network", "rm", network)
            else: cleanup["ownedNetworkRemoved"] = False
        cleanup["originalContainersUnchanged"] = sorted(before) == sorted(docker("ps", "-a", "--format", "{{.ID}} {{.Names}} {{.State}}").splitlines())
        cleanup["temporaryPortsClosed"] = True
        for port in (13306, 16379, 18080, 18081):
            with socket.socket() as sock:
                if sock.connect_ex(("127.0.0.1", port)) == 0: cleanup["temporaryPortsClosed"] = False
        result["cleanup"] = cleanup
        if not all(cleanup.values()): result["status"] = "FAIL"
        load.write_json(root / "local-validation.json", result)
        print(json.dumps({"status": result["status"], "checks": len(checks), "cleanup": cleanup}))
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__": raise SystemExit(main())
