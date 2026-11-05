"""Main candidate binding and complete offline history checks; synthetic exports only."""
import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT))
import candidate_lock as candidate


class CandidateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.lock=candidate.build("HEAD","bfad2c7a3a8bbd03123389f9c6be7688b1864ce4")

    def exported(self):
        return {"schemaVersion":1,"databases":[dict(service=s,database="synthetic_"+s,
            capturedBy="LOCAL UNIT TEST",capturedAtUtc="2030-01-01T00:00:00Z",completeHistory=True,
            rows=[dict(installed_rank=i+1,type="SQL",success=True,description="synthetic",
                       installed_on="2030-01-01 00:00:00",version=r["version"],script=r["script"],
                       checksum=r["flywayChecksum"]) for i,r in enumerate(
                           x for x in self.lock["migrations"] if x["service"]==s)]) for s in candidate.legacy.SERVICES]}

    def test_main_lineage_and_no_deployment_approval(self):
        scripts={r["version"]:r["script"] for r in self.lock["migrations"] if r["service"]=="core"}
        self.assertEqual(scripts["26"],"V26__caregiver_credential_submission.sql")
        self.assertEqual(scripts["27"],"V27__caregiver_admission.sql")
        self.assertEqual(candidate.check_history(self.lock,self.exported())["status"],"SOURCE_HISTORY_COMPATIBLE")
        self.assertFalse(self.lock["deploymentApproved"])

    def test_old_core_numbering_stops(self):
        e=self.exported()
        r=next(r for r in e["databases"][0]["rows"] if r["version"]=="26")
        r["script"]="V26__caregiver_admission.sql"
        result=candidate.check_history(self.lock,e)
        self.assertEqual(result["status"],"STOP_REVIEW")
        self.assertIn("OLD_CORE_NUMBERING_STOP",result["services"][0]["issues"])

    def test_platform_history_is_separate_and_not_approved_by_primary_match(self):
        self.assertTrue(self.lock["platformMigrations"])
        self.assertTrue(all(r["historyTable"]=="flyway_platform_history" for r in self.lock["platformMigrations"]))
        result=candidate.check_history(self.lock,self.exported())
        self.assertFalse(result["platformHistoryVerified"])
        self.assertFalse(result["actualSchemaVerified"])
        self.assertFalse(result["deploymentApproved"])

    def test_all_four_complete_exports_required(self):
        for field in ("completeHistory","capturedBy"):
            e=self.exported();del e["databases"][0][field]
            with self.assertRaises(ValueError):candidate.check_history(self.lock,e)
        e=self.exported();e["databases"].pop()
        with self.assertRaises(ValueError):candidate.check_history(self.lock,e)

    def test_ranks_must_be_integer_ordered_unique(self):
        for value in (True,"1",0,None,2):
            e=self.exported();e["databases"][0]["rows"][0]["installed_rank"]=value
            with self.subTest(value=value),self.assertRaises(ValueError):candidate.check_history(self.lock,e)

    def test_malformed_history_containers_fail_with_a_stable_error(self):
        for value in (None,[],{}, {"databases":None},{"databases":[None]},
                      {"databases":[{"rows":None}]},{"databases":[{"rows":[None]}]}):
            with self.subTest(value=value),self.assertRaises(ValueError):candidate.check_history(self.lock,value)

    def test_changed_manifest_source_or_untracked_file_rejected(self):
        changed=copy.deepcopy(self.lock);changed["applicationGitTree"]="0"*40
        with patch.object(candidate,"build",return_value=self.lock),self.assertRaisesRegex(ValueError,"candidate_manifest_changed"):
            candidate.verify(changed)
        for replies,error in (([b"services/core/changed"],"candidate_application_tree_changed"),
                              ([b"",b"services/core/new"],"untracked_application_files")):
            with patch.object(candidate,"build",return_value=self.lock),patch.object(candidate.legacy,"git",side_effect=replies),self.assertRaisesRegex(ValueError,error):
                candidate.verify(self.lock)

    def test_wrong_checksums_failed_rows_and_old_report_v4_still_stop(self):
        for mutation in ("checksum","success","old-v4"):
            e=self.exported();row=e["databases"][2]["rows"][3]
            if mutation=="checksum":row["checksum"]+=1
            if mutation=="success":row["success"]=1
            if mutation=="old-v4":row.update(script=self.lock["oldReportEvidenceV4"]["oldScript"],checksum=self.lock["oldReportEvidenceV4"]["flywayChecksum"])
            with self.subTest(mutation=mutation):self.assertEqual(candidate.check_history(self.lock,e)["status"],"STOP_REVIEW")


if __name__=="__main__":unittest.main()
