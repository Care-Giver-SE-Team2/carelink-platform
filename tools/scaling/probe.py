#!/usr/bin/env python3
"""Low-rate authenticated JSON GET observation, not a load generator/business assertion."""
import argparse
import csv
import json
import stat
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from common import percentile, utc_now


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file, code, message, headers, newurl):
        return None


def observe(opener, url, cookie, timeout):
    start, status, success, category = time.monotonic(), None, False, "network_or_tls_error"
    request = urllib.request.Request(url, headers={"Cookie": cookie, "Accept": "application/json"})
    try:
        with opener.open(request, timeout=timeout) as response:
            status = response.status
            # Read a bounded response; no body/cookies are written to evidence.
            body = response.read(2**20 + 1)
            if len(body) > 2**20:
                category = "response_over_1MiB"
            elif response.headers.get_content_type() != "application/json":
                category = "not_json"
            else:
                json.loads(body)
                success = 200 <= status < 300
                category = "json_2xx" if success else "http_error"
    except urllib.error.HTTPError as error:
        status, category = error.code, "redirect" if 300 <= error.code < 400 else "http_error"
        error.close()
    except (ValueError, UnicodeError):
        category = "invalid_json"
    except (OSError, urllib.error.URLError):
        pass
    return {"timestamp": utc_now(), "latency_ms": round((time.monotonic() - start) * 1000, 3),
            "status": status, "success": success, "category": category}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", required=True, help="Owner-confirmed read-only HTTPS /api/ endpoint")
    parser.add_argument("--cookie-file", required=True, type=Path, help="Private file containing Cookie header value only")
    parser.add_argument("--duration", type=int, default=1200)
    parser.add_argument("--interval", type=float, default=2)
    parser.add_argument("--timeout", type=float, default=5)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()
    url = urllib.parse.urlsplit(args.url)
    if url.scheme != "https" or not url.hostname or url.username or not url.path.startswith("/api/"):
        parser.error("Use an owner-confirmed HTTPS /api/ endpoint, without embedded credentials")
    if args.interval < 1 or args.duration < args.interval or not 0 < args.timeout <= 15:
        parser.error("interval >= 1, duration >= interval, timeout in (0,15] required")
    if stat.S_IMODE(args.cookie_file.stat().st_mode) & 0o077:
        parser.error("Cookie file must be private: chmod 600 FILE")
    cookie = args.cookie_file.read_text().strip()
    if not cookie or "\n" in cookie or "\r" in cookie:
        parser.error("Cookie header value must be one nonempty line")
    if args.output_dir.exists() and any(args.output_dir.iterdir()):
        parser.error("Use a new empty output directory")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    opener, rows = urllib.request.build_opener(NoRedirect()), []
    started, skipped, interrupted = time.monotonic(), 0, False
    due, end = started, started + args.duration
    with (args.output_dir / "requests.csv").open("w", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=["timestamp", "latency_ms", "status", "success", "category"])
        writer.writeheader()
        try:
            while time.monotonic() < end:
                time.sleep(max(0, due - time.monotonic()))
                if time.monotonic() >= end:
                    break
                row = observe(opener, args.url, cookie, args.timeout)
                rows.append(row)
                writer.writerow(row)
                output.flush()
                print(f'{row["timestamp"]} status={row["status"]} ms={row["latency_ms"]} {row["category"]}', flush=True)
                due += args.interval
                while due < min(time.monotonic(), end):
                    skipped += 1
                    due += args.interval
        except KeyboardInterrupt:
            interrupted = True
    success = [r["latency_ms"] for r in rows if r["success"]]
    summary = {"finished_at": utc_now(), "host": url.hostname, "attempted": len(rows),
               "success": len(success), "success_rate_percent": len(success) / len(rows) * 100 if rows else None,
               "successful_probe_p95_ms": percentile(success, 0.95), "skipped_slots": skipped,
               "operator_stopped": interrupted,
               "meaning": "Low-rate probe: JSON 2xx only; no business correctness assertion, load-test p95, SLA or unattempted successes. "
                          "Redirects are failures and never followed; browser/WAF responses are not bypassed."}
    (args.output_dir / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))
    return 0 if rows and len(success) == len(rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())
