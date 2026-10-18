-- Evidence may arrive before the visit state; keep it independently, never invent a visit status.
CREATE TABLE report_visit_evidence (
 visit_id BIGINT NOT NULL PRIMARY KEY,
 elder_id BIGINT NOT NULL,
 scheduled_start DATETIME NOT NULL,
 evidence_count INT NOT NULL,
 verified_evidence_count INT NOT NULL,
 version INT NOT NULL,
 CONSTRAINT ck_report_evidence_count CHECK(evidence_count>=0 AND verified_evidence_count>=0 AND verified_evidence_count<=evidence_count)
);
