"""D3/D4 offline artifact guards; synthetic input is never a real DB/fixture verification."""
import copy
import datetime as dt
import importlib.util
import json
from pathlib import Path
import socket
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
def module(name):
    spec = importlib.util.spec_from_file_location("d34_" + name, ROOT / (name + ".py"))
    m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m); return m
release, fixture = module("release_lock"), module("fixture_guard")
NOW = dt.datetime(2030, 1, 1, 0, 5, tzinfo=dt.timezone.utc)

def whitelist():
    return json.loads((ROOT / "fixture-whitelist.example.json").read_text(encoding="utf-8"))

def approved():
    w=whitelist()
    w.update(environment="synthetic-local-unit-test", dataApprovalOwner="Test Owner")
    w["review"]={k:True for k in w["review"]}
    w["retention"].update(owner="Test Owner", approved=True)
    return w

def config(w):
    return {"scenario":"work-pack", "codeBaseline":w["sourceApplicationCommit"], "actors":[
        {"alias":r["alias"],"userId":r["userId"],
         "visits":[{k:r[k] for k in ("visitId","elderId","expectedStatus","expectedVersion","expectedTaskCount")}]}
        for r in w["rows"]]}

class HistoryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lock=json.loads((ROOT/"release-lock.json").read_text(encoding="utf-8"))

    def exported(self):
        return {"schemaVersion":1,"databases":[{"service":s,"database":"synthetic_"+s,
            "capturedBy":"UNIT TEST", "capturedAtUtc":"2030-01-01T00:00:00Z",
            "rows":[dict(type="SQL",success=True,description="synthetic",installed_on="2030-01-01 00:00:00",
                         **{k:r[k] for k in ("version","script","checksum")}) for r in
                    [dict(x,checksum=x["flywayChecksum"]) for x in self.lock["migrations"] if x["service"]==s]]}
                    for s in release.SERVICES]}

    def check(self, e):
        return release.check_history(self.lock,e)

    def test_lock_has_all_41_migrations_and_version_limits(self):
        self.assertEqual(len(self.lock["migrations"]),41)
        self.assertEqual(self.lock["expectedVersions"],dict(core=28,visit=5,report=5,notification=3))
        self.assertTrue(self.lock["oldReportEvidenceV4"]["sameSqlAsNewV5"])
        self.assertEqual(self.lock["oldReportEvidenceV4"]["sharedDatabaseApplied"],"UNKNOWN")

    def test_full_target_does_not_approve_deployment(self):
        r=self.check(self.exported())
        self.assertEqual(r["status"],"SOURCE_HISTORY_COMPATIBLE")
        self.assertTrue(all(x["status"]=="TARGET_MATCH" for x in r["services"]))
        self.assertFalse(r["deploymentApproved"])

    def test_main_report_v4_is_valid_upgrade_prefix(self):
        e=self.exported(); e["databases"][2]["rows"].pop()
        r=self.check(e)
        self.assertEqual(r["services"][2]["status"],"UPGRADE_REQUIRED")
        self.assertEqual(r["services"][2]["pendingVersions"],["5"])

    def test_old_evidence_v4_stops(self):
        e=self.exported(); r=e["databases"][2]["rows"][3]
        r.update(script=self.lock["oldReportEvidenceV4"]["oldScript"],
                 checksum=self.lock["oldReportEvidenceV4"]["flywayChecksum"])
        result=self.check(e)
        self.assertEqual(result["status"],"STOP_REVIEW")
        self.assertIn("OLD_REPORT_EVIDENCE_V4_STOP",result["services"][2]["issues"])

    def test_same_filename_wrong_checksum_stops(self):
        e=self.exported(); e["databases"][0]["rows"][0]["checksum"]+=1
        self.assertEqual(self.check(e)["status"],"STOP_REVIEW")

    def test_success_bool_not_string_or_one(self):
        for v in (False,1,"true"):
            with self.subTest(v=v):
                e=self.exported(); e["databases"][0]["rows"][0]["success"]=v
                self.assertEqual(self.check(e)["status"],"STOP_REVIEW")

    def test_missing_and_duplicate_database_rejected(self):
        e=self.exported(); e["databases"].pop()
        with self.assertRaises(ValueError): self.check(e)
        e=self.exported(); e["databases"].append(copy.deepcopy(e["databases"][0]))
        with self.assertRaises(ValueError): self.check(e)

    def test_history_gap_and_duplicate_stops(self):
        for mutation in ("gap","duplicate"):
            with self.subTest(mutation=mutation):
                e=self.exported()
                if mutation=="gap": e["databases"][0]["rows"].pop(0)
                else: e["databases"][0]["rows"].append(copy.deepcopy(e["databases"][0]["rows"][0]))
                self.assertEqual(self.check(e)["status"],"STOP_REVIEW")

    def test_unknown_version_and_baseline_require_review(self):
        for v,t in (("99","SQL"),("1","BASELINE"),(None,"SCHEMA")):
            with self.subTest(version=v,type=t):
                e=self.exported(); e["databases"][0]["rows"][0].update(version=v,type=t)
                self.assertEqual(self.check(e)["status"],"STOP_REVIEW")

    def test_history_requires_provenance_and_metadata(self):
        e=self.exported(); del e["databases"][0]["capturedBy"]
        with self.assertRaises(ValueError): self.check(e)
        e=self.exported(); del e["databases"][0]["rows"][0]["installed_on"]
        self.assertEqual(self.check(e)["status"],"STOP_REVIEW")

    def test_checksum_line_endings_bom_and_signed_crc(self):
        base=b"-- sample\nSELECT 1;\n"
        self.assertEqual(release.flyway_checksum(base),release.flyway_checksum(base.replace(b"\n",b"\r\n")))
        self.assertEqual(release.flyway_checksum(base),release.flyway_checksum(b"\xef\xbb\xbf"+base))
        self.assertNotEqual(release.flyway_checksum(base),release.flyway_checksum(base+b" "))
        self.assertTrue(-(2**31)<=release.flyway_checksum(base)<2**31)

    def test_tampered_manifest_is_rejected(self):
        changed=copy.deepcopy(self.lock); changed["applicationCommit"]="0"*40
        with patch.object(release,"build_lock",return_value=self.lock):
            with self.assertRaisesRegex(ValueError,"fixed_manifest_changed"): release.verify(changed)


    def test_application_and_untracked_source_changes_rejected(self):
        for replies,error in (([b"services/core/change.java"],"application_tree_changed"),
                              ([b"",b"services/core/new.java"],"untracked_application_files")):
            with self.subTest(error=error),patch.object(release,"build_lock",return_value=self.lock), \
                 patch.object(release,"git",side_effect=replies):
                with self.assertRaisesRegex(ValueError,error): release.verify(self.lock)

    def test_engine_tamper_rejected(self):
        with patch.object(release,"build_lock",return_value=self.lock), \
             patch.object(release,"git",return_value=b""),patch.object(Path,"read_bytes",return_value=b"wrong"):
            with self.assertRaisesRegex(ValueError,"engine_file_changed"): release.verify(self.lock)

    def test_history_schema_boolean_or_database_nonobject_rejected(self):
        e=self.exported();e["schemaVersion"]=True
        with self.assertRaises(ValueError):self.check(e)
        e=self.exported();e["databases"][0]=[]
        with self.assertRaises(ValueError):self.check(e)

class FixtureTests(unittest.TestCase):
    def check(self,w=None,c=None,ready=False,now=NOW):
        return fixture.validate(w or whitelist(),c,now,ready)

    def test_example_structural_only_never_ready(self):
        r=self.check()
        self.assertEqual(r["pairs"],4)
        self.assertIn("dataApprovalOwner",r["pending"])
        self.assertFalse(r["executionApproved"])
        with self.assertRaises(ValueError): self.check(ready=True)

    def test_operator_inputs_complete_still_not_online_approval(self):
        w=approved(); r=self.check(w,config(w),True)
        self.assertTrue(r["offlineInputsComplete"])
        self.assertFalse(r["onlineStateVerified"])
        self.assertFalse(r["executionApproved"])

    def test_no_dns_or_socket_on_offline_validation(self):
        with patch.object(socket,"getaddrinfo",side_effect=AssertionError("network forbidden")), \
             patch.object(socket,"socket",side_effect=AssertionError("network forbidden")):
            self.check(approved(),config(approved()),True)

    def test_wrong_role_or_working_status_rejected(self):
        for k,v in (("role","MANAGER"),("canWork",False)):
            w=approved(); w["rows"][0][k]=v
            with self.subTest(k=k),self.assertRaises(ValueError): self.check(w)

    def test_boolean_and_float_ids_rejected(self):
        for v in (True,1.0,-1,0,"71100"):
            w=approved(); w["rows"][0]["userId"]=v
            with self.subTest(v=v),self.assertRaises(ValueError): self.check(w)

    def test_independent_accounts_and_entities_required(self):
        for k in ("alias","userId","caregiverId","elderId","carePlanId","visitId"):
            w=approved(); w["rows"][1][k]=w["rows"][0][k]
            with self.subTest(k=k),self.assertRaises(ValueError): self.check(w)

    def test_not_enough_pairs_rejected(self):
        w=approved(); w["rows"].pop()
        with self.assertRaises(ValueError): self.check(w)

    def test_state_version_and_shape_must_match_profile(self):
        for k,v in (("expectedStatus","IN_PROGRESS"),("expectedVersion",1),("expectedVersion",False),
                    ("expectedTaskCount",1),("expectedTaskCount",False),("serviceInstructionsCount",0)):
            w=approved(); w["rows"][0][k]=v
            with self.subTest(k=k,v=v),self.assertRaises(ValueError): self.check(w)

    def test_visit_cannot_start_during_window(self):
        w=approved(); w["rows"][0]["scheduledStartUtc"]=w["validUntilUtc"]
        with self.assertRaises(ValueError): self.check(w)

    def test_stale_or_future_precheck_not_ready(self):
        for value in ("2029-12-31T22:00:00Z","2030-01-01T00:06:00Z"):
            w=approved(); w["rows"][0]["observedAtUtc"]=value
            with self.subTest(value=value),self.assertRaises(ValueError): self.check(w,ready=True)

    def test_window_timezone_retention_and_expiry(self):
        w=approved()
        with self.assertRaises(ValueError): self.check(w,ready=True,now=NOW+dt.timedelta(days=1))
        w["timeZone"]="UTC"
        with self.assertRaises(ValueError): self.check(w)
        w=approved(); w["retention"]["reviewByUtc"]="2029-12-31T23:00:00Z"
        with self.assertRaises(ValueError): self.check(w,ready=True)

    def test_secrets_and_username_fields_refused(self):
        for key in ("password","Cookie","xsrfToken","username","secretValue"):
            w=approved(); w["rows"][0][key]="fake-sensitive-test"
            with self.subTest(key=key),self.assertRaises(ValueError): self.check(w)

    def test_configuration_must_be_whitelisted(self):
        w=approved(); c=config(w); c["actors"][0]["visits"][0]["visitId"]=999999
        with self.assertRaises(ValueError): self.check(w,c)

    def test_wrong_actor_assignment_refused(self):
        w=approved(); c=config(w); c["actors"][0]["userId"]+=1
        with self.assertRaises(ValueError): self.check(w,c)

    def test_omitted_pairs_and_wrong_shape_refused(self):
        w=approved(); c=config(w); c["actors"].pop()
        with self.assertRaises(ValueError): self.check(w,c)
        c=config(w); c["actors"][0]["visits"][0]["expectedTaskCount"]=1
        with self.assertRaises(ValueError): self.check(w,c)

    def test_malformed_config_containers_refused(self):
        for c in ([], {"actors":[],"scenario":"work-pack"}, {"actors":[[]]},
                  {"actors":[{"visits":[[]]}],"scenario":"work-pack","codeBaseline":approved()["sourceApplicationCommit"]}):
            with self.subTest(config=c),self.assertRaises(ValueError):self.check(approved(),c)

    def test_B_and_application_mismatch_refused(self):
        w=approved()
        for k,v in (("scenario","check-in"),("codeBaseline","wrong")):
            c=config(w); c[k]=v
            with self.subTest(k=k),self.assertRaises(ValueError): self.check(w,c)


class HandoffTemplateTests(unittest.TestCase):
    def test_four_pair_formal_candidate_preserves_budget_and_whitelist(self):
        c=json.loads((ROOT/"example.joint.four-pair.json").read_text(encoding="utf-8"))
        spec=importlib.util.spec_from_file_location("d6_load_template",ROOT/"load.py")
        loader=importlib.util.module_from_spec(spec);spec.loader.exec_module(loader)
        with patch.object(socket,"socket",side_effect=AssertionError("network forbidden")), \
             patch.object(socket,"getaddrinfo",side_effect=AssertionError("DNS forbidden")):
            plan=loader.validate(c)
            checked=fixture.validate(whitelist(),c,NOW)
        self.assertEqual((plan["plannedIterations"],c["maxVUs"],len(c["actors"])),(2700,8,4))
        self.assertFalse(c["approval"]["confirmed"])
        self.assertFalse(checked["executionApproved"])
        self.assertEqual(len({a["passwordEnv"] for a in c["actors"]}),4)
        self.assertFalse(plan["readBudget"]["inputsReviewed"])

    def test_four_pair_remote_template_is_rejected_before_dns_or_worker(self):
        c=json.loads((ROOT/"example.joint.four-pair.json").read_text(encoding="utf-8"))
        spec=importlib.util.spec_from_file_location("d6_load_refusal",ROOT/"load.py")
        loader=importlib.util.module_from_spec(spec);spec.loader.exec_module(loader)
        with patch.object(socket,"getaddrinfo",side_effect=AssertionError("DNS forbidden")), \
             patch.object(loader.subprocess,"Popen",side_effect=AssertionError("worker forbidden")):
            with self.assertRaises(ValueError):loader.validate(c,execute=True)

if __name__=="__main__": unittest.main()
