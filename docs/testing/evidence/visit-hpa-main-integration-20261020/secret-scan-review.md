# Secret-scan false positive, 2026-10-20

PR71 CI run [39301818302](https://github.com/Care-Giver-SE-Team2/carelink-platform/actions/runs/39301818302), Secret scanning job 118229271884, reported exactly one `generic-api-key` finding: `protection-qa.json`, line 5, introduced in dcb628c8. The value is the preserved C05 Git commit object `a106286224669cc9660ca1a47b0d107ae89f09f8`, recorded under an auth-named branch key. `git cat-file -t` confirms it is a commit. No credential was exposed by this finding.

The existing `.gitleaks.toml` now permits only that exact secret value, anchored at both ends. It keeps all default rules and the previous notification test-key exception. There is no path exclusion, general SHA exclusion, workflow change or history rewrite.

Local verification used official Gitleaks 8.30.1 for Windows x64. The downloaded archive matched its official release checksum, `d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e`. This does not claim that the CI `latest` Linux container has the same version; the observed CI image digest was `sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f`.

| Actual local scanner run | Result |
| --- | --- |
| History with previous configuration | Exit 1, exactly the same metadata finding |
| History with precise exception | Exit 0, no findings |
| Standalone original commit metadata | Exit 0, no findings |
| Same metadata key with a different value | Exit 1, one finding as required |
| Unrelated synthetic key control | Exit 1, one finding as required |

Negative-control values are generated test data, never credentials. Private reports are redacted. Application inputs, migrations, Redis code, the accepted load engine, protected C05 and PR68 are unchanged by this follow-up. GitHub CI results must still be read on the latest PR head; local scanner success is not a claim that the full CI pipeline passed.
