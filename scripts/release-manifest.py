#!/usr/bin/env python3
"""Assemble CI image records into a complete release, or resolve it for deploy.sh."""
import argparse
import json
from pathlib import Path
import re
import sys

SERVICES = ("core", "visit", "report", "notification")


def validate_record(record, registry):
    if not isinstance(record, dict) or set(record) != {"service", "tag", "image", "digest"}:
        raise ValueError("Image record must contain service, tag, image and digest")
    service = record["service"]
    if service not in SERVICES or record["image"] != f"{registry}/carelink/{service}":
        raise ValueError("Image record service/repository does not match the selected registry")
    if not isinstance(record["tag"], str) or not re.fullmatch(r"[0-9a-f]{40}", record["tag"]):
        raise ValueError("Image tag must be the full Git commit SHA")
    if not isinstance(record["digest"], str) or not re.fullmatch(r"sha256:[0-9a-f]{64}", record["digest"]):
        raise ValueError("Image record requires a SHA-256 digest")
    return service


def read_manifest(path, registry):
    document = json.loads(Path(path).read_text())
    if (not isinstance(document, dict) or set(document) != {"schema_version", "services"}
            or document["schema_version"] != 1 or not isinstance(document["services"], dict)
            or set(document["services"]) != set(SERVICES)):
        raise ValueError("Release manifest must contain schema_version 1 and all four services")
    for service, record in document["services"].items():
        if validate_record(record, registry) != service:
            raise ValueError("Release service key does not match its image record")
    return document["services"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--registry", required=True)
    commands = parser.add_subparsers(dest="command", required=True)
    build = commands.add_parser("build", help="Merge new CI image records over a complete previous release")
    build.add_argument("--base")
    build.add_argument("--record", action="append", required=True)
    build.add_argument("--output", required=True)
    resolve = commands.add_parser("resolve", help="Validate the whole release before returning deployment rows")
    resolve.add_argument("manifest")
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9]{12}\.dkr\.ecr\.ap-southeast-1\.amazonaws\.com", args.registry):
        raise ValueError("Select an ECR registry in ap-southeast-1")
    if args.command == "resolve":
        services = read_manifest(args.manifest, args.registry)
        for service in SERVICES:
            record = services[service]
            print(f"{service}\t{record['tag']}\t{record['digest']}")
        return
    services = read_manifest(args.base, args.registry) if args.base else {}
    updated = set()
    for path in args.record:
        record = json.loads(Path(path).read_text())
        service = validate_record(record, args.registry)
        if service in updated:
            raise ValueError(f"Multiple new records for {service}; choose one publication")
        updated.add(service)
        services[service] = record
    if set(services) != set(SERVICES):
        raise ValueError("First release needs four image records; incremental releases need --base")
    document = {"schema_version": 1, "services": {name: services[name] for name in SERVICES}}
    # Keep previous release files intact for promotion or an explicit image rollback.
    with Path(args.output).open("x") as output:
        output.write(json.dumps(document, indent=2) + "\n")
    print(f"Wrote complete release to {args.output}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError) as error:
        print(f"Release manifest failed: {error}", file=sys.stderr)
        sys.exit(1)
