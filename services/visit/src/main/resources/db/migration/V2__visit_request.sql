-- Which change each keyed request made to a visit. core's re-rostering sends each change with a request
-- key (header Idempotency-Key) and, when it does not hear back, sends it again with the same key; visit
-- answers the repeat with what the first request did instead of changing the visit a second time or
-- refusing a change it has already made. The key commits with the change it made.
CREATE TABLE visit_request (
    request_key     VARCHAR(120) NOT NULL,
    visit_id        BIGINT       NOT NULL COMMENT 'the visit the request changed',
    result_visit_id BIGINT       NULL COMMENT 'the visit a move created; null for the other changes',
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (request_key),
    KEY idx_visit_request_visit (visit_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
