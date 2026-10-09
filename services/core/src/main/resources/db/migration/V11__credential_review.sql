-- UC-MG06: the manager reviews a certificate a caregiver submitted, and publishes it or rejects
-- it. An unreadable scan is a rejection like any other; the note tells the caregiver to upload
-- it again.
--
-- review_note is the reason the manager gave for a rejection; reviewed_at is when the
-- certificate was published or rejected, next to the existing reviewed_by_user_id.
ALTER TABLE credential
    ADD COLUMN review_note VARCHAR(500) NULL COMMENT 'reason for a rejection' AFTER reviewed_by_user_id,
    ADD COLUMN reviewed_at DATETIME     NULL COMMENT 'when reviewed_by_user_id published or rejected it' AFTER review_note;
