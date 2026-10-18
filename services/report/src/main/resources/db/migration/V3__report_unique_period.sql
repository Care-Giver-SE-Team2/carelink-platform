-- Prevent concurrent Report Workers from generating duplicate
-- reports for the same elder, audience and reporting period.

ALTER TABLE report
    ADD CONSTRAINT uk_report_elder_audience_period
    UNIQUE (
        elder_id,
        audience,
        period_start,
        period_end
    );