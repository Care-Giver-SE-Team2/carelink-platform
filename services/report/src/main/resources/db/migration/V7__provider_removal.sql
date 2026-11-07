-- A provider notification removed for good (docs/section5/decisions.md, D19): kept, for the jobs that name it,
-- but offered nothing and refused by the job API. Its open jobs go back to the managers when report hears of it.
ALTER TABLE report_provider
    MODIFY status ENUM('ACTIVE','SUSPENDED','REMOVED') NOT NULL;
