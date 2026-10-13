#!/usr/bin/env python3
"""Upload verified static assets before index.html, then refresh CloudFront."""
import argparse
import json
import mimetypes
import os
from pathlib import Path
import re
import subprocess

from importlib.util import module_from_spec, spec_from_file_location

spec = spec_from_file_location("frontend_release", Path(__file__).with_name("frontend-release.py"))
release = module_from_spec(spec)
spec.loader.exec_module(release)


def aws(*args):
    return subprocess.check_output(["aws", "--region", "ap-southeast-1", *args], text=True)


def publish(directory, bucket, distribution):
    record = release.verify(directory)
    account = os.environ["AWS_ACCOUNT_ID"]
    if (not re.fullmatch(r"\d{12}", account) or bucket != f"carelink-frontend-{account}-staging"
            or not re.fullmatch(r"[A-Z0-9]+", distribution)):
        raise ValueError("Unexpected frontend deployment target")
    identity = json.loads(aws("sts", "get-caller-identity"))
    if identity["Account"] != account:
        raise ValueError("Wrong AWS account")
    config = json.loads(aws("cloudfront", "get-distribution", "--id", distribution))["Distribution"]
    expected_origin = bucket + ".s3.ap-southeast-1.amazonaws.com"
    if (config["DistributionConfig"]["DefaultCacheBehavior"]["TargetOriginId"] != "frontend"
            or not any(o["Id"] == "frontend" and o["DomainName"] == expected_origin
                       for o in config["DistributionConfig"]["Origins"]["Items"])):
        raise ValueError("CloudFront is not connected to the expected frontend bucket")
    # Keep old hashed assets, so open browser tabs and rollback can still load them.
    for name in sorted(record["files"], key=lambda name: name == "index.html"):
        cache = "public,max-age=31536000,immutable" if name.startswith("assets/") else "no-cache,max-age=0"
        content_type = mimetypes.guess_type(name)[0] or "application/octet-stream"
        aws("s3", "cp", str(Path(directory) / "site" / name), f"s3://{bucket}/{name}",
            "--content-type", content_type, "--cache-control", cache, "--only-show-errors")
    invalidation = json.loads(aws("cloudfront", "create-invalidation", "--distribution-id", distribution,
                                  "--paths", "/*"))["Invalidation"]["Id"]
    aws("cloudfront", "wait", "invalidation-completed", "--distribution-id", distribution,
        "--id", invalidation)
    print("Published frontend " + record["commit"] + " at https://" + config["DomainName"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory")
    args = parser.parse_args()
    publish(args.directory, os.environ["FRONTEND_BUCKET"], os.environ["CLOUDFRONT_DISTRIBUTION_ID"])
