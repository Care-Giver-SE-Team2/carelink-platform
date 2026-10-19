import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location("iac_scan", Path(__file__).resolve().parents[1] / "scan-iac.py")
scan = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(scan)


class IacScanTests(unittest.TestCase):
    def test_only_tracked_nonprivate_terraform_is_copied(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "repo"
            root.mkdir()
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            names = ["infra/main.tf", "infra/runtime/data.tf", "infra/.local/private.tf",
                     "infra/.terraform/cache.tf", "infra/secrets.tfvars", "infra/state.tfstate"]
            for name in names + ["infra/untracked.tf"]:
                file = root / name
                file.parent.mkdir(parents=True, exist_ok=True)
                file.write_text("# synthetic test content\n")
            subprocess.run(["git", "add", "--", *names], cwd=root, check=True)
            destination = Path(directory) / "scan"
            result = scan.copy_sources(root, destination)
            self.assertEqual([r["path"] for r in result], ["infra/main.tf", "infra/runtime/data.tf"])
            self.assertEqual(len(list(destination.rglob("*tf"))), 2)

    def test_tracked_symlink_cannot_copy_outside_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / "repo"
            (root / "infra").mkdir(parents=True)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            outside = Path(directory) / "outside.tf"
            outside.write_text("# must not be scanned\n")
            (root / "infra/link.tf").symlink_to(outside)
            subprocess.run(["git", "add", "infra/link.tf"], cwd=root, check=True)
            with self.assertRaises(ValueError):
                scan.copy_sources(root, Path(directory) / "scan")

    def report(self, **summary):
        return json.dumps({"check_type": "terraform", "summary": {
            "passed": 1, "failed": 0, "skipped": 0, "parsing_errors": 0,
            "resource_count": 1, **summary}, "results": {"parsing_errors": []}})

    def test_execution_and_parsing_errors_are_not_soft_passed(self):
        for output, status in [(self.report(), 2), (self.report(parsing_errors=1), 0),
                               (self.report(), 1), (self.report(failed=1), 0)]:
            with self.subTest(status=status, output=output), self.assertRaises(ValueError):
                scan.read_report(output, status)

    def test_empty_scan_cannot_pass(self):
        with self.assertRaises(ValueError):
            scan.read_report(self.report(resource_count=0), 0)
        with self.assertRaises(ValueError):
            scan.read_report(self.report(passed=0), 0)

    def test_new_findings_preserve_failure_and_baseline_is_not_clean(self):
        self.assertEqual(scan.read_report(self.report(failed=1), 1)["summary"]["failed"], 1)
        self.assertEqual(scan.read_report(self.report(skipped=36), 0)["summary"]["skipped"], 36)

    def test_disabled_data_resources_cannot_silently_remove_rds_check(self):
        with self.assertRaises(ValueError):
            scan.require_data_checks({"results": {"passed_checks": []}})
        scan.require_data_checks({"results": {"passed_checks": [
            {"check_id": "CKV_AWS_17", "resource": "aws_db_instance.primary[0]"}]}})


if __name__ == "__main__":
    unittest.main()
