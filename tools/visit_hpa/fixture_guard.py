"""Offline A whitelist/config check. Does not login, resolve DNS, prepare data or grant approval."""
import argparse
import datetime as dt
import json
from pathlib import Path
import re

UNCONFIRMED = ("", "UNCONFIRMED", "UNKNOWN")


def require(ok, code):
    if not ok:
        raise ValueError(code)


def instant(value):
    require(type(value) is str, "utc_string_required")
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    require(parsed.tzinfo is not None and parsed.utcoffset() == dt.timedelta(), "utc_required")
    return parsed


def no_secrets(value):
    if isinstance(value, dict):
        for key, child in value.items():
            require(not re.search(r"password|cookie|secret|token|credential|username$", key, re.I),
                    "secret_or_username_field_not_allowed")
            no_secrets(child)
    elif isinstance(value, list):
        for child in value:
            no_secrets(child)


def validate(whitelist, config=None, now=None, ready=False):
    no_secrets(whitelist)
    require(type(whitelist) is dict and type(whitelist.get("schemaVersion")) is int
            and whitelist["schemaVersion"] == 1, "invalid_whitelist_schema")
    require(whitelist.get("businessOwner") == "Chenyu", "business_owner_required")
    for k in ("familyId", "preparedBy", "environment", "dataApprovalOwner", "sourceApplicationCommit"):
        require(type(whitelist.get(k)) is str and whitelist[k].strip(), "whitelist_metadata_required")
    require(whitelist.get("fixtureProfile") == "scheduled-small-v1", "unsupported_fixture_profile")
    begin, end = instant(whitelist.get("validFromUtc")), instant(whitelist.get("validUntilUtc"))
    require(begin < end, "invalid_validity_window")
    require(whitelist.get("timeZone") == "Asia/Singapore", "business_timezone_required")
    rows = whitelist.get("rows")
    require(type(rows) is list and len(rows) >= 4, "four_independent_pairs_required")
    unique = {k: set() for k in ("alias", "userId", "caregiverId", "elderId", "carePlanId", "visitId")}
    by_visit = {}
    for row in rows:
        require(type(row) is dict, "whitelist_row_required")
        require(type(row.get("alias")) is str and re.fullmatch(r"[a-zA-Z0-9_-]+", row["alias"]),
                "invalid_alias")
        for key in ("userId", "caregiverId", "elderId", "carePlanId", "visitId", "responseBytes"):
            require(type(row.get(key)) is int and row[key] > 0, "positive_integer_required")
        for key in unique:
            require(row[key] not in unique[key], "independent_pairs_required")
            unique[key].add(row[key])
        require(row.get("role") == "CAREGIVER" and row.get("canWork") is True, "working_caregiver_required")
        require(row.get("expectedStatus") == "SCHEDULED" and type(row.get("expectedVersion")) is int
                and row["expectedVersion"] == 0, "fresh_scheduled_version_zero_required")
        require(type(row.get("expectedTaskCount")) is int and row["expectedTaskCount"] == 0
                and type(row.get("serviceInstructionsCount")) is int and row["serviceInstructionsCount"] == 1,
                "scheduled_small_shape_required")
        require(instant(row.get("scheduledStartUtc")) > end, "visit_must_remain_future_through_window")
        require(instant(row.get("observedAtUtc")) <= end, "observation_after_validity")
        by_visit[row["visitId"]] = row
    retention = whitelist.get("retention")
    require(type(retention) is dict and retention.get("mode") == "KEEP_MARKED_NO_AUTO_DELETE"
            and type(retention.get("approved")) is bool
            and type(retention.get("owner")) is str and retention["owner"].strip()
            and type(retention.get("reviewByUtc")) is str, "retention_plan_required")
    instant(retention["reviewByUtc"])
    review = whitelist.get("review")
    keys = ("syntheticOnly", "assignmentVerified", "auditBudgetAccepted", "freezeAccepted", "profileAccepted")
    require(type(review) is dict and all(type(review.get(k)) is bool for k in keys), "review_flags_required")
    pending = [k for k in keys if not review[k]]
    for key in ("environment", "dataApprovalOwner"):
        if whitelist[key] in UNCONFIRMED:
            pending.append(key)
    if not retention["approved"] or retention["owner"] in UNCONFIRMED:
        pending.append("retention")
    if config is not None:
        require(type(config) is dict and type(config.get("actors")) is list, "config_actor_list_required")
        require(config.get("scenario") == "work-pack", "A_reads_only")
        require(config.get("codeBaseline") == whitelist["sourceApplicationCommit"], "application_mismatch")
        configured = set()
        for actor in config.get("actors", []):
            require(type(actor) is dict and type(actor.get("visits")) is list, "actor_visit_list_required")
            for visit in actor.get("visits", []):
                require(type(visit) is dict, "config_visit_object_required")
                row = by_visit.get(visit.get("visitId"))
                require(row is not None, "visit_outside_whitelist")
                require(type(actor.get("userId")) is int and actor["userId"] == row["userId"]
                        and actor.get("alias") == row["alias"], "actor_mismatch")
                for key in ("elderId", "expectedStatus", "expectedVersion", "expectedTaskCount"):
                    require(type(visit.get(key)) is type(row[key]) and visit[key] == row[key], "fixture_mismatch")
                configured.add(row["visitId"])
        require(configured == set(by_visit), "all_whitelisted_pairs_must_be_configured")
    now = now or dt.datetime.now(dt.timezone.utc)
    if any(not dt.timedelta() <= now - instant(row["observedAtUtc"]) <= dt.timedelta(minutes=15) for row in rows):
        pending.append("fresh_precheck_required")
    if instant(retention["reviewByUtc"]) < end:
        pending.append("retention_review_before_window_end")
    if not begin <= now <= end:
        pending.append("window_not_current")
    if ready:
        require(not pending, "offline_readiness_inputs_incomplete")
    return {"status": "STRUCTURALLY_VALID", "pairs": len(rows), "profile": whitelist["fixtureProfile"],
            "pending": pending, "offlineInputsComplete": not pending, "onlineStateVerified": False,
            "executionApproved": False}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--whitelist", required=True)
    p.add_argument("--config")
    p.add_argument("--ready", action="store_true", help="Reject incomplete/expired operator inputs; still not cloud approval")
    a = p.parse_args()
    try:
        w = json.loads(Path(a.whitelist).read_text(encoding="utf-8-sig"))
        c = json.loads(Path(a.config).read_text(encoding="utf-8-sig")) if a.config else None
        print(json.dumps(validate(w, c, ready=a.ready), indent=2))
        return 0
    except (ValueError, KeyError, TypeError, OSError):
        print(json.dumps({"status": "FAIL", "detail": "Invalid or incomplete private whitelist/config; no network used."}))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
