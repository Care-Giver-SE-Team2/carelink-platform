"""Main-candidate normal fixtures and 4x2 sessions on 2 core/2 visit processes.

Requires already-built JARs, Java 25 and cached mysql:8.4 / redis:7.4-alpine.
Creates only labelled disposable containers; no downloads or shared DB cleanup.
"""
import argparse
import concurrent.futures as futures
import candidate_lock
import datetime as dt
import hashlib
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import signal
import sys
import socket
import subprocess
import time
import urllib.parse
import urllib.request
import uuid

import load
import fixture_guard



def interrupt_cli(process):
    if os.name != "nt":
        os.kill(process.pid, signal.SIGINT)
        return
    code = ("import ctypes,sys,time; k=ctypes.WinDLL('kernel32',use_last_error=True); "
            "k.FreeConsole(); assert k.AttachConsole(int(sys.argv[1])),ctypes.get_last_error(); "
            "assert k.SetConsoleCtrlHandler(None,True); "
            "assert k.GenerateConsoleCtrlEvent(0,0),ctypes.get_last_error(); time.sleep(.15); k.FreeConsole()")
    completed = subprocess.run([sys.executable,"-c",code,str(process.pid)],capture_output=True,
                              timeout=4,creationflags=subprocess.CREATE_NO_WINDOW)
    load.require(completed.returncode==0,"native_ctrl_c_failed")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--core-jar", required=True)
    parser.add_argument("--visit-jar", required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--docker-context", default="desktop-linux")
    parser.add_argument("--output", required=True, help="New private directory; never commit its contents")
    parser.add_argument("--include-handoff-checks", action="store_true", help="After D4 passes, run two local CLI pilots and STOP/Ctrl+C; no HPA")
    parser.add_argument("--candidate-manifest", required=True)
    parser.add_argument("--build-manifest", required=True)
    args = parser.parse_args()
    root = Path(args.output).resolve()
    load.require(not root.exists(), "new_output_directory_required")
    jars = {s: Path(getattr(args, s + "_jar")).resolve(strict=True) for s in ("core", "visit")}
    for port in (13306, 16379, 18080, 18081, 18082, 18083):
        with socket.socket() as sock:
            load.require(sock.connect_ex(("127.0.0.1", port)) != 0, "local_port_already_used")
    lock = load.read_json(Path(args.candidate_manifest))
    candidate_lock.verify(lock)
    build = load.read_json(Path(args.build_manifest))
    load.require(build["applicationCommit"] == lock["applicationCommit"], "build_application_mismatch")
    pinned = build["jarHashes"]
    for service, jar in jars.items():
        load.require(hashlib.sha256(jar.read_bytes()).hexdigest() == pinned[service], "candidate_jar_mismatch")
    root.mkdir(parents=True)
    private = root / "runtime-private"; private.mkdir(mode=0o700)
    runid = "hpa-" + dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S") + "-" + secrets.token_hex(3)
    runid = runid.lower()
    dbpass, password = secrets.token_hex(24), "LocalHpa-" + secrets.token_hex(12)
    dc = ["docker", "--context", args.docker_context]
    def docker(*command, sql=None, check=True):
        p = subprocess.run(dc + list(command), input=sql, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=45)
        if check and p.returncode:
            raise RuntimeError("local_docker_operation_failed:" + command[0])
        return p.stdout.strip()
    before = docker("ps", "-a", "--format", "{{.ID}} {{.Names}} {{.State}}").splitlines()
    containers, processes, logs = {}, {}, []
    checks, runs, network = [], [], None
    load_processes = []
    fixture = []
    result = {"runId": runid, "status": "RUNNING", "scope": "localhost-only; events disabled; no HPA", "checks": checks,
              "applicationCommit":lock["applicationCommit"], "mainBaseCommit":lock["mainBaseCommit"], "applicationJars": {s: hashlib.sha256(j.read_bytes()).hexdigest() for s, j in jars.items()}}
    load.write_json(root / "local-validation.json", result)
    def query(sql, db="carelink_visit"):
        load.require(db in ("carelink", "carelink_visit"), "invalid_db")
        return docker("exec", "-i", containers["mysql"], "sh", "-c",
                      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --default-character-set=utf8mb4 -uroot --batch --raw -N ' + db, sql=sql)
    def start_service(instance):
        service=instance.split("-")[0]
        port={"core-a":18080,"core-b":18082,"visit-a":18081,"visit-b":18083}[instance]
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
        properties = private / (instance + ".properties")
        properties.write_text(config, encoding="utf-8"); properties.chmod(0o600)
        env = {k: v for k, v in os.environ.items() if not k.startswith(("AWS_", "SPRING_", "CARELINK_", "DB_")) and k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")}
        env.update(AWS_EC2_METADATA_DISABLED="true", SPRING_PROFILES_ACTIVE="default", APP_COMMIT=lock["applicationCommit"])
        stdout, stderr = (root / (instance + ".log")).open("w", encoding="utf-8"), (root / (instance + "-error.log")).open("w", encoding="utf-8")
        logs.extend([stdout, stderr])
        processes[instance] = subprocess.Popen([args.java, "-Xmx384m", "-Duser.timezone=Asia/Singapore", "-jar", str(jars[service]),
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
        for instance in ("core-a", "core-b", "visit-a", "visit-b"): start_service(instance)
        load.write_json(private / "child-pids.json", {s: p.pid for s, p in processes.items()})
        for port in (18080, 18081, 18082, 18083): ready(port)
        checks.append("production_core_visit_real_redis_ready")

        load.require(query("SELECT COUNT(*) FROM app_user;", "carelink") == "0" and query("SELECT COUNT(*) FROM visit;") == "0", "refuse_nonempty_fixture_database")
        # Only the otherwise-unavailable initial manager is bootstrapped in the disposable empty DB.
        query(f"INSERT INTO app_user(id,username,password_hash,display_name,enabled) VALUES(71001,'hpa-manager','{{noop}}{password}','Synthetic HPA manager',true);\nINSERT INTO user_role(user_id,role) VALUES(71001,'MANAGER');", "carelink")
        class Api:
            def __init__(self):
                self.core_port, self.visit_port = 18080, 18081
                self.jar = http.cookiejar.CookieJar()
                self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), load.NoRedirect(), urllib.request.HTTPCookieProcessor(self.jar))
                self.last_bytes = 0
            def session(self):
                return next((x.value for x in self.jar if x.name == "SESSION"), None)
            def call(self, path, body=None, expected=200, method=None, visit=False):
                headers = {"Accept": "application/json"}
                if body is not None:
                    token = next((x.value for x in self.jar if x.name == "XSRF-TOKEN"), None)
                    load.require(bool(token), "fixture_csrf_missing")
                    headers.update({"Content-Type": "application/json", "X-XSRF-TOKEN": urllib.parse.unquote(token)})
                req = urllib.request.Request(("http://127.0.0.1:"+str(self.visit_port if visit else self.core_port)) + path,
                    headers=headers, data=None if body is None else json.dumps(body).encode(), method=method)
                try:
                    with self.opener.open(req, timeout=15) as response:
                        load.require(response.status == expected, "fixture_http_status")
                        raw = response.read(8 * 1024 * 1024 + 1)
                        self.last_bytes = len(raw)
                        load.require(len(raw) <= 8 * 1024 * 1024, "fixture_body_limit")
                        return json.loads(raw) if raw else None
                except urllib.error.HTTPError as error:
                    if error.code == expected:
                        return None
                    # Never share response bodies, identities or credential-bearing arguments.
                    raise RuntimeError("fixture_http_" + str(error.code) + ":" + path.split("?")[0]) from None
            def login(self, username, user_id, role):
                self.call("/api/auth/csrf")
                value = self.call("/api/auth/login", {"username": username, "password": password})
                load.require(value["id"] == user_id and role in value["roles"], "fixture_wrong_login")
                self.call("/api/auth/csrf")
                value = self.call("/api/auth/me")
                load.require(value["id"] == user_id and role in value["roles"], "fixture_wrong_identity")
        manager = Api(); manager.login("hpa-manager", 71001, "MANAGER")
        now = dt.datetime.now(dt.timezone(dt.timedelta(hours=8)))
        start = (now + dt.timedelta(days=1)).replace(hour=10, minute=0, second=0, microsecond=0)
        actors, caregiver_clients, rows_for_whitelist = [], [], []
        for i, word in enumerate(("Alpha", "Beta", "Gamma", "Delta")):
            name = runid + "-cg-" + str(i)
            applicant = Api(); applicant.call("/api/auth/csrf")
            registered = applicant.call("/api/caregiver-registrations", {
                "username": name, "password": password, "fullName": "Synthetic Caregiver " + word,
                "phone": "+65800001" + str(i).zfill(2), "declaredDialects": "English", "requestedSector": "North"}, 201)
            uid, cg, application = registered["userId"], registered["caregiverId"], registered["application"]["id"]
            load.require(registered["application"]["status"] == "PENDING" and registered["canWork"] is False, "registration_not_pending")
            item = manager.call(f"/api/caregiver-applications/{application}")
            load.require(item["caregiverId"] == cg and item["username"] == name and item["certificates"] == [], "wrong_approval_subject")
            decision = manager.call(f"/api/caregiver-applications/{application}/approve", {
                "message": "Synthetic local visit HPA fixture", "expectedVersion": item["version"], "clientRequestId": str(uuid.uuid4())})
            load.require(decision["status"] == "APPROVED" and decision["caregiverId"] == cg, "admission_not_approved")
            caregiver = Api(); caregiver.login(name, uid, "CAREGIVER")
            own = caregiver.call("/api/caregivers/me/application")
            load.require(own["canWork"] is True and own["application"]["status"] == "APPROVED", "caregiver_cannot_work")
            saved_session = caregiver.session()
            profile = caregiver.call("/api/caregivers/me", visit=True)
            load.require(profile["id"] == cg and profile["userId"] == uid and caregiver.session() == saved_session, "shared_session_failed")
            elder_signup = Api(); elder_signup.call("/api/auth/csrf")
            elder_name = "Synthetic Elder " + word
            elder_registered = elder_signup.call("/api/elder-registrations", {
                "fullName": elder_name, "username": runid + "-elder-" + str(i), "password": password}, 201)
            matches = [e for e in manager.call("/api/elders") if e["fullName"] == elder_name]
            load.require(len(matches) == 1, "ambiguous_fixture_elder")
            elder = manager.call(f"/api/elders/{matches[0]['id']}")
            load.require(elder["userId"] == elder_registered["userId"], "elder_user_link_mismatch")
            elderid = elder["id"]
            eligible = [c for c in manager.call(f"/api/caregivers?elderId={elderid}") if c["id"] == cg]
            load.require(len(eligible) == 1 and eligible[0]["assignable"] is True, "fixture_not_assignable")
            assignment = manager.call(f"/api/elders/{elderid}/primary-caregiver", {"caregiverId": cg}, method="PUT")
            load.require(assignment["caregiverId"] == cg, "wrong_primary_caregiver")
            plan = manager.call("/api/care-plans", {"elderId": elderid}, 201)
            published = manager.call(f"/api/care-plans/{plan['id']}/publish", {
                "startDate": start.date().isoformat(), "nodes": [{"groupName": "Personal care", "name": "Personal care",
                "visits": [{"day": start.strftime("%a").upper(), "startTime": "10:00:00", "minutes": 90}], "evidenceType": "NONE"}]})
            load.require(published["status"] == "PUBLISHED", "fixture_not_published")
            schedule = caregiver.call("/api/caregivers/me/schedule?dateFrom=" + start.date().isoformat() +
                "&dateTo=" + (start + dt.timedelta(days=7)).date().isoformat(), visit=True)
            selected = [v for v in schedule["upcomingVisits"] if v["elderId"] == elderid and
                        v["scheduledStart"].startswith(start.date().isoformat())]
            load.require(len(selected) == 1 and selected[0]["status"] == "SCHEDULED" and selected[0]["version"] == 0, "invalid_published_roster")
            chosen = selected[0]
            pack = caregiver.call(f"/api/visits/{chosen['id']}/work-pack", visit=True)
            expected = {"visitId": chosen["id"], "elderId": elderid, "expectedStatus": "SCHEDULED", "expectedVersion": 0, "expectedTaskCount": 0}
            load.Client.pack(pack, expected)
            load.require(pack["carePlanId"] == plan["id"] and pack["serviceInstructions"] == ["Personal care"] and
                caregiver.session() == saved_session, "wrong_plan_shape_or_session")
            envname = "LOCAL_A_CG" + str(i) + "_USERNAME"; os.environ[envname] = name
            actors.append({"alias": "synthetic-cg-" + str(i + 1), "userId": uid, "usernameEnv": envname,
                           "passwordEnv": "LOCAL_A_PASSWORD", "visits": [expected]})
            caregiver_clients.append(caregiver)
            rows_for_whitelist.append({"alias": actors[-1]["alias"], "userId": uid, "caregiverId": cg,
                "elderId": elderid, "carePlanId": plan["id"], **expected, "role": "CAREGIVER", "canWork": True,
                "serviceInstructionsCount": 1, "responseBytes": caregiver.last_bytes, "observedAtUtc": load.utc(),
                "scheduledStartUtc": start.astimezone(dt.timezone.utc).isoformat()})
        os.environ["LOCAL_A_PASSWORD"] = password
        checks.extend(["four_normal_certificate_free_registrations_and_manager_approvals",
            "four_normal_elder_registrations_user_links_primary_assignments_published_plans",
            "four_future_scheduled_v0_empty_tasks_one_service_instruction",
            "four_unchanged_core_to_visit_session_cookies"])
        anonymous = Api()
        anonymous.call(f"/api/visits/{actors[0]['visits'][0]['visitId']}/work-pack", expected=401, visit=True)
        caregiver_clients[0].call(f"/api/visits/{actors[1]['visits'][0]['visitId']}/work-pack", expected=403, visit=True)
        checks.append("anonymous_and_cross_caregiver_reads_rejected")
        uids = ",".join(str(a["userId"]) for a in actors)
        vids = ",".join(str(a["visits"][0]["visitId"]) for a in actors)
        def audits():
            value = query(f"SELECT COUNT(*) FROM audit_log WHERE actor_user_id IN ({uids}) AND resource_id IN ({vids}) AND resource_type='VISIT_WORK_PACK' AND action='READ' AND result='OK';")
            return int(value)
        def state():
            return query("SELECT CONCAT_WS('|',id,elder_id,caregiver_id,care_plan_id,status,version,scheduled_start) FROM visit ORDER BY id;")
        frozen = state()
        load.require(int(query("SELECT COUNT(*) FROM visit;")) >= 4, "missing_generated_visits")
        load.require(query("SELECT COUNT(*) FROM visit_check_in_record;") == "0", "unexpected_checkin")

        import base64
        all_clients = []
        identities = []
        for i, first in enumerate(caregiver_clients):
            second = Api(); second.core_port=18082; second.visit_port=18083
            second.login(os.environ[actors[i]["usernameEnv"]], actors[i]["userId"], "CAREGIVER")
            all_clients.extend([(i,first),(i,second)])
        cookies=[c.session() for _,c in all_clients]
        load.require(len(set(cookies))==8, "sessions_not_independent")
        for index,(i,c) in enumerate(all_clients):
            cookie=c.session()
            key=f"carelink-hpa:{runid}:session:sessions:"+base64.b64decode(cookie).decode()
            fields=docker("exec",containers["redis"],"redis-cli","--raw","HKEYS",key).splitlines()
            load.require({"creationTime","lastAccessedTime","maxInactiveInterval"}.issubset(fields),"incomplete_session_hash")
            for core_port,visit_port in ((18080,18081),(18082,18083)):
                c.core_port,c.visit_port=core_port,visit_port
                me=c.call("/api/auth/me")
                load.require(me["id"]==actors[i]["userId"] and me["roles"]==["CAREGIVER"],"cross_instance_wrong_identity")
                profile=c.call("/api/caregivers/me",visit=True)
                load.require(profile["id"]==rows_for_whitelist[i]["caregiverId"] and profile["userId"]==me["id"],"cross_instance_wrong_profile")
                pack=c.call(f"/api/visits/{rows_for_whitelist[i]['visitId']}/work-pack",visit=True)
                load.Client.pack(pack,actors[i]["visits"][0])
                load.require(c.session()==cookie,"session_cookie_changed")
            identities.append({"actor":i+1,"jar":index%2+1,"sessionFingerprint":hashlib.sha256(cookie.encode()).hexdigest(),"completeRedisHash":True,"crossCoreAndVisit":True})
        before_parallel=audits()
        def parallel_read(pair):
            i,c=pair
            for core_port,visit_port in ((18080,18081),(18082,18083)):
                c.core_port,c.visit_port=core_port,visit_port
                pack=c.call(f"/api/visits/{rows_for_whitelist[i]['visitId']}/work-pack",visit=True)
                load.Client.pack(pack,actors[i]["visits"][0])
            return True
        with futures.ThreadPoolExecutor(max_workers=8) as pool:
            load.require(all(pool.map(parallel_read,all_clients)),"parallel_read_failed")
        load.require(audits()-before_parallel==16 and state()==frozen,"parallel_audit_or_state_changed")
        for i,c in all_clients:
            c.call(f"/api/visits/{rows_for_whitelist[(i+1)%4]['visitId']}/work-pack",expected=403,visit=True)
        invalidations=[]
        for i,first in enumerate(caregiver_clients):
            second=all_clients[i*2+1][1]; old=first.session()
            first.call("/api/auth/logout",{},expected=204)
            key=f"carelink-hpa:{runid}:session:sessions:"+base64.b64decode(old).decode()
            load.require(docker("exec",containers["redis"],"redis-cli","EXISTS",key)=="0","logout_did_not_delete_key")
            for port,route in ((18080,"/api/auth/me"),(18082,"/api/auth/me"),(18081,f"/api/visits/{rows_for_whitelist[i]['visitId']}/work-pack"),(18083,f"/api/visits/{rows_for_whitelist[i]['visitId']}/work-pack")):
                req=urllib.request.Request(f"http://127.0.0.1:{port}"+route,headers={"Cookie":"SESSION="+old,"Accept":"application/json"})
                try:
                    urllib.request.build_opener(urllib.request.ProxyHandler({}),load.NoRedirect()).open(req,timeout=10)
                    raise RuntimeError("logged_out_cookie_was_accepted")
                except urllib.error.HTTPError as e: load.require(e.code==401,"logged_out_cookie_wrong_status")
            load.require(second.call("/api/auth/me")["id"]==actors[i]["userId"],"logout_removed_other_session")
            pack=second.call(f"/api/visits/{rows_for_whitelist[i]['visitId']}/work-pack",visit=True)
            load.Client.pack(pack,actors[i]["visits"][0])
            first.login(os.environ[actors[i]["usernameEnv"]],actors[i]["userId"],"CAREGIVER")
            invalidations.append({"actor":i+1,"oldSession401OnFourInstances":True,"otherIndependentSessionSurvives":True})
        load.require(state()==frozen,"dual_session_changed_business_state")
        generated=query("SELECT id,elder_id,caregiver_id,care_plan_id,status,version,scheduled_start FROM visit ORDER BY id;")
        (private/"all-generated-visits.tsv").write_text(generated+"\n",encoding="utf-8")
        load.write_json(root/"four-pair-dual-session.json",{"status":"PASS","pairs":4,"independentSessions":8,"instances":{"core":2,"visit":2},"identities":identities,"invalidations":invalidations,"concurrentSuccessfulReads":16,"crossActorDenials":8,"businessStateUnchanged":True})
        checks.append("four_pairs_eight_distinct_sessions_two_core_two_visit_parallel_permissions_logout_isolation")

        whitelist = load.read_json(Path(__file__).with_name("fixture-whitelist.example.json"))
        nowutc = dt.datetime.now(dt.timezone.utc)
        whitelist.update(familyId=runid, preparedBy="Chenyu local disposable acceptance",
            environment="localhost-disposable-only", dataApprovalOwner="Chenyu local test only",
            validFromUtc=(nowutc-dt.timedelta(minutes=1)).isoformat(), validUntilUtc=(nowutc+dt.timedelta(hours=2)).isoformat(),
            sourceApplicationCommit=lock["applicationCommit"], rows=rows_for_whitelist)
        whitelist["retention"].update(owner="Chenyu local test only", approved=True,
            reviewByUtc=(nowutc+dt.timedelta(hours=3)).isoformat())
        whitelist["review"] = {k: True for k in whitelist["review"]}
        # Local outer-environment teardown is explicit and separate from shared row retention.
        load.write_json(private / "fixture-lifecycle.json", {
            "scope": "Disposable local DB only", "keepRowsDuringAllArms": True,
            "explicitApprovedOuterTeardown": "Remove owned tmpfs DB/Redis after verification; no shared DELETE/repair/flush",
            "sharedEnvironmentRetentionApproval": False})
        base = load.read_json(Path(__file__).with_name("example.local.json"))
        base.update(runId=runid+"-d4", codeBaseline=lock["applicationCommit"], actors=actors, maxVUs=4, maxIterations=4,
            phases=[{"name":"d4-smoke","rps":1,"durationSeconds":4}])
        load.validate(base)
        fixture_guard.validate(whitelist, base, ready=True)
        load.write_json(private / "fixture-whitelist.json", whitelist)
        before_audit = audits()
        run_config(base, "d4-smoke")
        delta = audits() - before_audit
        load.require(delta == 8 and state() == frozen, "d4_audit_or_state_mismatch")
        checks.append("d4_four_business_gets_twenty_setup_eight_ok_audits_state_unchanged")
        result["d4"] = {"status":"PASS","businessGets":4,"setupHttp":20,"auditDelta":delta,
            "pairs":4,"generatedVisits":int(query("SELECT COUNT(*) FROM visit;")),
            "userAccounts":int(query("SELECT COUNT(*) FROM app_user;","carelink")),
            "executionTasks":0,"serviceInstructions":1,
            "observedResponseBytesRange":[min(r["responseBytes"] for r in rows_for_whitelist),max(r["responseBytes"] for r in rows_for_whitelist)]}
        load.write_json(root / "local-validation.json", result)
        print(json.dumps({"milestone":"D4_PASS","checks":len(checks),"next":"D6" if args.include_handoff_checks else "cleanup"}),flush=True)
        if args.include_handoff_checks:
            cli_cases = []
            def command(name, argv, expected):
                p = subprocess.run([sys.executable, *argv], capture_output=True, text=True, encoding="utf-8", timeout=60)
                (private/(name+".log")).write_text(p.stdout+"\n"+p.stderr,encoding="utf-8")
                load.require(p.returncode == expected, "local_cli_"+name)
                cli_cases.append({"name":name,"exitCode":p.returncode,"expected":expected})
                return p
            def save_config(c,name):
                cfg=private/(name+".json");load.write_json(cfg,c);return cfg
            def refresh(c,name):
                for row, api_client in zip(whitelist["rows"],caregiver_clients):
                    pack=api_client.call(f"/api/visits/{row['visitId']}/work-pack",visit=True)
                    load.Client.pack(pack,row)
                    load.require(pack["carePlanId"]==row["carePlanId"] and pack["serviceInstructions"]==["Personal care"],"precheck_shape_changed")
                    row.update(observedAtUtc=load.utc(),responseBytes=api_client.last_bytes)
                load.write_json(private/"fixture-whitelist.json",whitelist)
                config_path=save_config(c,name)
                command(name+"-dry", [str(Path(load.__file__)), "--config",str(config_path)],0)
                command(name+"-whitelist",[str(Path(fixture_guard.__file__)),"--whitelist",str(private/"fixture-whitelist.json"),
                    "--config",str(config_path),"--ready"],0)
                return config_path
            def cli_run(c,name,stop_kind=None):
                cfg=refresh(c,name)
                out=root/name
                options={}
                if os.name=="nt":
                    si=subprocess.STARTUPINFO();si.dwFlags|=subprocess.STARTF_USESHOWWINDOW;si.wShowWindow=0
                    options.update(creationflags=subprocess.CREATE_NEW_CONSOLE,startupinfo=si)
                before_count=audits()
                with (private/(name+"-cli.log")).open("w",encoding="utf-8") as stdout, (private/(name+"-error.log")).open("w",encoding="utf-8") as stderr:
                    proc=subprocess.Popen([sys.executable,str(Path(load.__file__)),"--config",str(cfg),"--output",str(out),"--execute"],
                        stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,**options)
                    load_processes.append(proc)
                    if stop_kind:
                        until=time.monotonic()+45
                        while time.monotonic()<until:
                            if proc.poll() is not None:raise RuntimeError("cli_exited_before_stop")
                            sample=out/"iterations.jsonl"
                            if sample.exists() and len(sample.read_text(encoding="utf-8").splitlines())>=3:break
                            time.sleep(.025)
                        else:raise RuntimeError("cli_did_not_begin_work")
                        if stop_kind=="file":(out/"STOP").touch()
                        else:interrupt_cli(proc)
                    exit_code=proc.wait(timeout=190 if not stop_kind else 30)
                summary=load.read_json(out/"summary.json")
                cleanup=load.read_json(out/"worker-cleanup.json")
                load.require(exit_code==(2 if stop_kind else 0),"wrong_load_exit")
                load.require(summary["outcome"]==("STOPPED" if stop_kind else "COMPLETED"),"wrong_load_outcome")
                load.require(summary["workerCleanupComplete"] and summary["accountingValid"] and cleanup["complete"],"cli_cleanup_or_accounting")
                load.require(summary["failedIterations"]==0 and summary["droppedIterations"]==0,"cli_failed_or_dropped")
                observed=audits()-before_count
                load.require(observed==summary["httpRequestsDuringLoad"]+c["maxVUs"],"cli_audit_count")
                load.require(state()==frozen,"cli_changed_business_state")
                row={"name":name,"exitCode":exit_code,"outcome":summary["outcome"],
                    "businessGets":summary["httpRequestsDuringLoad"],"setupHttp":summary["httpRequestsDuringSetup"],
                    "auditDelta":observed,"failed":summary["failedIterations"],"dropped":summary["droppedIterations"],
                    "clientEvidenceStatus":summary["clientEvidenceStatus"],"stopReasons":summary["stopReasons"],
                    "stopToSupervisorEndMs":summary["timing"]["stopToSupervisorEndMs"],
                    "workerCleanupComplete":True,"businessStateUnchanged":True}
                cli_cases.append(row)
                print(json.dumps({"milestone":name,"outcome":summary["outcome"],"businessGets":row["businessGets"]}),flush=True)
                return cfg
            pilot=json.loads(json.dumps(base))
            pilot.update(maxVUs=8,maxIterations=200,
                phases=[{"name":"pilot-1","rps":1,"durationSeconds":100},{"name":"pilot-5","rps":5,"durationSeconds":20},
                        {"name":"cooldown","rps":0,"durationSeconds":2}])
            pilot["runId"]=runid+"-local-A0"
            first_cfg=cli_run(pilot,"local-A0-client")
            pilot["runId"]=runid+"-local-A1"
            second_cfg=cli_run(pilot,"local-A1-client")
            comparison=root/"local-comparison.json"
            command("compare-without-resource-context",[str(Path(__file__).with_name("compare_read.py")),
                "--a0",str(root/"local-A0-client"),"--a1",str(root/"local-A1-client"),"--output",str(comparison)],2)
            report=load.read_json(comparison)
            load.require(report["status"]=="AWAITING_RESOURCE_REVIEW" and report["clientComparable"] is True and
                report["formalAcceptance"] is False and report["hpaMechanism"]=="NOT_EVALUATED","local_compare_overclaimed")
            checks.append("d6_two_cli_arms_two_hundred_gets_each_no_failures_drops_state_changes")
            checks.append("d6_client_evidence_comparable_but_no_hpa_or_resource_acceptance")
            stopping=json.loads(json.dumps(base))
            stopping.update(maxVUs=4,maxIterations=60,phases=[{"name":"stop-pilot","rps":2,"durationSeconds":30}])
            for name,kind in (("real-stop","file"),("real-ctrl-c","signal")):
                stopping["runId"]=runid+"-"+name
                cli_run(stopping,name,kind)
            checks.append("d6_real_service_stop_and_native_ctrl_c_cli_cleanup")
            # Failed output reuse and unapproved remote execution must not create/alter data.
            summary_hash=hashlib.sha256((root/"local-A0-client"/"summary.json").read_bytes()).hexdigest()
            command("reject-existing-load-output",[str(Path(load.__file__)),"--config",str(first_cfg),
                "--output",str(root/"local-A0-client"),"--execute"],2)
            load.require(hashlib.sha256((root/"local-A0-client"/"summary.json").read_bytes()).hexdigest()==summary_hash,"existing_output_changed")
            command("reject-existing-compare-output",[str(Path(__file__).with_name("compare_read.py")),"--a0",str(root/"local-A0-client"),
                "--a1",str(root/"local-A1-client"),"--output",str(comparison)],2)
            remote=load.read_json(Path(__file__).with_name("example.joint.json"));remote_cfg=save_config(remote,"unapproved-remote")
            command("reject-remote-before-execute",[str(Path(load.__file__)),"--config",str(remote_cfg),
                "--output",str(root/"must-not-exist"),"--execute"],2)
            load.require(not (root/"must-not-exist").exists(),"remote_output_created")
            checks.append("d6_existing_output_and_unapproved_remote_rejected")
            load.write_json(root/"d6-cli-checks.json",{"status":"PASS","cases":cli_cases,
                "comparisonStatus":report["status"],"cloudOrHpaTest":False})
            result["d6"]={"status":"PASS","cases":len(cli_cases),"clientComparable":True,
                "resourceStatus":"AWAITING_RESOURCE_REVIEW","hpaMechanism":"NOT_EVALUATED","formalAcceptance":False}
        load.require(query("SELECT COUNT(*) FROM visit_check_in_record;")=="0" and
            query("SELECT COUNT(*) FROM caregiver_command_receipt WHERE action_code='CHECK_IN';")=="0","unexpected_B_write")
        load.require(state()==frozen,"final_state_changed")
        checks.append("no_checkin_B_or_visit_state_version_assignment_changes")
        result.update(status="PASS",runs=runs,formalHpaOrSlaAcceptance=False,
            preparedData={"pairs":4,"scheduledStatus":"SCHEDULED:0","executionTasksPerVisit":0,"instructionsPerVisit":1,
            "totalGeneratedVisits":int(query("SELECT COUNT(*) FROM visit;")),
            "successfulWhitelistedReadAudits":audits()},
            localRetention="Rows frozen through all arms; explicitly-owned disposable tmpfs environment removed after checks; no shared data approval")
    except Exception as e:
        # Local logs remain private; only stable error categories are shareable.
        result.update(status="FAIL", errorType=type(e).__name__, errorCategory=str(e) if isinstance(e, (ValueError, RuntimeError)) else "inspect_private_runtime_logs")
        raise
    finally:
        for p in load_processes:
            if p.poll() is None:
                p.terminate()
                try: p.wait(timeout=10)
                except subprocess.TimeoutExpired: p.kill(); p.wait(timeout=5)
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
        for port in (13306, 16379, 18080, 18081, 18082, 18083):
            with socket.socket() as sock:
                if sock.connect_ex(("127.0.0.1", port)) == 0: cleanup["temporaryPortsClosed"] = False
        result["cleanup"] = cleanup
        if not all(cleanup.values()): result["status"] = "FAIL"
        load.write_json(root / "local-validation.json", result)
        print(json.dumps({"status": result["status"], "checks": len(checks), "cleanup": cleanup}))
    return 0 if result["status"] == "PASS" else 2


if __name__ == "__main__": raise SystemExit(main())
