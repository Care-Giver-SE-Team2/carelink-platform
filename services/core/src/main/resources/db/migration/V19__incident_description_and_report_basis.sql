-- Room for a full incident description, and what a generated report was built from (UC-MG07).
--
-- 1. incident.description grows from 500 to 2000 characters. The caregiver's incident form
--    (UC-CG04) and the elder's answer when disputing a visit (UC-EL01), which is filed as the
--    description of the incident it opens, both accept 2000 characters; anything over 500 was
--    refused by the column at the insert.
ALTER TABLE incident MODIFY COLUMN description VARCHAR(2000) NULL;

-- 2. The basis ("底稿", UC-MG07 step 2): every fact one generation run read for one elder and
--    one period, before any reader's filter, and the period's numbers worked out from them.
--    The three readers' reports of that run are assembled from the same facts and point here
--    (report.basis_id), so a number a report states can be traced to what it was counted from.
--
--    The facts are a JSON document for the back office; nothing a family or regulator reads
--    comes from this table. The numbers are columns so they can be listed and compared across
--    elders and weeks without opening the document.
--
--    No unique key on (elder, period): a basis is stored only together with the reports filed
--    from it. If a reader's report of a period is ever filed later than the others, it gets a
--    basis of its own, built from the records as they then were, rather than being pointed at
--    one that does not describe it.
--
--    elder_id is a soft reference, like the app_user references elsewhere: the hard one is on
--    report.elder_id, which every report filed from a basis carries.
CREATE TABLE report_basis (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    elder_id            BIGINT       NOT NULL COMMENT 'soft FK to elder.id',
    period_start        DATE         NOT NULL,
    period_end          DATE         NOT NULL,
    facts               JSON         NOT NULL COMMENT 'every fact the run read, before any reader''s filter; back office only',
    facts_version       SMALLINT     NOT NULL DEFAULT 1 COMMENT 'the shape of facts; raised whenever that shape changes',
    visits_planned      INT          NOT NULL DEFAULT 0 COMMENT 'visits in the period that were not cancelled',
    visits_completed    INT          NOT NULL DEFAULT 0 COMMENT 'of those, the ones carried out: COMPLETED, VERIFIED or AUTO_CLOSED',
    fulfilment_rate     DECIMAL(5,2) NULL     COMMENT 'visits_completed out of visits_planned, in percent; null when nothing was planned',
    vitals_out_of_range INT          NOT NULL DEFAULT 0 COMMENT 'readings flagged out of range when they were entered',
    incident_count      INT          NOT NULL DEFAULT 0,
    avg_elder_rating    DECIMAL(3,2) NULL     COMMENT 'mean of the elder''s ratings of the period''s visits (UC-EL01); null without any',
    rating_count        INT          NOT NULL DEFAULT 0 COMMENT 'how many ratings avg_elder_rating is the mean of',
    data_complete       BOOLEAN      NOT NULL COMMENT 'false while a visit of the period is not closed (UC-MG07 2a)',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_report_basis_elder (elder_id, period_start, period_end)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Reports filed before this migration have no basis and keep a null here.
ALTER TABLE report
    ADD COLUMN basis_id BIGINT NULL COMMENT 'the basis the report was assembled from; null before V19' AFTER elder_id,
    ADD CONSTRAINT fk_report_basis FOREIGN KEY (basis_id) REFERENCES report_basis (id);

-- 3. What an appended note is. A correction says the report was wrong; a follow-up records
--    what was done about something it reported. Both are appended, signed and never edited,
--    and every note written before this migration was a correction.
ALTER TABLE report_amendment
    ADD COLUMN kind ENUM('CORRECTION','FOLLOW_UP') NOT NULL DEFAULT 'CORRECTION' AFTER report_id;
