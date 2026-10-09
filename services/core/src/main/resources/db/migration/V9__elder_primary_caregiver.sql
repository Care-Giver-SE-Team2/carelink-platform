-- The elder's primary caregiver, as the manager assigns it from the Elders index. This is the
-- standing "who looks after this elder" answer, not a per-visit booking: visit.caregiver_id and
-- visit_assignment still record who was rostered onto each visit. At most one row per elder;
-- unassigning deletes the row.
CREATE TABLE elder_primary_caregiver (
    elder_id      BIGINT   NOT NULL,
    caregiver_id  BIGINT   NOT NULL,
    assigned_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (elder_id),
    KEY idx_primary_caregiver_caregiver (caregiver_id),
    CONSTRAINT fk_primary_caregiver_elder FOREIGN KEY (elder_id) REFERENCES elder (id),
    CONSTRAINT fk_primary_caregiver_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
