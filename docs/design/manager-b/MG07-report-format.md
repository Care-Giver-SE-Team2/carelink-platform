# UC-MG07 report format

What a filed report holds since V19, written for whoever displays one. The family pages
(UC-FM04 detail and weekly summary) are the main reader of this note.

## What changed

| | Before V19 | Since V19 |
|---|---|---|
| Sections | Service completion, Vital signs, Observations, Incidents | Overview, Services, Service completion, Vital signs, Observations, Incidents, Ratings and spot checks — same order for every reader |
| A section | `title`, `body` | `key`, `title`, `body`, `figures[]`, `series[]` |
| Vital signs | ranges as text | the same text, plus one chartable series per metric |
| Notes on a report | corrections only | `kind`: `CORRECTION` or `FOLLOW_UP` |
| Where the numbers come from | — | one `report_basis` row per generation run; the three readers' reports of the run point to it (`report.basis_id`) |

The wording of the four original sections did not change, so text that was matched on
before still matches.

## What the family code sees today

`ReportContent.familySections()` keeps only the four original titles, and
`FamilyReportDetailResponse.Section` carries `title` and `body`. So the FM04 detail page and
the weekly summary show exactly what they showed before. `FamilyReportWorkflowIT`,
`FamilyReportDetailIT`, `FamilyReportListIT` and `FamilyWeeklySummaryIT` pass unchanged.
Nothing has to change unless the new content is wanted.

## The family version, section by section

Everything below is already filtered by `FamilyReportAssembler` for a family reader:
caregivers by name only, no staff names on incidents, no medical notes, no spot-check
findings or caregiver responses, vital signs as daily ranges. Take sections only from the
FAMILY report; another audience's figures or series must never reach a family.

| key | title | body says | figures | series |
|---|---|---|---|---|
| `overview` | Overview | plan in force and main caregiver, then the period's numbers in fixed sentences | `visits`, `fulfilment` (when anything was planned), `out-of-range`, `incidents`, `rating` (when rated) | — |
| `services` | Services | per service "2 of 3 carried out"; caregiver changes the family decided on; extra services asked for | one per service type, key from its name (`personal-care`) | — |
| `service-completion` | Service completion | unchanged | — | — |
| `vital-signs` | Vital signs | unchanged: "Systolic 128–142 mmHg" per metric | — | one per metric, one point per day |
| `observations` | Observations | unchanged | — | — |
| `incidents` | Incidents | unchanged | — | — |
| `ratings-and-spot-checks` | Ratings and spot checks | elder's confirmations and average rating; the family's periodic reviews; each spot check by its conclusion only | `rating` (when rated), `ratings`, `disputed`, `reviews`, `spot-checks` | — |

A section with nothing to report still appears and says so ("No ratings, reviews or spot
checks were recorded in this period."), with figures that are zeros and no series.

Reports filed before V19 have only the four original sections. They read back with a key
made from the title (`vital-signs`), empty `figures` and empty `series`: show their text alone.

## Shapes

```json
{
  "key": "vital-signs",
  "title": "Vital signs",
  "body": "Systolic 128–142 mmHg\nDiastolic 82–88 mmHg\nPulse 72–76 bpm\nTemperature 36.6–36.8 °C",
  "figures": [],
  "series": [
    {
      "key": "systolic",
      "label": "Systolic",
      "unit": "mmHg",
      "points": [
        { "at": "2026-09-14", "low": 128, "high": 128, "flagged": false },
        { "at": "2026-09-16", "low": 142, "high": 142, "flagged": true }
      ]
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `figure.key` | stable within its section; find a figure by it, not by its label |
| `figure.value` / `figure.outOf` | numbers without trailing zeros (3.5, not 3.50); `outOf` is null when the value stands alone |
| `figure.unit` | `"%"` for a rate, otherwise null |
| `series.key` | the metric as the caregiver's form records it: `systolic`, `diastolic`, `pulse`, `temperature` |
| `series.unit` | the unit of the metric's first reading that has one; may be null |
| `point.at` | family: a date (`2026-09-14`), one point per day. Manager versions: a date and time, one point per reading |
| `point.low` / `point.high` | the day's lowest and highest reading; equal when the day had one reading |
| `point.flagged` | a reading behind the point was marked out of range when it was entered |

Points are oldest first. Spacing them evenly in that order is enough for a trend chart; draw
a bar from `low` to `high` when they differ, and mark `flagged` points.

## Showing the new content on FM04

1. Add the titles wanted to `FAMILY_SECTION_TITLES` in `ReportContent`, for example
   `"Overview"`, `"Services"`, `"Ratings and spot checks"`. That also adds them to the weekly
   summary text, which is built from the same method; filter there if the summary should stay
   as it is.
2. Add `key`, `figures` and `series` to `FamilyReportDetailResponse.Section`, mapped from
   `ReportSection.key()`, `figures()` and `series()`. They are records of plain values
   (`ReportFigure`, `ReportSeries`, `ReportSeries.Point`), so a mapping of the same shape as the
   manager's `ReportResponses.Figure/Series/Point` is all it takes.
3. Optionally add `kind` to the family `Amendment` record, from `ReportAmendment.kind()`, to
   label follow-ups.

The manager screens show the same data: section figures as a strip above the text, each
series as a small chart (`frontend/src/routes/manager/pages/reports/ReportSparkline.tsx`,
geometry in `sparkline()` in `features/reports/presentation.ts`), which can be reused.

## Where it comes from

| Data | Table | Use case |
|---|---|---|
| plan version and weekly hours | `care_plan` (latest PUBLISHED) | MG01 |
| primary caregiver | `elder_primary_caregiver` | MG03 |
| elder's confirmations and ratings | `elder_confirmation` | EL01 |
| periodic reviews and renewal decisions | `caregiver_review` | FM09 |
| spot checks | `spot_check` | MG08 |
| caregiver changes after an absence | `roster_change` | MG04 |
| extra services | `value_added_service_request` | EL02, FM08 |
| visit and on-site hours | `visit.scheduled_end`, `checked_in_at`, `checked_out_at` | CG03, CG05 |
