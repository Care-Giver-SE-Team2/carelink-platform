#!/usr/bin/env python3
"""Offline v1 task contract check only. No AWS calls or message delivery."""
import argparse
from datetime import date, datetime, timedelta
import json
from pathlib import Path
import re
from uuid import UUID
from zoneinfo import ZoneInfo


def validate(event):
    required = {"id", "type", "source", "occurredAt", "payload"}
    if not isinstance(event, dict) or not required <= event.keys() or event.keys() - required - {"sequence"}:
        raise ValueError("Expected the v1 Envelope fields and optional sequence")
    if not isinstance(event["id"], str):
        raise ValueError("id must be a UUID string")
    UUID(event["id"])
    if event["type"] != "ReportRequested" or event["source"] != "report":
        raise ValueError("Expected ReportRequested from report")
    if "sequence" in event and (type(event["sequence"]) is not int or event["sequence"] < 1):
        raise ValueError("sequence must be a positive integer when present")
    timestamp = event["occurredAt"]
    if not isinstance(timestamp, str) or not re.fullmatch(
            r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z", timestamp):
        raise ValueError("occurredAt must be a UTC ISO timestamp ending in Z")
    occurred = datetime.fromisoformat(timestamp[:-1] + "+00:00")
    payload = event["payload"]
    keys = {"schemaVersion", "elderId", "periodStart", "periodEnd", "runId"}
    if not isinstance(payload, dict) or payload.keys() != keys:
        raise ValueError("Expected exactly the v1 payload fields; no all-elder fallback")
    if type(payload["schemaVersion"]) is not int or payload["schemaVersion"] != 1:
        raise ValueError("Unsupported schemaVersion")
    if type(payload["elderId"]) is not int or payload["elderId"] <= 0:
        raise ValueError("elderId must be a positive integer")
    for key in ("periodStart", "periodEnd"):
        if not isinstance(payload[key], str) or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", payload[key]):
            raise ValueError("Period dates must use YYYY-MM-DD")
    start, end = date.fromisoformat(payload["periodStart"]), date.fromisoformat(payload["periodEnd"])
    if start.weekday() != 0 or end != start + timedelta(days=6):
        raise ValueError("Period must be one Monday-Sunday week")
    if end >= occurred.astimezone(ZoneInfo("Asia/Singapore")).date():
        raise ValueError("Reporting week must be complete at occurredAt in Singapore")
    if not isinstance(payload["runId"], str) or not re.fullmatch(r"[a-z0-9][a-z0-9-]{0,63}", payload["runId"]):
        raise ValueError("runId must be 1-64 lowercase letters, digits or hyphens")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("message", type=Path)
    args = parser.parse_args()
    try:
        validate(json.loads(args.message.read_text()))
    except (OSError, ValueError, TypeError) as error:
        parser.exit(1, f"Invalid contract: {error}\n")
    print("V1 contract valid; no message sent. Java/cloud behavior is not verified by this check.")
