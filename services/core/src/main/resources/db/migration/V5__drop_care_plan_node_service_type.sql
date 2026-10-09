-- service_type was never set by CarePlanService (always null) and never read by
-- CarePlanNodeResponse — dead weight left over from V2's original ERD. Dropped.
ALTER TABLE care_plan_node
    DROP COLUMN service_type;
