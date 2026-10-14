#!/usr/bin/env python3
"""Verify the published commit and authenticated reads across the four services."""
import http.cookiejar
import json
import os
import re
import urllib.parse
import urllib.request


def check(base, sha, password):
    if not re.fullmatch(r"https://[a-z0-9]+\.cloudfront\.net", base) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise ValueError("Unexpected public deployment target")
    if not password:
        raise ValueError("Set CARELINK_SMOKE_PASSWORD")
    with urllib.request.urlopen(base + "/version.json", timeout=30) as response:
        if json.load(response).get("commit") != sha:
            raise ValueError("Public frontend commit differs from the successful CI release")
    paths = {
        "demo-alice": ["/api/auth/me", "/api/elders", "/api/visits/roster", "/api/reports", "/api/notifications/me"],
        "demo-fam-wei": ["/api/auth/me", "/api/visits", "/api/reports"],
        "demo-fam-cg-ming": ["/api/auth/me", "/api/caregivers/me/schedule"],
    }
    for username, endpoints in paths.items():
        cookies = http.cookiejar.CookieJar()
        client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))
        def csrf():
            with client.open(base + "/api/auth/csrf", timeout=30):
                pass
            return urllib.parse.unquote(next(c.value for c in cookies if c.name == "XSRF-TOKEN"))
        request = urllib.request.Request(base + "/api/auth/login",
            data=json.dumps({"username": username, "password": password}).encode(),
            headers={"Content-Type": "application/json", "X-XSRF-TOKEN": csrf()})
        with client.open(request, timeout=30) as response:
            if json.load(response).get("username") != username:
                raise ValueError("Public login did not return the expected demo account")
        if not next(c for c in cookies if c.name == "SESSION").secure:
            raise ValueError("Public session cookie must be Secure")
        try:
            for path in endpoints:
                with client.open(base + path, timeout=30) as response:
                    if response.status != 200 or "json" not in response.headers.get("Content-Type", ""):
                        raise ValueError("Public service read failed")
                    json.load(response)
        finally:
            client.open(urllib.request.Request(base + "/api/auth/logout", data=b"",
                        headers={"X-XSRF-TOKEN": csrf()}), timeout=30).close()
        print(username + ": public login and service reads passed")
    print("Public frontend and backend acceptance passed for " + sha)


if __name__ == "__main__":
    check(os.environ["CARELINK_PUBLIC_URL"].rstrip("/"), os.environ["RELEASE_SHA"],
          os.environ.get("CARELINK_SMOKE_PASSWORD", ""))
