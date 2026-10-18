#!/usr/bin/env python3
"""Write scoped administrator-review policies locally; never calls AWS or grants permissions."""
import argparse
import json
import os
from pathlib import Path
import re


def policies(account):
    if not re.fullmatch(r"\d{12}", account):
        raise ValueError("Expected a 12-digit account ID")
    region = "ap-southeast-1"
    queue = f"arn:aws:sqs:{region}:{account}:carelink-staging-report-worker"
    topic = f"arn:aws:sns:{region}:{account}:carelink-staging-events"
    role = f"arn:aws:iam::{account}:role/CareLinkRuntime-staging-"
    policy = f"arn:aws:iam::{account}:policy/"
    roles = [role + "report-worker", role + "keda"]
    regional = {"StringEquals": {"aws:RequestedRegion": region}}
    tags = {"aws:RequestTag/Project": "CareLink", "aws:RequestTag/Component": "Runtime", "aws:RequestTag/ManagedBy": "Terraform"}

    def allow(actions, resources, condition=None):
        entry = {"Effect": "Allow", "Action": actions, "Resource": resources}
        if condition:
            entry["Condition"] = condition
        return entry

    def document(statements):
        return {"Version": "2012-10-17", "Statement": statements}

    result = {
        "CareLinkReportWorkerBoundary": document([allow(
            ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:ChangeMessageVisibility"], queue, regional)]),
        "CareLinkKedaQueueReadBoundary": document([allow("sqs:GetQueueAttributes", queue, regional)]),
    }
    state_bucket = f"arn:aws:s3:::carelink-tfstate-{account}-{region}"
    state = state_bucket + "/carelink/report-worker/terraform.tfstate"
    statements = [
        allow(["sqs:CreateQueue", "sqs:TagQueue"], [queue, queue + "-dlq"],
              {"StringEquals": {**tags, "aws:RequestedRegion": region}}),
        allow(["sqs:GetQueueAttributes", "sqs:ListQueueTags", "sqs:SetQueueAttributes", "sqs:TagQueue", "sqs:UntagQueue", "sqs:DeleteQueue"],
              [queue, queue + "-dlq"], regional),
        allow("sns:Subscribe", topic, {"StringEquals": {"aws:RequestedRegion": region, "sns:Protocol": "sqs", "sns:Endpoint": queue}}),
        allow("sns:Publish", topic, regional),
        allow(["iam:GetRole", "iam:ListRolePolicies", "iam:GetRolePolicy", "iam:ListAttachedRolePolicies", "iam:ListRoleTags",
               "iam:ListInstanceProfilesForRole", "iam:PutRolePolicy", "iam:DeleteRolePolicy", "iam:TagRole", "iam:UntagRole", "iam:DeleteRole"], roles),
        allow("iam:PassRole", roles, {"StringEquals": {"iam:PassedToService": "pods.eks.amazonaws.com"}}),
        allow(["iam:GetPolicy", "iam:GetPolicyVersion"], [policy + "CareLinkReportWorkerBoundary", policy + "CareLinkKedaQueueReadBoundary"]),
        allow(["s3:GetObject", "s3:PutObject"], [state, state + ".tflock"]),
        allow("s3:DeleteObject", state + ".tflock"),
        allow("s3:ListBucket", state_bucket, {"StringLike": {"s3:prefix": ["carelink/report-worker/*", "carelink/report-worker/"]}}),
    ]
    for target, boundary in zip(roles, result):
        statements.append(allow("iam:CreateRole", target, {"StringEquals": {**tags, "iam:PermissionsBoundary": policy + boundary}}))
    result["CareLinkKedaIntegrationOperator"] = document(statements)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--account-id", required=True)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        docs = policies(args.account_id)
    except ValueError as error:
        parser.error(str(error))
    args.output.mkdir(parents=True, exist_ok=True, mode=0o700)
    for name, doc in docs.items():
        compact = json.dumps(doc, separators=(",", ":"))
        if len(compact) > 6144:
            raise SystemExit(f"{name} exceeds the managed-policy size limit")
        with os.fdopen(os.open(args.output / f"{name}.json", os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w") as f:
            json.dump(doc, f, indent=2)
            f.write("\n")
        print(f"Prepared {name}: {len(compact)} non-whitespace characters; no IAM writes.")


if __name__ == "__main__":
    main()
