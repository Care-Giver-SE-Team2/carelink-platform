import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("audit", Path(__file__).resolve().parents[1] / "audit-visit-evidence.py")
audit_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit_module)

class EvidenceAuditTest(unittest.TestCase):
    def setUp(self):
        project_tmp = Path(__file__).resolve().parents[2] / "target/main-sync/tmp"
        project_tmp.mkdir(parents=True, exist_ok=True)
        self.directory = tempfile.TemporaryDirectory(dir=project_tmp, prefix="evidence-audit-")
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.key = "11111111-1111-4111-8111-111111111111.png"
        self.data = b"synthetic-storage-test"
        self.entry = {"storageKey": self.key, "byteSize": len(self.data), "sha256": hashlib.sha256(self.data).hexdigest()}

    def test_valid_and_orphan_are_never_deleted(self):
        (self.root / self.key).write_bytes(self.data)
        result = audit_module.audit(self.root, [self.entry])
        self.assertEqual(result["issues"], [])
        result = audit_module.audit(self.root, [])
        self.assertEqual(result["issues"][0]["problem"], "orphan-candidate")
        self.assertEqual((self.root / self.key).read_bytes(), self.data)
        self.assertEqual(result["deletions"], 0)

    def test_missing_size_and_hash_are_distinguished(self):
        self.assertEqual(audit_module.audit(self.root, [self.entry])["issues"][0]["problem"], "missing")
        (self.root / self.key).write_bytes(b"x")
        self.assertEqual(audit_module.audit(self.root, [self.entry])["issues"][0]["problem"], "size-mismatch")
        (self.root / self.key).write_bytes(b"x" * len(self.data))
        self.assertEqual(audit_module.audit(self.root, [self.entry])["issues"][0]["problem"], "hash-mismatch")

    def test_path_traversal_and_duplicate_manifest_are_rejected(self):
        with self.assertRaises(ValueError):
            audit_module.audit(self.root, [{**self.entry, "storageKey": "../secret.png"}])
        with self.assertRaises(ValueError):
            audit_module.audit(self.root, [self.entry, self.entry])

if __name__ == "__main__":
    unittest.main()
