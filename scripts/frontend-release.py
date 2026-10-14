#!/usr/bin/env python3
"""Record and verify the static files built by one frontend CI run."""
import argparse
import hashlib
import json
from pathlib import Path
import re


def files(directory):
    site = Path(directory) / "site"
    result = {}
    for path in sorted(site.rglob("*")):
        name = path.relative_to(site).as_posix()
        if path.is_symlink() or any(part.startswith(".") for part in path.relative_to(site).parts):
            raise ValueError("Hidden files and symlinks cannot be published")
        if path.is_file():
            if not re.fullmatch(r"[A-Za-z0-9_/-]+\.[A-Za-z0-9_-]+", name):
                raise ValueError("Unexpected static file path")
            if path.suffix not in {".html", ".js", ".css", ".svg", ".png", ".jpg", ".jpeg",
                                   ".webp", ".ico", ".woff", ".woff2", ".ttf", ".json", ".txt"}:
                raise ValueError("Unexpected static file type")
            result[name] = hashlib.sha256(path.read_bytes()).hexdigest()
    if "index.html" not in result:
        raise ValueError("Missing index.html")
    return result


def verify(directory, expected_sha=None):
    record = json.loads((Path(directory) / "release.json").read_text())
    if (set(record) != {"commit", "files"} or not re.fullmatch(r"[0-9a-f]{40}", record["commit"])
            or expected_sha is not None and record["commit"] != expected_sha):
        raise ValueError("Frontend commit does not match the selected CI run")
    if record["files"] != files(directory):
        raise ValueError("Frontend files differ from the CI release manifest")
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["build", "verify"])
    parser.add_argument("--directory", required=True)
    parser.add_argument("--sha", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.sha):
        parser.error("Invalid commit SHA")
    if args.command == "build":
        (Path(args.directory) / "site/version.json").write_text(json.dumps({"commit": args.sha}) + "\n")
        record = {"commit": args.sha, "files": files(args.directory)}
        (Path(args.directory) / "release.json").write_text(json.dumps(record, indent=2) + "\n")
    verify(args.directory, args.sha)
    print("Verified static frontend " + args.sha)


if __name__ == "__main__":
    main()
