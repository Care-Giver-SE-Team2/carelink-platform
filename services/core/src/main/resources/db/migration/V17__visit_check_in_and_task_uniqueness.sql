CREATE TABLE visit_check_in_record (
    visit_id BIGINT NOT NULL PRIMARY KEY,
    actor_user_id BIGINT NOT NULL,
    client_request_id CHAR(36) NOT NULL,
    location_source VARCHAR(30) NOT NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    accuracy DOUBLE NULL,
    location_note VARCHAR(500) NULL,
    client_captured_at VARCHAR(40) NULL,
    received_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_check_in_visit FOREIGN KEY (visit_id) REFERENCES visit(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Fails rather than deleting/merging historical duplicate task instances.
ALTER TABLE visit_task ADD UNIQUE KEY uk_visit_task_node (visit_id, care_plan_node_id);
