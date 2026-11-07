-- A manager decides who carries out an extra service, a caregiver or a provider they pick
-- (docs/section5/decisions.md, D20). A provider that declines, or does not answer in time, hands the request
-- back, and the manager may offer it to another: a request may now have several jobs, one open at a time, and
-- never two for the same provider.
ALTER TABLE report_provider_job
    ADD KEY idx_report_provider_job_request (value_added_service_request_id, id),
    DROP INDEX uk_report_provider_job_request,
    ADD UNIQUE KEY uk_report_provider_job_request_provider (value_added_service_request_id, provider_id);
