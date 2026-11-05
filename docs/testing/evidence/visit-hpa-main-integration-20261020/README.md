# Local main integration evidence, 2026-10-20

Local-only sanitized summaries. Raw HTTP/Redis/browser results, credentials, fixture inventory and logs remain private with Chenyu. No shared history or deployment/cloud claims.

- Combined integration: 150 passed; expanded legacy-fact preservation: 1 passed.
- Java unit regression: 2034 passed.
- Accepted tool tests: 104 passed in the 110-test run; final new validator tests: 8 passed separately (112 distinct cases).
- Two fresh fixture environments: four groups, eight sessions, two core/two visit processes each.
- Two fresh deterministic concurrency environments: C00-C03 each 20 times per environment; 120 controlled interleavings plus 40 serial controls.
- Main migrations stay unchanged; report V5 is additive. Old core numbering and old evidence report V4 are expected STOP results, never repaired.

Application source is the applicationCommit in local-summary.json. Later handoff-only commits can differ from that source SHA; the source lock checks application input paths. New build hashes are from that exact application source, not old cached JARs.

The initial unpatched-main probe had three errors on incomplete session metadata; the selected Redis patch resolves them. C05 Cookie ordering and frontend switching remain separate. Temporary-directory permission failures in the first tool test attempt were environmental and were resolved before the passing run.
