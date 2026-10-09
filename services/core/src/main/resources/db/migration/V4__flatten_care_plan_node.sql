-- Drops the sub-plan/task distinction from care_plan_node: it turned out to be a purely
-- presentational grouping, not a business rule, so it no longer needs a self-referencing tree
-- or a type discriminator at the storage level. Every row is now a task (it always carries its
-- own visits). group_name replaces both: an optional label the UI uses to cluster tasks under a
-- heading, with no validation or roll-up logic attached to it.
ALTER TABLE care_plan_node
    DROP FOREIGN KEY fk_plan_node_parent,
    DROP KEY idx_plan_node_parent,
    DROP COLUMN parent_id,
    DROP COLUMN node_type,
    ADD COLUMN group_name VARCHAR(150) NULL COMMENT 'display-only grouping label; no business meaning' AFTER care_plan_id;
