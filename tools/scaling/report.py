#!/usr/bin/env python3
"""Build an offline HTML evidence viewer from collector/probe output. No CDN or telemetry."""
import argparse
import csv
import html
import json
from datetime import datetime
from pathlib import Path

from common import percentile


def stamp(value):
    return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()


def chart(title, series, start, end, markers):
    width, height, left, top, inner_w, inner_h = 1000, 255, 65, 15, 900, 185
    numbers = [float(value) for _, points, _ in series for _, value in points if value is not None]
    ceiling = max(numbers, default=1) * 1.15 or 1
    x = lambda at: left + (at - start) / max(end - start, 1) * inner_w
    y = lambda value: top + inner_h - value / ceiling * inner_h
    svg = [f'<svg viewBox="0 0 {width} {height}" role="img" aria-label="{html.escape(title)}">']
    for fraction in (0, 0.5, 1):
        yy = y(ceiling * fraction)
        svg.append(f'<path d="M{left},{yy}h{inner_w}" stroke="#d9e2ec"/><text x="5" y="{yy + 5}">{ceiling * fraction:.1f}</text>')
    for fraction in (0, .25, .5, .75, 1):
        xx = left + fraction * inner_w
        svg.append(f'<text x="{xx}" y="225" text-anchor="middle">{(end - start) * fraction / 60:.1f} min</text>')
    for at, label in markers:
        if start <= at <= end:
            xx = x(at)
            svg.append(f'<path d="M{xx},{top}v{inner_h}" stroke="#b8c4d2" stroke-dasharray="4 4"><title>{html.escape(label)}</title></path>')
    for name, points, color in series:
        segment = []
        for at, value in points + [(end, None)]:
            if value is not None:
                segment.append(f'{x(at):.2f},{y(float(value)):.2f}')
            elif segment:
                svg.append(f'<polyline points="{" ".join(segment)}" fill="none" stroke="{color}" stroke-width="2"/>')
                if len(segment) == 1:
                    cx, cy = segment[0].split(",")
                    svg.append(f'<circle cx="{cx}" cy="{cy}" r="3" fill="{color}"/>')
                segment = []
    svg.append('</svg>')
    legend = " · ".join(f'<span style="color:{color}">{html.escape(name)}</span>' for name, _, color in series)
    return f'<section><h2>{html.escape(title)}</h2><p>{legend}</p>{"".join(svg)}</section>'


def build(cluster_dir, probe_dir=None, marker_file=None):
    samples = []
    for line in (cluster_dir / "samples.jsonl").read_text().splitlines():
        try:
            samples.append(json.loads(line))
        except json.JSONDecodeError:
            # A collector may be mid-write when a live report is requested.
            raise ValueError("Partial JSONL sample: rerun report after the collector flushes")
    if not samples:
        raise ValueError("No cluster samples")
    run_file = cluster_dir / "run.json"
    run = json.loads(run_file.read_text()) if run_file.exists() else {}
    fixture_banner = ('<p style="background:#fff0bd;padding:16px;font-weight:bold">OFFLINE FIXTURE — NOT AWS MEASUREMENTS</p>'
                      if run.get("evidence_kind") == "offline_fixture" else '')
    requests, markers = [], []
    if probe_dir:
        with (probe_dir / "requests.csv").open() as source:
            requests = list(csv.DictReader(source))
    if marker_file:
        with marker_file.open() as source:
            markers = [(stamp(r["timestamp"]), r["phase"]) for r in csv.DictReader(source)]
    times = [stamp(s["timestamp"]) for s in samples] + [stamp(r["timestamp"]) for r in requests]
    start, end = min(times), max(times)
    warnings = []
    if any(s.get("error") or s.get("metrics_error") for s in samples):
        warnings.append("Collection errors occurred. Missing observations are not zeros or successful requests.")
    if any(s.get("release_changed") for s in samples):
        warnings.append("Deployment image/revision/identity changed during the run: investigate concurrent release before comparing results.")
    if not any(s.get("hpa") for s in samples):
        warnings.append("No HPA was observed; these samples do not demonstrate autoscaling.")
    latest_hpa = next((s["hpa"] for s in reversed(samples) if s.get("hpa")), [])
    hpa_rows = []
    for hpa in latest_hpa:
        condition = "; ".join(f'{c["type"]}={c["status"]} ({c.get("reason", "")})'
                              for c in hpa.get("status", {}).get("conditions", []))
        hpa_rows.append(f'<li>{html.escape(hpa["name"])} · min {hpa["min"]} / max {hpa["max"]} · {html.escape(condition or "No conditions recorded")}</li>')
    if any(c["status"] != "True" for h in latest_hpa for c in h.get("status", {}).get("conditions", [])
           if c["type"] in ("AbleToScale", "ScalingActive")):
        warnings.append("Latest HPA conditions do not confirm active/able scaling; inspect the recorded reason.")
    cpu, memory = [], []
    for sample in samples:
        active = [p for p in sample.get("pods", []) if p["phase"] not in ("Succeeded", "Failed") and not p["terminating"]]
        known = [p for p in active if p["usage"] is not None]
        request = sum(p["requests"]["cpu_m"] for p in known)
        at = stamp(sample["timestamp"])
        cpu.append((at, sum(p["usage"]["cpu_m"] for p in known) / request * 100 if request else None))
        memory.append((at, sum(p["usage"]["memory_mib"] for p in known) if known else None))
        if len(known) != len(active):
            warnings.append("Some active Pods lack Metrics API observations; CPU/memory curves show known Pods only.")
        if any(p.get("metrics_timestamp") and abs(at - stamp(p["metrics_timestamp"])) > 90 for p in known):
            warnings.append("Some metric timestamps differ from collection time by more than 90s; do not interpret them as fresh readings.")
    series = lambda key: [(stamp(s["timestamp"]), s.get("deployment", {}).get(key)) for s in samples]
    cards = [chart("Deployment replicas", [("Desired", series("desired"), "#2563eb"), ("Ready", series("ready"), "#0f766e")], start, end, markers),
             chart("Observed CPU / requests (%) — known Pods, not HPA's exact calculation", [("CPU %", cpu, "#7c3aed")], start, end, markers),
             chart("Memory working set (MiB) — known Pods", [("MiB", memory, "#0f766e")], start, end, markers)]
    probe_summary = "No HTTP probe supplied. Import the teammate's load-test results separately."
    if requests:
        successful = [float(r["latency_ms"]) for r in requests if r["success"] == "True"]
        cards.append(chart("Low-rate probe latency (ms) — separate from workload/load-test p95", [
            ("JSON 2xx", [(stamp(r["timestamp"]), float(r["latency_ms"]) if r["success"] == "True" else None) for r in requests], "#2563eb"),
            ("Failed observation", [(stamp(r["timestamp"]), float(r["latency_ms"]) if r["success"] != "True" else None) for r in requests], "#dc2626")], start, end, markers))
        p95 = percentile(successful, .95)
        probe_summary = (f'Probe JSON-2xx: {len(successful)}/{len(requests)} ({len(successful) / len(requests) * 100:.2f}%). '
                         f'Successful probe p95: {round(p95, 2) if p95 is not None else "N/A"} ms. '
                         'Business correctness and workload p95 must come from the load-test owner.')
        if (probe_dir / "summary.json").exists():
            result = json.loads((probe_dir / "summary.json").read_text())
            probe_summary += f' Unattempted/skipped probe slots: {result.get("skipped_slots", "unknown")} (excluded from successes).'
    warning_html = "".join(f'<li>{html.escape(w)}</li>' for w in dict.fromkeys(warnings)) or '<li>No automated warnings; this is not an acceptance verdict.</li>'
    phase_html = "".join(f'<li>{datetime.fromtimestamp(at).astimezone().isoformat()} — {html.escape(label)}</li>' for at, label in markers)
    return f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>CareLink scaling evidence</title><style>
body{{font:16px/1.6 system-ui,sans-serif;background:#eef3f8;color:#172b4d;margin:0}}main{{max-width:1120px;margin:32px auto;padding:0 24px}}
section,header{{background:white;border-radius:12px;padding:20px 26px;margin:18px 0}}h1{{font-size:30px}}h2{{font-size:19px}}svg{{width:100%;height:auto}}svg text{{font:13px system-ui;fill:#526477}}
.warnings{{border-left:5px solid #d97706}}p{{color:#43556d}}code{{overflow-wrap:anywhere}}</style><main>
<header><h1>CareLink · Scaling test evidence</h1>{fixture_banner}<p>Source: recorded observations. No synthetic data unless the enclosing run is explicitly marked OFFLINE FIXTURE.</p>
<p>Start: {datetime.fromtimestamp(start).astimezone().isoformat()} · {len(samples)} cluster samples · elapsed {(end-start)/60:.1f} min.</p>
<p>{html.escape(probe_summary)}</p><p>Refresh by rerunning report.py; this file does not poll AWS or upload data.</p></header>
<section class="warnings"><h2>Interpretation and data gaps</h2><ul>{warning_html}</ul><p>CPU utilization, replica changes and latency correlation do not by themselves prove business correctness or causation. Keep original JSONL/CSV and the workload summary.</p></section>
<section><h2>Latest recorded HPA conditions</h2><ul>{"".join(hpa_rows) or '<li>No HPA status recorded.</li>'}</ul></section>
{"".join(cards)}<section><h2>Operator phase markers</h2><ul>{phase_html or '<li>No phase markers supplied. Record baseline, load start/steps, load stop and recovery.</li>'}</ul>
<p>Use the same timestamp axis to explain: load → CPU → desired/ready replicas → workload p95/error rate → cooldown. No WAF exclusion or infrastructure mutation was performed by these tools.</p></section></main></html>'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cluster-dir", required=True, type=Path)
    parser.add_argument("--probe-dir", type=Path)
    parser.add_argument("--markers", type=Path, help="CSV with timestamp (ISO8601),phase columns")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        output = build(args.cluster_dir, args.probe_dir, args.markers)
    except (OSError, ValueError, KeyError) as error:
        parser.exit(2, f"Report incomplete: {error}\n")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(output)
    print(args.output.resolve())


if __name__ == "__main__":
    main()
