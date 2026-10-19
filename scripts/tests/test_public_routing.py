"""Check the public route boundaries against rendered Ingress and the edge function."""
import json
from pathlib import Path
import re
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]


class PublicRoutingTest(unittest.TestCase):
    def test_shared_api_paths_reach_the_owning_service(self):
        rendered = subprocess.check_output([
            "helm", "template", "carelink-ingress", "charts/carelink-ingress", "--namespace", "staging",
            "--set-json", 'privateSubnetIds=["subnet-00000000000000001","subnet-00000000000000002"]',
            "--set", "cloudFrontPrefixListId=pl-00000000000000001"], cwd=ROOT, text=True)
        entries = re.findall(r'- path: "([^"\n]+)"\s+pathType: ImplementationSpecific\s+backend:\s+service:\s+name: (\w+)', rendered)
        self.assertEqual(len(entries), 16)
        cases = {
            "/api/auth/login": "core", "/api/caregivers/me/absences": "core",
            "/api/caregivers/42/spot-checks": "core", "/api/family/elders": "core",
            "/api/caregivers/me": "visit", "/api/caregivers/me/schedule": "visit",
            "/api/caregivers/me/incidents/7": "visit", "/api/caregivers/42": "visit",
            "/api/caregivers/42/credentials": "visit", "/api/visits/7": "visit",
            "/api/elders/me/visits": "visit", "/api/reports/7": "report",
            "/api/elders/7/weekly-summary": "report", "/api/family/caregiver-reviews": "report",
            "/api/family/value-added-services": "report", "/api/elders/me/value-added-service": "report",
            "/api/value-added-service-requests": "report", "/api/notifications": "notification",
            "/api/provider-applications/417/approve": "notification", "/api/providers": "notification",
            "/api/providers/509/suspend": "notification",
            "/api/open/v1/applications": "notification", "/api/open/v1/portal/sessions": "notification",
            "/api/open/v1/oauth2/token": "notification", "/api/open/v1/jobs": "report",
            "/api/open/v1/jobs/JOB-24081/accept": "report", "/api/open/v1/jobsite": "notification",
            "/actuator/health": None, "/internal/v1/users": None,
        }
        for path, expected in cases.items():
            with self.subTest(path=path):
                actual = next((service for pattern, service in entries if re.search(pattern[1:], path)), None)
                self.assertEqual(actual, expected)

    def test_spa_refresh_does_not_rewrite_api_or_missing_assets(self):
        function = (ROOT / "deploy/cloudfront/spa.js").read_text()
        paths = ["/family/home", "/caregiver/visits/42", "/api/auth/me", "/api",
                 "/assets/missing.js", "/internal/v1/users", "/actuator/health", "/.env"]
        code = function + "\nconsole.log(JSON.stringify(" + json.dumps(paths) + ".map(uri => handler({request:{uri}}))));"
        actual = json.loads(subprocess.check_output(["node", "-e", code], text=True))
        self.assertEqual([item.get("uri") for item in actual[:5]],
                         ["/index.html", "/index.html", "/api/auth/me", "/api", "/assets/missing.js"])
        self.assertEqual([item.get("statusCode") for item in actual[5:]], [404, 404, 404])


if __name__ == "__main__":
    unittest.main()
