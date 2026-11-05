-- Check-in no longer records an arrival location (GPS fix or manual note); the
-- row still records who checked in, with which request, and when.
ALTER TABLE visit_check_in_record
    DROP COLUMN location_source,
    DROP COLUMN latitude,
    DROP COLUMN longitude,
    DROP COLUMN accuracy,
    DROP COLUMN location_note,
    DROP COLUMN client_captured_at;
