"""Exercise skip/rollout and bounded concurrency with local Helm/Kubernetes stubs."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SERVICES = ("core", "visit", "report", "notification")
REGISTRY = "123456789012.dkr.ecr.ap-southeast-1.amazonaws.com"


class IncrementalDeployTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        records = {s: {"service": s, "tag": "a" * 40, "image": f"{REGISTRY}/carelink/{s}",
                       "digest": "sha256:" + str(i) * 64} for i, s in enumerate(SERVICES, 1)}
        (self.directory / "release.json").write_text(json.dumps({"schema_version": 1, "services": records}))
        stub = '''#!/usr/bin/env python3
import fcntl,json,os,pathlib,sys,time
p=pathlib.Path(os.environ['FIXTURE']);args=sys.argv[1:];tool=pathlib.Path(sys.argv[0]).name
with (p/'calls').open('a') as f: f.write(json.dumps([tool,*args])+'\\n')
if tool=='kubectl':
 if 'configmap' in args: print('public-ca')
 elif 'secret' in args: print('\\n'.join('DB_URL DB_USER DB_PASSWORD SPRING_DATA_REDIS_HOST SPRING_DATA_REDIS_PORT SPRING_DATA_REDIS_PASSWORD SPRING_DATA_REDIS_SSL_ENABLED CARELINK_EVENTS_TOPICARN CARELINK_EVENTS_QUEUEURL CARELINK_WEBHOOKS_ENCRYPTIONKEY'.split()))
 elif 'get' in args:
  s=args[args.index('deployment')+1];r=json.loads((p/'release.json').read_text())['services'][s];print(r['image']+'@'+r['digest'])
 sys.exit(0)
if args[0]=='template': print('desired-'+args[1]);sys.exit(0)
if args[:2]==['get','manifest']:
 print(('desired-' if os.environ['UNCHANGED']=='true' else 'old-')+args[2]);sys.exit(0)
s=args[2]
def record(start):
 with (p/'state').open('a+') as f:
  fcntl.flock(f,fcntl.LOCK_EX);f.seek(0);state=json.loads(f.read() or '{"active":0,"maximum":0,"events":[]}')
  state['active']+=1 if start else -1;state['maximum']=max(state['maximum'],state['active'])
  state['events'].append([s,'start' if start else 'end']);f.seek(0);f.truncate();json.dump(state,f)
record(True);time.sleep(0.2);record(False)
sys.exit(1 if os.environ.get('FAIL_SERVICE')==s else 0)
'''
        for name in ("helm", "kubectl"):
            path = self.directory / name
            path.write_text(stub)
            path.chmod(0o700)

    def run_deploy(self, unchanged=False, fail=""):
        env = dict(os.environ, PATH=f"{self.directory}:{os.environ['PATH']}", FIXTURE=str(self.directory),
                   IMAGE_REGISTRY=REGISTRY, IMAGE_TAG="", RELEASE_MANIFEST=str(self.directory / "release.json"),
                   KUBE_CONTEXT="test-context", DRY_RUN="0", PUBLIC_HTTPS="false",
                   UNCHANGED=str(unchanged).lower(), FAIL_SERVICE=fail)
        result = subprocess.run(["bash", "scripts/deploy.sh", "all", "staging"], cwd=ROOT,
                                env=env, capture_output=True, text=True)
        calls = [json.loads(line) for line in (self.directory / "calls").read_text().splitlines()]
        return result, calls

    def test_unchanged_services_do_not_upgrade_and_all_live_rollouts_are_checked(self):
        result, calls = self.run_deploy(unchanged=True)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(any(c[:2] == ["helm", "upgrade"] for c in calls))
        checks = [c for c in calls if c[0] == "kubectl" and "rollout" in c]
        self.assertEqual(len(checks), 4)

    def test_core_is_ready_first_and_at_most_two_other_services_overlap(self):
        result, calls = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        state = json.loads((self.directory / "state").read_text())
        self.assertEqual(state["events"][:2], [["core", "start"], ["core", "end"]])
        self.assertEqual(state["maximum"], 2)
        self.assertEqual(state["active"], 0)
        self.assertEqual(len([c for c in calls if c[:2] == ["helm", "upgrade"]]), 4)

    def test_failed_parallel_release_fails_deployment_and_waits_for_other_inflight_release(self):
        result, _ = self.run_deploy(fail="visit")
        self.assertNotEqual(result.returncode, 0)
        state = json.loads((self.directory / "state").read_text())
        self.assertEqual(state["active"], 0)
        self.assertIn(["report", "end"], state["events"])
        self.assertNotIn(["notification", "start"], state["events"])


if __name__ == "__main__":
    unittest.main()
