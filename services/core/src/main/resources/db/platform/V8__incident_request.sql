-- Which incident each keyed request from another service raised. visit asks core to raise an incident
-- inside its own transaction (a caregiver's report, a missed check-in, an elder's dispute); when that
-- transaction fails after core has answered, visit asks again with the same key and gets the same
-- incident back instead of raising a second one. The key commits with the incident it raised.
CREATE TABLE incident_request (
    request_key VARCHAR(120) NOT NULL,
    incident_id BIGINT       NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (request_key),
    KEY idx_incident_request_incident (incident_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
