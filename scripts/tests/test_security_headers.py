"""Guard the offline header update against changing routing, API behavior or identity."""
from copy import deepcopy
from importlib.util import module_from_spec, spec_from_file_location
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = spec_from_file_location("headers", ROOT / "scripts/prepare-security-headers.py")
headers = module_from_spec(spec)
spec.loader.exec_module(headers)
POLICY_ID = "12345678-1234-1234-1234-123456789012"
PREVIOUS_POLICY_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"


class SecurityHeadersTest(unittest.TestCase):
    def setUp(self):
        self.export = {"ETag": "current-version", "Distribution": {
            "Id": "E123", "DomainName": headers.DOMAIN, "DistributionConfig": {
                "WebACLId": "existing-waf", "Aliases": {"Quantity": 0},
                "Origins": {"Quantity": 2, "Items": [
                    {"Id": "frontend", "DomainName": headers.BUCKET},
                    {"Id": "api", "DomainName": "private-alb"}]},
                "DefaultCacheBehavior": {"TargetOriginId": "frontend",
                    "ViewerProtocolPolicy": "redirect-to-https", "FunctionAssociations": {"Quantity": 1}},
                "CacheBehaviors": {"Quantity": 3, "Items": [
                    {"PathPattern": "/api", "TargetOriginId": "api", "ResponseHeadersPolicyId": "api-policy"},
                    {"PathPattern": "/api/*", "TargetOriginId": "api", "AllowedMethods": ["GET", "POST"]},
                    {"PathPattern": "/assets/*", "TargetOriginId": "frontend",
                     "ViewerProtocolPolicy": "redirect-to-https", "CachePolicyId": "immutable"}]}}}}

    def test_only_two_static_policy_references_change_and_export_is_not_mutated(self):
        original = deepcopy(self.export)
        update = headers.prepare(self.export, POLICY_ID)
        self.assertEqual(self.export, original)
        self.assertEqual((update["Id"], update["IfMatch"]), ("E123", "current-version"))
        updated = update["DistributionConfig"]
        for behavior in [updated["DefaultCacheBehavior"], updated["CacheBehaviors"]["Items"][2]]:
            self.assertEqual(behavior.pop("ResponseHeadersPolicyId"), POLICY_ID)
        self.assertEqual(updated, original["Distribution"]["DistributionConfig"])

    def test_refuses_wrong_site_origin_missing_etag_or_policy_replacement(self):
        for failure in ("domain", "origin", "etag", "default-policy", "assets-policy", "http"):
            with self.subTest(failure=failure):
                export = deepcopy(self.export)
                config = export["Distribution"]["DistributionConfig"]
                if failure == "domain": export["Distribution"]["DomainName"] = "other.cloudfront.net"
                if failure == "origin": config["Origins"]["Items"][0]["DomainName"] = "other.s3.amazonaws.com"
                if failure == "etag": del export["ETag"]
                if failure == "default-policy": config["DefaultCacheBehavior"]["ResponseHeadersPolicyId"] = "other"
                if failure == "assets-policy": config["CacheBehaviors"]["Items"][2]["ResponseHeadersPolicyId"] = "other"
                if failure == "http": config["DefaultCacheBehavior"]["ViewerProtocolPolicy"] = "allow-all"
                with self.assertRaises(ValueError): headers.prepare(export, POLICY_ID)

    def test_refuses_invalid_policy_and_missing_or_misrouted_assets(self):
        with self.assertRaises(ValueError): headers.prepare(self.export, "not-a-policy-id")
        for mode in ("missing", "misrouted", "duplicate"):
            with self.subTest(mode=mode):
                export = deepcopy(self.export)
                items = export["Distribution"]["DistributionConfig"]["CacheBehaviors"]["Items"]
                if mode == "missing": items.pop()
                if mode == "misrouted": items[-1]["TargetOriginId"] = "api"
                if mode == "duplicate": items.append(deepcopy(items[-1]))
                with self.assertRaises(ValueError): headers.prepare(export, POLICY_ID)

    def test_reapplying_same_policy_is_idempotent(self):
        update = headers.prepare(self.export, POLICY_ID)
        self.export["Distribution"]["DistributionConfig"] = update["DistributionConfig"]
        self.assertEqual(headers.prepare(self.export, POLICY_ID), update)

    def test_explicit_transition_preserves_everything_except_two_reviewed_references(self):
        config = self.export["Distribution"]["DistributionConfig"]
        for behavior in [config["DefaultCacheBehavior"], config["CacheBehaviors"]["Items"][2]]:
            behavior["ResponseHeadersPolicyId"] = PREVIOUS_POLICY_ID
        original = deepcopy(self.export)
        update = headers.prepare(self.export, POLICY_ID, PREVIOUS_POLICY_ID)
        self.assertEqual(self.export, original)
        self.assertEqual(update["IfMatch"], "current-version")
        for behavior in [update["DistributionConfig"]["DefaultCacheBehavior"],
                         update["DistributionConfig"]["CacheBehaviors"]["Items"][2]]:
            self.assertEqual(behavior["ResponseHeadersPolicyId"], POLICY_ID)
            behavior["ResponseHeadersPolicyId"] = PREVIOUS_POLICY_ID
        self.assertEqual(update["DistributionConfig"], original["Distribution"]["DistributionConfig"])

    def test_explicit_transition_refuses_missing_foreign_or_inconsistent_policies(self):
        for attached in [(None, None), (PREVIOUS_POLICY_ID, None), (PREVIOUS_POLICY_ID, POLICY_ID),
                         (POLICY_ID, PREVIOUS_POLICY_ID), (POLICY_ID, "foreign")]:
            with self.subTest(attached=attached):
                export = deepcopy(self.export)
                config = export["Distribution"]["DistributionConfig"]
                config["DefaultCacheBehavior"]["ResponseHeadersPolicyId"] = attached[0]
                config["CacheBehaviors"]["Items"][2]["ResponseHeadersPolicyId"] = attached[1]
                with self.assertRaises(ValueError): headers.prepare(export, POLICY_ID, PREVIOUS_POLICY_ID)
        with self.assertRaises(ValueError): headers.prepare(self.export, POLICY_ID, "not-a-policy")

    def test_explicit_transition_reapplication_is_idempotent(self):
        self.export["Distribution"]["DistributionConfig"] = headers.prepare(self.export, POLICY_ID)["DistributionConfig"]
        self.assertEqual(headers.prepare(self.export, POLICY_ID, PREVIOUS_POLICY_ID), headers.prepare(self.export, POLICY_ID))

    def test_security_policy_keeps_current_ui_requirements_without_weakening_scripts(self):
        policy = json.loads((ROOT / "deploy/cloudfront/security-headers.json").read_text())
        security = policy["SecurityHeadersConfig"]
        csp = security["ContentSecurityPolicy"]["ContentSecurityPolicy"]
        directives = {part.strip().split()[0]: part.strip().split()[1:] for part in csp.split(";")}
        self.assertEqual(directives["script-src"], ["'self'"])
        self.assertEqual(directives["connect-src"], ["'self'"])
        self.assertIn("'unsafe-inline'", directives["style-src"])
        self.assertIn("data:", directives["img-src"])
        self.assertIn("blob:", directives["frame-src"])
        self.assertEqual(directives["frame-ancestors"], ["'self'"])
        self.assertEqual(security["FrameOptions"]["FrameOption"], "SAMEORIGIN")
        self.assertLessEqual(len(csp), 1783)
        self.assertFalse(security["StrictTransportSecurity"]["Preload"])
        self.assertFalse(security["StrictTransportSecurity"]["IncludeSubdomains"])
        self.assertTrue(security["ContentTypeOptions"]["Override"])
        self.assertEqual(policy["RemoveHeadersConfig"]["Items"], [{"Header": "Server"}])
        self.assertNotIn("CorsConfig", policy)

    def test_google_font_dependency_is_removed_and_licenses_are_distributed(self):
        for path in [ROOT / "frontend/index.html", ROOT / "frontend/src/routes/landing/Landing.module.css"]:
            self.assertNotIn("fonts.googleapis.com", path.read_text())
        main = (ROOT / "frontend/src/main.tsx").read_text()
        for font, weights in [("instrument-sans", (400, 500, 600, 700)), ("ibm-plex-mono", (400, 500, 600))]:
            for weight in weights:
                self.assertIn(f"@fontsource/{font}/{weight}.css", main)
            self.assertIn("SIL OPEN FONT LICENSE", (ROOT / f"frontend/public/licenses/{font}.txt").read_text())

    def test_strict_candidate_removes_both_csp_findings_without_changing_other_headers(self):
        current = json.loads((ROOT / "deploy/cloudfront/security-headers.json").read_text())
        candidate = json.loads((ROOT / "deploy/cloudfront/security-headers-strict.json").read_text())
        self.assertNotEqual(candidate["Name"], current["Name"])
        csp = candidate["SecurityHeadersConfig"]["ContentSecurityPolicy"]["ContentSecurityPolicy"]
        directives = {part.strip().split()[0]: part.strip().split()[1:] for part in csp.split(";")}
        for directive in ("script-src", "style-src", "font-src", "connect-src"):
            self.assertEqual(directives[directive], ["'self'"])
        self.assertEqual(directives["style-src-attr"], ["'none'"])
        self.assertEqual(directives["img-src"], ["'self'", "data:", "blob:"])
        self.assertEqual(directives["frame-src"], ["'self'", "blob:"])
        self.assertEqual(directives["frame-ancestors"], ["'self'"])
        for forbidden in ("'unsafe-inline'", "'unsafe-eval'", "'unsafe-hashes'", "https:", "*"):
            self.assertNotIn(forbidden, csp)
        candidate["SecurityHeadersConfig"].pop("ContentSecurityPolicy")
        current["SecurityHeadersConfig"].pop("ContentSecurityPolicy")
        self.assertEqual(candidate["SecurityHeadersConfig"], current["SecurityHeadersConfig"])
        self.assertEqual(candidate["RemoveHeadersConfig"], current["RemoveHeadersConfig"])


if __name__ == "__main__":
    unittest.main()
