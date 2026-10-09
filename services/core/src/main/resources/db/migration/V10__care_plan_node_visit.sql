-- A task's schedule, one row per day it runs: the start time and how many minutes.
--
-- Until now a task stored only schedule_days ("MON,WED,FRI") and one
-- duration_per_visit, so a schedule the editor built per day - Mon at 8:00 for
-- 30 m, Wed at 8:00 for 45 m - came back as the same averaged duration every day
-- and with no start time at all. This table holds each day as it was entered.
--
-- schedule_days, duration_per_visit and weekly_hours stay on care_plan_node and
-- are still written on publish: the next-visit lookup reads schedule_days, and
-- weekly_hours remains the task's rolled-up effort (the sum of these minutes).
CREATE TABLE care_plan_node_visit (
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    care_plan_node_id BIGINT   NOT NULL,
    day_of_week       ENUM('MON','TUE','WED','THU','FRI','SAT','SUN') NOT NULL,
    start_time        TIME     NOT NULL,
    minutes           INT      NOT NULL,
    created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_node_visit_day (care_plan_node_id, day_of_week),
    CONSTRAINT fk_node_visit_node FOREIGN KEY (care_plan_node_id) REFERENCES care_plan_node (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Existing tasks never recorded a start time, so each of their days is given
-- 08:00 - the editor's default - and the task's one stored duration.
INSERT INTO care_plan_node_visit (care_plan_node_id, day_of_week, start_time, minutes)
SELECT n.id, d.day_of_week, '08:00:00', ROUND(n.duration_per_visit * 60)
  FROM care_plan_node n
  JOIN (SELECT 'MON' AS day_of_week UNION ALL SELECT 'TUE' UNION ALL SELECT 'WED' UNION ALL SELECT 'THU'
        UNION ALL SELECT 'FRI' UNION ALL SELECT 'SAT' UNION ALL SELECT 'SUN') AS d
    ON n.schedule_days = 'DAILY' OR FIND_IN_SET(d.day_of_week, n.schedule_days) > 0
 WHERE n.duration_per_visit IS NOT NULL
   AND n.duration_per_visit > 0;
