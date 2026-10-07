-- Adds the care plan's start date (screen 1d, "Starts"): when care under this plan begins.
-- Set by the manager and required before a draft can be published; persists across the plan's
-- later transitions (published -> superseded / stopped), unlike stop_effective_date which is
-- specific to a single stop event.
ALTER TABLE care_plan
    ADD COLUMN start_date DATE NULL COMMENT 'when care under this plan begins; required to publish';
