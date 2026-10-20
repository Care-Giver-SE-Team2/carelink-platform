"""Render the real CD entry point offline; protect other services/environments."""
import os
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]


class VisitHpaDeploymentTest(unittest.TestCase):
    def render(self, service, environment):
        env = dict(os.environ, DRY_RUN="1", IMAGE_REGISTRY="registry.invalid",
                   IMAGE_TAG="offline-hpa-check", RELEASE_MANIFEST="", PUBLIC_HTTPS="false")
        result = subprocess.run(["bash", "scripts/deploy.sh", service, environment],
                                cwd=ROOT, env=env, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout

    def test_staging_visit_hpa_owns_replicas_and_keeps_cpu_denominator(self):
        rendered = self.render("visit", "staging")
        self.assertIn("kind: HorizontalPodAutoscaler\n", rendered)
        self.assertIn("minReplicas: 2\n", rendered)
        self.assertIn("maxReplicas: 3\n", rendered)
        self.assertIn("averageUtilization: 70\n", rendered)
        self.assertIn("stabilizationWindowSeconds: 300\n", rendered)
        deployment = next(part for part in rendered.split("---") if "kind: Deployment\n" in part)
        self.assertNotIn("\n  replicas:", deployment)
        self.assertIn("cpu: 100m\n", deployment)
        self.assertIn("cpu: 400m\n", deployment)
        self.assertIn("SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE", deployment)
        self.assertIn('value: "3"', deployment)
        self.assertIn("failureThreshold: 60\n", deployment)

    def test_other_staging_apis_and_demo_remain_fixed(self):
        for service, environment in (("core", "staging"), ("report", "staging"),
                                     ("notification", "staging"), ("visit", "demo")):
            with self.subTest(service=service, environment=environment):
                rendered = self.render(service, environment)
                self.assertNotIn("kind: HorizontalPodAutoscaler\n", rendered)
                self.assertIn("\n  replicas: 2\n", rendered)
                self.assertNotIn("SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE", rendered)
                self.assertNotIn("cpu: 400m\n", rendered)


if __name__ == "__main__":
    unittest.main()
