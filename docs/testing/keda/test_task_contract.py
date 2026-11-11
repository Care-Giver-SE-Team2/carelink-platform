from copy import deepcopy
import json
from pathlib import Path
import unittest

from task_contract import validate


class TaskContractTest(unittest.TestCase):
    def setUp(self):
        self.event = json.loads(Path(__file__).with_name("report-requested.v1.example.json").read_text())

    def test_fixture_and_outbox_sequence(self):
        validate(self.event)
        self.event["sequence"] = 12
        validate(self.event)

    def test_no_all_elders_fallback(self):
        for value in (None, False, 0, -1, "101"):
            event = deepcopy(self.event)
            event["payload"]["elderId"] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate(event)
        del self.event["payload"]["elderId"]
        with self.assertRaises(ValueError):
            validate(self.event)

    def test_unknown_version_and_fields(self):
        for key, value in (("schemaVersion", 2), ("schemaVersion", True), ("allElders", True)):
            event = deepcopy(self.event)
            event["payload"][key] = value
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                validate(event)

    def test_only_complete_weeks(self):
        for start, end in (("2026-10-06", "2026-10-12"), ("2026-10-05", "2026-10-12"),
                           ("2026-10-12", "2026-10-18"), ("2026-02-30", "2026-03-08")):
            event = deepcopy(self.event)
            event["payload"].update(periodStart=start, periodEnd=end)
            with self.subTest(start=start, end=end), self.assertRaises(ValueError):
                validate(event)

    def test_period_not_recomputed_from_delivery_time(self):
        self.event["occurredAt"] = "2026-11-01T09:00:00Z"
        validate(self.event)
        self.assertEqual(self.event["payload"]["periodStart"], "2026-10-05")

    def test_singapore_completion_boundary(self):
        self.event["occurredAt"] = "2026-10-11T15:59:59Z"
        with self.assertRaises(ValueError):
            validate(self.event)
        self.event["occurredAt"] = "2026-10-11T16:00:00Z"
        validate(self.event)

    def test_no_timezone_or_date_only_timestamp(self):
        for timestamp in ("2026-10-18Z", "2026-10-18T09:00:00", "2026-10-18T09:00:00+08:00"):
            self.event["occurredAt"] = timestamp
            with self.subTest(timestamp=timestamp), self.assertRaises(ValueError):
                validate(self.event)


if __name__ == "__main__":
    unittest.main()
