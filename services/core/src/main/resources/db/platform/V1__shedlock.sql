-- Locks that let one replica at a time run each scheduled job (see SchedulerLocks).
-- A platform migration: recorded in flyway_platform_history, apart from the application's
-- migrations (see PlatformMigrations).
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
