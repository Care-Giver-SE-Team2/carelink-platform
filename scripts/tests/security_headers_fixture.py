#!/usr/bin/env python3
"""Serve the production build with the proposed headers, on loopback only. No AWS/proxy."""
import argparse
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
POLICY = json.loads((ROOT / "deploy/cloudfront/security-headers.json").read_text())["SecurityHeadersConfig"]


class Fixture(SimpleHTTPRequestHandler):
    def end_headers(self):
        # The proposed CloudFront policy is deliberately NOT attached to API behaviors.
        if not urlsplit(self.path).path.startswith("/api/"):
            self.send_header("Content-Security-Policy", POLICY["ContentSecurityPolicy"]["ContentSecurityPolicy"])
            self.send_header("X-Frame-Options", POLICY["FrameOptions"]["FrameOption"])
            self.send_header("Strict-Transport-Security", "max-age=86400")
            self.send_header("X-Content-Type-Options", "nosniff")
        super().end_headers()

    def send_response(self, code, message=None):
        # Match the proposed CloudFront policy: do not disclose the server product.
        self.send_response_only(code, message)

    def send_body(self, data, mime="text/html", status=200):
        self.send_response(status)
        self.send_header("Content-Type", mime)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/__csp/probe.js":
            self.send_body((Path(__file__).with_name("security_headers_probe.js")).read_bytes(), "text/javascript")
        elif path.startswith("/api/"):
            # Deliberately fake responses: this fixture can never contact staging.
            if path == "/api/__csp-preview":
                self.send_body(b"<!doctype html><p>Local preview navigation permitted</p>")
            elif path == "/api/auth/csrf":
                self.send_body(b'{}', "application/json")
            else:
                self.send_body(b'{"message":"Local fixture: not signed in"}', "application/json", 401)
        elif path.startswith("/assets/") or path.startswith("/licenses/") or path.endswith(".svg"):
            super().do_GET()
        else:
            html = (Path(self.directory) / "index.html").read_text()
            strict = "true" if "'unsafe-inline'" not in POLICY["ContentSecurityPolicy"]["ContentSecurityPolicy"] else "false"
            self.send_body(html.replace('<div id="root">',
                f'<script src="/__csp/probe.js" data-strict="{strict}"></script>'
                '<pre id="csp-result">Checking CSP…</pre><div id="root">').encode())

    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length", "0")))
        self.send_body(b'{"message":"Local fixture rejects login; no real backend"}', "application/json", 401)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=15174)
    parser.add_argument("--policy", type=Path, default=ROOT / "deploy/cloudfront/security-headers.json")
    args = parser.parse_args()
    POLICY = json.loads(args.policy.read_text())["SecurityHeadersConfig"]
    directory = ROOT / "frontend/dist"
    if not (directory / "index.html").is_file():
        raise SystemExit("Build frontend first")
    print(f"Local fixture only: http://127.0.0.1:{args.port}/", flush=True)
    ThreadingHTTPServer(("127.0.0.1", args.port), partial(Fixture, directory=str(directory))).serve_forever()
