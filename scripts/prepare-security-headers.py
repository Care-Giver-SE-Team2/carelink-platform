#!/usr/bin/env python3
"""Prepare an offline CloudFront update from get-distribution output; never call AWS."""
import argparse
from copy import deepcopy
import json
from pathlib import Path
import re

DOMAIN = "dzfs8sv334fwf.cloudfront.net"
BUCKET = "carelink-frontend-733653166180-staging.s3.ap-southeast-1.amazonaws.com"


def prepare(export, policy_id):
    if not re.fullmatch(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", policy_id):
        raise ValueError("Supply the reviewed response headers policy UUID")
    distribution = export["Distribution"]
    original = distribution["DistributionConfig"]
    if distribution["DomainName"] != DOMAIN or not export.get("ETag"):
        raise ValueError("Wrong distribution or missing concurrency ETag")
    origins = original["Origins"]["Items"]
    if not any(o["Id"] == "frontend" and o["DomainName"] == BUCKET for o in origins):
        raise ValueError("Unexpected frontend S3 origin")
    config = deepcopy(original)
    default = config["DefaultCacheBehavior"]
    assets = [b for b in config.get("CacheBehaviors", {}).get("Items", [])
              if b["PathPattern"] == "/assets/*"]
    if default["TargetOriginId"] != "frontend" or len(assets) != 1:
        raise ValueError("Expected the existing frontend default and assets behaviors")
    for behavior in [default, assets[0]]:
        if (behavior["TargetOriginId"] != "frontend"
                or behavior["ViewerProtocolPolicy"] not in ("https-only", "redirect-to-https")
                or behavior.get("ResponseHeadersPolicyId") not in (None, policy_id)):
            raise ValueError("Refuse to replace an existing policy or alter a different origin")
        behavior["ResponseHeadersPolicyId"] = policy_id
    return {"Id": distribution["Id"], "IfMatch": export["ETag"], "DistributionConfig": config}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("distribution_export", type=Path)
    parser.add_argument("policy_id")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    update = prepare(json.loads(args.distribution_export.read_text()), args.policy_id)
    with args.output.open("x") as output:
        json.dump(update, output, indent=2)
        output.write("\n")
    print("Prepared only; no AWS calls. Review the diff before any separately authorized update.")
