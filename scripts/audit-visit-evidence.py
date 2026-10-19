"""Read-only reconciliation of a quiescent DB manifest with private Visit image files.

Manifest is a JSON array of storageKey, byteSize, sha256 from visit_evidence_file.
Never deletes or fixes files. Orphan candidates need a fresh DB snapshot and human review.
Run with -B; redirect output only to a protected project-local report.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

KEY = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(png|jpg)")

def audit(root, entries):
    root = Path(root).absolute()
    if not root.is_dir() or root.resolve() != root:
        raise ValueError("Use an existing direct private directory, not a link/junction.")
    issues, expected = [], set()
    for entry in entries:
        key = entry["storageKey"]
        if not isinstance(key, str) or not KEY.fullmatch(key) or key in expected:
            raise ValueError("Invalid or duplicate manifest storage key.")
        expected.add(key)
        path = root / key
        size = entry["byteSize"]
        digest = entry["sha256"]
        if not isinstance(size, int) or not 0 < size <= 5242880 or not re.fullmatch(r"[0-9a-f]{64}", digest):
            raise ValueError("Invalid manifest metadata.")
        if path.is_symlink() or path.resolve() != path:
            issues.append({"key": key, "problem": "unsafe-link"})
        elif not path.is_file():
            issues.append({"key": key, "problem": "missing"})
        elif path.stat().st_size != size:
            issues.append({"key": key, "problem": "size-mismatch"})
        elif hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            issues.append({"key": key, "problem": "hash-mismatch"})
    for path in root.iterdir():
        if path.name not in expected:
            issues.append({"key": path.name, "problem": "orphan-candidate" if KEY.fullmatch(path.name) else "unrecognized-entry"})
    return {"referencedFiles": len(expected), "issues": issues, "deletions": 0}

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True)
    parser.add_argument("--manifest", required=True)
    options = parser.parse_args()
    print(json.dumps(audit(options.root, json.loads(Path(options.manifest).read_text(encoding="utf-8-sig"))), indent=2))
