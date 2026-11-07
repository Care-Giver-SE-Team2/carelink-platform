-- A manager adds extra services to the catalogue from the Extra services screen (docs/section5/decisions.md, D21).
-- Providers offer services by name, and ProviderUpdated and job.dispatched carry the name, so two services may
-- never share one: the check before saving says so to the manager, and this key holds when two managers add the
-- same name at once. The column's collation compares names without regard to case.
ALTER TABLE value_added_service
    ADD CONSTRAINT uk_value_added_service_name UNIQUE (name);
