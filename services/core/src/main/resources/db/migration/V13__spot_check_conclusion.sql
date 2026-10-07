-- UC-MG08: conduct a home service spot check.
--
-- spot_check already holds the request, the family's consent and the finding. What it could
-- not say is how a request ended: a conclusion with a result the caregiver's record and the
-- rostering search read, a caregiver who did not turn up (and the missed-visit incident that
-- became), or a request closed because the family declined or the manager withdrew it - with
-- the reason, which the use case asks to be kept.
ALTER TABLE spot_check
    ADD COLUMN result         ENUM('MEETS_STANDARD','NEEDS_IMPROVEMENT') NULL
        COMMENT 'the conclusion recorded on site' AFTER finding,
    ADD COLUMN outcome        ENUM('COMPLETED','CAREGIVER_NO_SHOW','WITHDRAWN') NULL
        COMMENT 'how the request ended; null while it is open, and for a declined one' AFTER result,
    ADD COLUMN closing_reason VARCHAR(255) NULL
        COMMENT 'why the family declined, or why the manager withdrew' AFTER outcome,
    ADD COLUMN incident_id    BIGINT       NULL
        COMMENT 'the missed-visit incident when the caregiver did not turn up' AFTER closing_reason,
    ADD KEY idx_spot_check_caregiver (caregiver_id, checked_at),
    ADD KEY idx_spot_check_elder (elder_id, approval_status);
