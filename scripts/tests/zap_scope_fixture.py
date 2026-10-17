#!/usr/bin/env python3
"""Explicit Docker integration check on an internal network; never contacts staging.

Run separately: python3 scripts/tests/zap_scope_fixture.py
Requires the runner's pinned official ZAP image already pulled. No published ports or cloud
credentials are used. The production runner is exercised with GitHub/version
lookups mocked and its target replaced only inside this local test process.
"""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from unittest.mock import patch
import uuid

spec = importlib.util.spec_from_file_location("baseline", Path(__file__).resolve().parents[1] / "run-zap-baseline.py")
baseline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(baseline)

SERVER = r'''
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
import json, ssl
base = Path('/fixture')
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        with (base/'requests.jsonl').open('a') as output:
            output.write(json.dumps({'method': self.command, 'host': self.headers['Host'], 'path': self.path})+'\n')
        redirect = None
        if self.path == '/' and (base/'redirect-root').exists():
            redirect = 'https://outside-scope.test:8443/trap'
        elif self.path == '/assets/redirect-api.js':
            redirect = '/api/auth/logout'
        elif self.path == '/assets/redirect-other.js':
            redirect = 'https://outside-scope.test:8443/trap'
        self.send_response(302 if redirect else 200)
        if redirect:
            self.send_header('Location', redirect)
        self.send_header('Content-Type', 'text/html' if self.path == '/' else 'text/plain')
        self.end_headers()
        if self.path == '/':
            self.wfile.write(''' + repr('''<html><body>
<a href="/assets/good.js">asset</a><a href="/api/auth/logout">api</a>
<a href="/assets/../api/traversal">dot</a><a href="/assets/%2e%2e/api/encoded">encoded</a>
<a href="/assets/good.js?delete=1">query</a><a href="/?delete=1">root query</a>
<a href="https://outside-scope.test:8443/trap">external</a>
<a href="/assets/redirect-api.js">api redirect</a><a href="/assets/redirect-other.js">external redirect</a>
<form action="/api/form" method="POST"><input name="x" value="y"><input type="submit"></form>
</body></html>''') + r'''.encode())
        else:
            self.wfile.write(b'ok')
    do_POST = do_GET
    def log_message(self, *args): pass
server = HTTPServer(('0.0.0.0',8443),Handler)
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain('/fixture/cert.pem','/fixture/key.pem')
server.socket = context.wrap_socket(server.socket,server_side=True)
(base/'ready').touch()
server.serve_forever()
'''


def check():
    real_run = subprocess.run
    image = json.loads(subprocess.check_output(["docker", "image", "inspect", baseline.IMAGE], text=True))[0]["Id"]
    name = "carelink-zap-fixture-" + uuid.uuid4().hex[:10]
    network, server = name + "-net", name + "-server"
    target = "https://carelink-zap-fixture:8443"
    sha = "a" * 40
    metadata = {"status": "completed", "conclusion": "success", "head_branch": "main",
                "event": "workflow_run", "path": ".github/workflows/deploy.yml", "head_sha": sha,
                "repository": {"id": baseline.REPOSITORY_ID}, "head_repository": {"id": baseline.REPOSITORY_ID},
                "run_attempt": 1, "html_url": "https://github.com/local-fixture/123"}
    jobs = {"jobs": [{"name": n, "conclusion": "success"} for n in
                     ("release", "deploy", "frontend / deploy", "acceptance")]}
    with tempfile.TemporaryDirectory(prefix="carelink-zap-scope-") as directory:
        root = Path(directory)
        (root/"server.py").write_text(SERVER)
        real_run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                  "-subj", "/CN=carelink-zap-fixture", "-keyout", str(root/"key.pem"),
                  "-out", str(root/"cert.pem")], check=True, capture_output=True)
        real_run(["docker", "network", "create", "--internal", network], check=True, capture_output=True)
        try:
            real_run(["docker", "run", "-d", "--name", server, "--network", network,
                      "--network-alias", "carelink-zap-fixture", "--network-alias", "outside-scope.test",
                      "--user", "0", "--entrypoint", "python3", "-v", f"{root}:/fixture:rw",
                      image, "/fixture/server.py"], check=True, capture_output=True)
            for _ in range(30):
                if (root/"ready").exists():
                    break
                time.sleep(1)
            else:
                raise RuntimeError("Local HTTPS fixture did not start")

            def docker(command, **kwargs):
                if command[:2] == ["docker", "pull"]:
                    return subprocess.CompletedProcess(command, 0)
                if command[:2] == ["docker", "run"]:
                    command = command[:2] + ["--network", network] + command[2:]
                return real_run(command, **kwargs)

            for mode in ("links-and-redirects", "seed-redirect"):
                (root/"requests.jsonl").write_text('')
                if mode == "seed-redirect":
                    (root/"redirect-root").touch()
                output = root/mode
                error = None
                with patch.dict(baseline.os.environ, {"CARELINK_ZAP_ENABLED": "true"}):
                    with patch.object(baseline, "TARGET", target), patch.object(baseline, "public_commit", return_value=sha):
                        with patch.object(baseline, "gh", side_effect=[metadata, jobs]):
                            with patch.object(baseline.subprocess, "run", side_effect=docker):
                                with patch.object(sys, "argv", ["fixture", "--run-id", "123", "--output", str(output)]):
                                    try:
                                        baseline.main()
                                    except ValueError as caught:
                                        error = str(caught)
                requests = [json.loads(line) for line in (root/"requests.jsonl").read_text().splitlines()]
                evidence = json.loads((output/"release-evidence.json").read_text())
                allowed = {"/", "/robots.txt", "/sitemap.xml", "/assets/good.js",
                           "/assets/redirect-api.js", "/assets/redirect-other.js"}
                assert requests and all(r["method"] == "GET" and r["host"] == "carelink-zap-fixture:8443"
                                        and r["path"] in allowed for r in requests), requests
                if mode == "links-and-redirects":
                    assert {"/assets/good.js", "/assets/redirect-api.js", "/assets/redirect-other.js"}.issubset(
                        {r["path"] for r in requests}), requests
                    assert evidence["completed"] and evidence["zap_exit_code"] in (0, 1, 2), evidence
                else:
                    assert {r["path"] for r in requests} == {"/"}, requests
                    assert error and not evidence["accepted"] and evidence["zap_exit_code"] != 0, evidence
                print(json.dumps({"case": mode, "requests": requests, "zap_exit_code": evidence["zap_exit_code"],
                                  "scanner_image_id": image, "scope_check": "PASS"}), flush=True)
        finally:
            real_run(["docker", "rm", "-f", server], capture_output=True, check=False)
            real_run(["docker", "network", "rm", network], capture_output=True, check=False)


if __name__ == "__main__":
    check()
