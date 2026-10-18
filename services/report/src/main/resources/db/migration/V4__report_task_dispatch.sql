-- Each elder/week is dispatched once, atomically with its outbox event.
-- A retry after rollback may claim it again; a committed claim prevents duplicate AI work.
CREATE TABLE report_task_dispatch (
    elder_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (elder_id, period_start, period_end)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
