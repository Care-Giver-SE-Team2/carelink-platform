-- CareLink care-domain schema.
--
-- Merged from the four member submissions (elder, family, supervisor, caregiver;
-- the originals are kept under docs/) and revised after the team's review of
-- 2026-09-05 and 2026-09-06. Every decision taken while merging is marked
-- "DECISION" below, numbered 1-19, so the reasoning travels with the schema.
--
-- Together with V1 this creates 37 tables and 50 foreign keys. Verified on
-- MySQL 8.0.43 by hand and on MySQL 8.4 by the pipeline's integration tests.
--
-- Convention (see V1): once this file is on main it is never edited. A change
-- to the schema is a new versioned script, V3__*.sql onwards.
--
-- To try it on an empty database by hand: create a schema, run V1__baseline.sql,
-- then run this file. Flyway does exactly that on application start-up.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Conventions applied throughout
--
-- DECISION 1  Table names are singular.
--             Two of the three submissions and the existing baseline already
--             are; family.sql used plurals.
--
-- DECISION 2  The primary key is always `id BIGINT`.
--             The existing app_user table and family.sql already do this;
--             elder and manager used <table>_id, and manager used INT.
--             A foreign key is named <referenced_table>_id.
--
-- DECISION 3  The elder is called `elder` everywhere.
--             The three submissions used elderly / senior / elder. The code
--             already uses elder (Role.ELDER, /elder route), so the schema
--             follows the code rather than the other way round.
--
-- DECISION 4  Accounts stay in app_user + user_role.
--             elderly_care_system.sql introduced its own `users` table with a
--             single-role ENUM. The identity module is already built on
--             app_user + user_role, which also allows a person to hold more
--             than one role. The `users` table is dropped from the merge.
--
-- DECISION 5  Foreign keys inside a module are real constraints; references
--             that cross a module boundary store the id without a constraint.
--             Cross-module constraints make Flyway migration order a shared
--             problem and let one owner's change break another's table.
--             Cross-module references are marked "-- soft FK" below.
--
-- DECISION 6  utf8mb4 throughout, and every table carries created_at.
--             Tables that can be edited after creation also carry updated_at.
-- ---------------------------------------------------------------------


-- =====================================================================
-- 1. People and profiles
-- =====================================================================

-- Merges elder.elderly + manager.elder, plus the profile fields the supervisor's
-- per-screen ERDs carried but the consolidated SQL dropped (dialect, lives_alone,
-- mobility, continuity_preference) and the ones family's intake form collects.
-- These are the inputs the rostering rules check on screen 1b: dialect match,
-- postal sector band, continuity of caregiver.
CREATE TABLE elder (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    user_id                BIGINT       NULL COMMENT 'soft FK to app_user.id; null until an account is issued',
    full_name              VARCHAR(100) NOT NULL,
    gender                 ENUM('MALE','FEMALE','OTHER') NULL,
    date_of_birth          DATE         NULL,
    phone                  VARCHAR(20)  NULL,
    address                VARCHAR(255) NULL,
    postal_code            VARCHAR(10)  NULL,
    sector                 VARCHAR(50)  NULL COMMENT 'locality band used when matching caregivers',
    preferred_dialects     VARCHAR(100) NULL COMMENT 'comma-separated; matched against caregiver.dialects',
    lives_alone            BOOLEAN      NULL,
    mobility_level         ENUM('INDEPENDENT','ASSISTIVE_CANE','WHEELCHAIR_BEDBOUND') NULL,
    continuity_preference  ENUM('PREFERRED','REQUIRED','NONE') NOT NULL DEFAULT 'PREFERRED'
                           COMMENT 'how strongly the same caregiver should be kept',
    medical_notes          TEXT         NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_elder_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Merges elder.caregiver + manager.caregiver.
-- The two definitions disagreed on almost everything: BIGINT vs INT,
-- full_name vs name, and whether a caregiver has an account at all.
-- Resolved in favour of elder's version (accounts exist, statuses matter)
-- plus manager's sector.
CREATE TABLE caregiver (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL COMMENT 'soft FK to app_user.id; a caregiver always has an account',
    full_name  VARCHAR(100) NOT NULL,
    phone      VARCHAR(20)  NULL,
    sector     VARCHAR(50)  NULL,
    dialects   VARCHAR(100) NULL COMMENT 'comma-separated; supervisor ERD screen 1b',
    status     ENUM('ONBOARDING','AVAILABLE','BUSY','INACTIVE') NOT NULL DEFAULT 'ONBOARDING' COMMENT 'ONBOARDING until the first credential is PUBLISHED; the CERTIFICATION_VALID rostering rule excludes ONBOARDING caregivers',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_caregiver_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Merges elder.family_member + family.family_members + manager.family_member.
--
-- DECISION 7  A family member is an account holder in their own right, not an
--             attribute of one elder. manager.family_member carried an elder_id,
--             which cannot express a person who looks after two elders. The
--             relationship lives in elder_family_binding instead.
CREATE TABLE family_member (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NULL COMMENT 'soft FK to app_user.id',
    full_name           VARCHAR(100) NOT NULL,
    phone               VARCHAR(20)  NULL,
    residential_address VARCHAR(255) NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_family_member_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Merges elder.family_binding + family.senior_family_links. The elder
-- initiates the binding (team decision 2026-09-05: no pairing code, no
-- requested_by; it is always the elder's request) and the family member
-- confirms. access_scope is what the family reads through afterwards.
CREATE TABLE elder_family_binding (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    elder_id           BIGINT      NOT NULL,
    family_member_id   BIGINT      NOT NULL,
    relationship       ENUM('SON','DAUGHTER','SPOUSE','GUARDIAN','OTHER') NOT NULL DEFAULT 'OTHER',
    is_primary_contact BOOLEAN     NOT NULL DEFAULT FALSE,
    access_scope       ENUM('FULL','READ_ONLY') NOT NULL DEFAULT 'FULL',
    status             ENUM('PENDING_CONFIRMATION','ACTIVE','REJECTED','REVOKED') NOT NULL DEFAULT 'PENDING_CONFIRMATION',
    confirmed_at       DATETIME    NULL,
    expires_at         DATETIME    NULL COMMENT 'delegation expiry; revocation cascades to report access',
    created_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_elder_family (elder_id, family_member_id),
    CONSTRAINT fk_binding_elder  FOREIGN KEY (elder_id) REFERENCES elder (id),
    CONSTRAINT fk_binding_family FOREIGN KEY (family_member_id) REFERENCES family_member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From family.intake_applications.
--
-- DECISION 19  Application and master record are two rows, deliberately.
--              The application holds what the family claimed, at the time
--              they claimed it, and stays even when rejected; the elder row
--              is created only on approval, from the (possibly corrected)
--              application. elder_id is filled at that moment so the two are
--              linked afterwards. The same "request first, record later"
--              shape recurs wherever one thing creates another:
--                intake_application.elder_id            filled on approval
--                value_added_service_request.visit_id    filled on dispatch
--                visit_assignment.rostering_candidate_id filled on commit
--                credential.renews_credential_id         new row on renewal
--              In every case the later id is NULL until the event happens.
CREATE TABLE intake_application (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    applicant_family_member_id BIGINT       NOT NULL,
    target_elder_name          VARCHAR(100) NOT NULL,
    target_elder_age           INT          NULL,
    target_address             VARCHAR(255) NOT NULL,
    postal_code                VARCHAR(10)  NOT NULL,
    mobility_level             ENUM('INDEPENDENT','ASSISTIVE_CANE','WHEELCHAIR_BEDBOUND') NOT NULL DEFAULT 'INDEPENDENT',
    preferred_dialects         VARCHAR(100) NULL,
    care_needs                 JSON         NULL COMMENT 'requested tasks, e.g. ["BATHING","VITALS"]',
    medical_notes              TEXT         NULL,
    status                     ENUM('SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED') NOT NULL DEFAULT 'SUBMITTED',
    reviewed_by_user_id        BIGINT       NULL COMMENT 'soft FK to app_user.id',
    review_remarks             VARCHAR(255) NULL,
    created_at                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at                DATETIME     NULL,
    elder_id                   BIGINT       NULL COMMENT 'set on approval: the elder record created from this application; null while pending or rejected',
    PRIMARY KEY (id),
    KEY idx_intake_status (status),
    CONSTRAINT fk_intake_family FOREIGN KEY (applicant_family_member_id) REFERENCES family_member (id),
    KEY idx_intake_elder (elder_id),
    CONSTRAINT fk_intake_elder FOREIGN KEY (elder_id) REFERENCES elder (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From the supervisor's screen-2a ERD (CREDENTIAL_TYPE), which the consolidated
-- SQL dropped. A catalogue of certification kinds, so that a plan can require
-- "first aid" as a type rather than as free text that has to match by string.
CREATE TABLE credential_type (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_credential_type_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From manager.credential, now pointing at credential_type. SYS01 scans
-- expiry_date daily; screen 2d shows the 30-day warning threshold.
CREATE TABLE credential (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    caregiver_id        BIGINT       NOT NULL,
    credential_type_id  BIGINT       NOT NULL,
    reviewed_by_user_id BIGINT       NULL COMMENT 'soft FK to app_user.id',
    certificate_no      VARCHAR(100) NULL,
    issuing_body        VARCHAR(150) NULL,
    valid_from          DATE         NULL,
    expiry_date         DATE         NOT NULL DEFAULT '9999-12-31' COMMENT 'default = permanent; the daily expiry scan never reaches it',
    status              ENUM('SUBMITTED','PUBLISHED','REJECTED','EXPIRING','EXPIRED','REVOKED')
                        NOT NULL DEFAULT 'SUBMITTED',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    renews_credential_id BIGINT      NULL COMMENT 'FK self: the credential this one replaces on renewal; null for a first submission',
    PRIMARY KEY (id),
    KEY idx_credential_expiry (expiry_date, status),
    CONSTRAINT fk_credential_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id),
    CONSTRAINT fk_credential_type      FOREIGN KEY (credential_type_id) REFERENCES credential_type (id),
    CONSTRAINT fk_credential_renews FOREIGN KEY (renews_credential_id) REFERENCES credential (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================================
-- 2. Care plan
-- =====================================================================

-- From manager.care_plan, with supersedes_plan_id and published_at restored from
-- the supervisor's screen-2a ERD. Screen 2a shows the version history ("v4 vitals
-- to daily, v3 grooming added"), which is what the supersedes chain records.
CREATE TABLE care_plan (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    elder_id            BIGINT        NOT NULL,
    created_by_user_id  BIGINT        NULL COMMENT 'soft FK to app_user.id',
    supersedes_plan_id  BIGINT        NULL COMMENT 'the previous version this one replaced',
    version             INT           NOT NULL DEFAULT 1,
    status              ENUM('DRAFT','PUBLISHED','SUPERSEDED') NOT NULL DEFAULT 'DRAFT',
    total_hours         DECIMAL(6,2)  NULL COMMENT 'rolled up from care_plan_node, not entered by hand',
    published_at        DATETIME      NULL,
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_care_plan_elder (elder_id, status),
    CONSTRAINT fk_care_plan_elder      FOREIGN KEY (elder_id) REFERENCES elder (id),
    CONSTRAINT fk_care_plan_supersedes FOREIGN KEY (supersedes_plan_id) REFERENCES care_plan (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- This IS in a submission after all: the supervisor's screen-2a ERD has PLAN_NODE,
-- "a self-referencing tree (sub-plans nest to any depth)". It was dropped when the
-- twelve-table SQL was consolidated, and it is the table the Composite design
-- problem is built on. The columns below are his, renamed to the conventions above.
--
-- DECISION 8  The plan is a tree, not one flat row. Story A3 requires hours to
--             roll up "from a task item to the overall plan" across an
--             indeterminate number of levels, which is the whole reason
--             Composite was chosen. parent_id null means the node sits directly
--             under the plan. Screen 2a: "a sub-plan can hold tasks or further
--             sub-plans to any depth; effort at every level is the sum of its
--             children".
CREATE TABLE care_plan_node (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    care_plan_id       BIGINT        NOT NULL,
    parent_id          BIGINT        NULL COMMENT 'self reference; null at the top level',
    node_type          ENUM('SUB_PLAN','TASK') NOT NULL,
    name               VARCHAR(150)  NOT NULL,
    service_type       VARCHAR(50)   NULL COMMENT 'bathing, medication reminder, rehabilitation …',
    schedule_days      VARCHAR(30)   NULL COMMENT 'e.g. MON,WED,FRI or DAILY',
    duration_per_visit DECIMAL(5,2)  NULL COMMENT 'hours; TASK nodes only',
    weekly_hours       DECIMAL(6,2)  NULL COMMENT 'TASK: computed; SUB_PLAN: sum of children',
    evidence_type      ENUM('NONE','CHECKLIST','PHOTO','READING') NOT NULL DEFAULT 'NONE'
                       COMMENT 'what the caregiver must capture to close the task',
    display_order      INT           NOT NULL DEFAULT 0,
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_plan_node_plan (care_plan_id),
    KEY idx_plan_node_parent (parent_id),
    CONSTRAINT fk_plan_node_plan   FOREIGN KEY (care_plan_id) REFERENCES care_plan (id),
    CONSTRAINT fk_plan_node_parent FOREIGN KEY (parent_id) REFERENCES care_plan_node (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From the supervisor's screen-2a ERD (CARE_PLAN_REQUIRED_CREDENTIAL). Which
-- certifications a caregiver must hold to be rostered onto this plan; rostering
-- checks it ("certification valid: PASS" on screen 1b).
CREATE TABLE care_plan_required_credential (
    care_plan_id        BIGINT NOT NULL,
    credential_type_id  BIGINT NOT NULL,
    PRIMARY KEY (care_plan_id, credential_type_id),
    CONSTRAINT fk_cprc_plan FOREIGN KEY (care_plan_id) REFERENCES care_plan (id),
    CONSTRAINT fk_cprc_type FOREIGN KEY (credential_type_id) REFERENCES credential_type (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================================
-- 3. Rostering and visits
-- =====================================================================

-- From the caregiver analysis (WorkPreference). UC-CG02: what the caregiver would
-- prefer. Rostering reads it as a soft preference; it never overrides a hard
-- constraint such as an expired credential or an approved absence.
CREATE TABLE caregiver_preference (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    caregiver_id            BIGINT       NOT NULL,
    preferred_service_types VARCHAR(255) NULL COMMENT 'comma-separated',
    preferred_sectors       VARCHAR(255) NULL COMMENT 'comma-separated',
    preferred_time_windows  VARCHAR(255) NULL COMMENT 'e.g. MON 08:00-12:00;WED 13:00-17:00',
    max_visits_per_day      INT          NULL,
    max_hours_per_day       DECIMAL(4,1) NULL COMMENT 'screen 1b checks "daily hours cap" against this',
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_preference_caregiver (caregiver_id),
    CONSTRAINT fk_preference_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From the caregiver analysis (Availability). Day-by-day windows the caregiver
-- can be rostered into. Combined with absence_report to decide who is free.
CREATE TABLE caregiver_availability (
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    caregiver_id    BIGINT   NOT NULL,
    available_date  DATE     NOT NULL,
    available_start TIME     NOT NULL,
    available_end   TIME     NOT NULL,
    status          ENUM('AVAILABLE','UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_availability_caregiver_date (caregiver_id, available_date),
    CONSTRAINT fk_availability_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From manager.absence_report. CG02 writes it, MG04 reads it.
CREATE TABLE absence_report (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    caregiver_id        BIGINT   NOT NULL,
    reviewed_by_user_id BIGINT   NULL COMMENT 'soft FK to app_user.id',
    type                ENUM('SICK','ANNUAL','EMERGENCY','OTHER') NOT NULL DEFAULT 'OTHER',
    start_date          DATE     NOT NULL,
    end_date            DATE     NOT NULL,
    reason              VARCHAR(255) NULL,
    status              ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_absence_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Merges manager.visit + elder.service_order.
--
-- DECISION 9  These were the same thing under two names. manager.visit is a
--             scheduled home visit; elder.service_order is a service with a
--             caregiver, a time window and a completion confirmation. Keeping
--             both would mean the caregiver closing a visit while the elder
--             confirms an order, with nothing joining them.
--
--             The merged status list is elder's, which is the one the State
--             design problem is built on, plus manager's EXCEPTION outcome.
CREATE TABLE visit (
    id                 BIGINT   NOT NULL AUTO_INCREMENT,
    elder_id           BIGINT   NOT NULL,
    caregiver_id       BIGINT   NULL COMMENT 'null while unassigned',
    care_plan_node_id  BIGINT   NULL COMMENT 'soft FK to care_plan_node.id; which plan task this fulfils',
    absence_id         BIGINT   NULL COMMENT 'set when this visit was re-rostered because of an absence',
    service_type       VARCHAR(50)  NULL,
    scheduled_start    DATETIME NOT NULL,
    scheduled_end      DATETIME NULL,
    checked_in_at      DATETIME NULL,
    checked_out_at     DATETIME NULL,
    status             ENUM('SCHEDULED','ARRIVED','IN_PROGRESS','COMPLETED','VERIFIED','AUTO_CLOSED','EXCEPTION','CANCELLED')
                       NOT NULL DEFAULT 'SCHEDULED'
                       COMMENT 'COMPLETED = checked out, awaiting the elder; VERIFIED = elder confirmed and supervisor verified; AUTO_CLOSED = the elder never answered (DECISION 16)',
    state_deadline     DATETIME NULL COMMENT 'when the current state must have advanced by; SYS03 infers a missed check-in from it',
    care_plan_id       BIGINT   NULL COMMENT 'soft FK to care_plan.id: the plan VERSION in force when this visit was created (story A6). Superseded versions are never edited, so pointing at the row is the snapshot',
    version            INT      NOT NULL DEFAULT 0 COMMENT 'optimistic lock for concurrent state changes',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_visit_elder (elder_id, scheduled_start),
    KEY idx_visit_caregiver (caregiver_id, scheduled_start),
    KEY idx_visit_status (status),
    KEY idx_visit_deadline (status, state_deadline),
    CONSTRAINT fk_visit_elder     FOREIGN KEY (elder_id) REFERENCES elder (id),
    CONSTRAINT fk_visit_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id),
    CONSTRAINT fk_visit_absence   FOREIGN KEY (absence_id) REFERENCES absence_report (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From the caregiver analysis (RosterAssignment). visit.caregiver_id above is the
-- CURRENT assignee, kept there because every roster query needs it. This table is
-- the history: who was assigned, who was replaced after an absence, and why.
--
-- DECISION 15  Caregiver-to-visit is many-to-many over time, one-to-one at any
--              instant. The caregiver analysis is right that a re-roster must not
--              erase the previous assignment. So the current one is denormalised
--              onto visit and the full history lives here.
CREATE TABLE visit_assignment (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    visit_id         BIGINT   NOT NULL,
    caregiver_id     BIGINT   NOT NULL,
    assigned_by_user_id BIGINT NULL COMMENT 'soft FK to app_user.id',
    status           ENUM('ACTIVE','REPLACED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    reason           VARCHAR(255) NULL COMMENT 'why this assignment was made or replaced',
    assigned_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at         DATETIME NULL,
    rostering_candidate_id BIGINT NULL COMMENT 'soft FK to rostering_candidate.id: the run and option this assignment came from; null when assigned by hand',
    PRIMARY KEY (id),
    KEY idx_assignment_visit (visit_id, status),
    KEY idx_assignment_caregiver (caregiver_id, assigned_at),
    CONSTRAINT fk_assignment_visit     FOREIGN KEY (visit_id) REFERENCES visit (id),
    CONSTRAINT fk_assignment_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id),
    KEY idx_assignment_candidate (rostering_candidate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From the caregiver analysis (VisitStateTransition). Every attempt to move the
-- visit between states, INCLUDING the ones the state machine rejected. Story C8
-- asks for exactly this: "rejected transition attempts logged separately from the
-- legal history". It is the evidence the State design problem is judged on.
CREATE TABLE visit_state_transition (
    id               BIGINT   NOT NULL AUTO_INCREMENT,
    visit_id         BIGINT   NOT NULL,
    from_state       VARCHAR(20) NOT NULL,
    to_state         VARCHAR(20) NOT NULL,
    actor_user_id    BIGINT   NULL COMMENT 'soft FK to app_user.id; null when the system acted',
    result           ENUM('APPLIED','REJECTED') NOT NULL,
    rejection_reason VARCHAR(255) NULL,
    occurred_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_transition_visit (visit_id, occurred_at),
    CONSTRAINT fk_transition_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------
-- Rostering run  (Strategy)
--
-- NOT submitted as tables by anyone, but drawn in full on supervisor screens
-- 1b and 4c: an objective toggle (Continuity / Travel time / Even workload /
-- Cost), a "constraints checked" panel with HARD and SOFT rules, "suggestion
-- 1 of 4" per visit with Accept / Next option, and a run summary (visits
-- covered 6 of 7, continuity kept 5 of 7, added travel +11 km). Stories
-- B1-B4 describe the same thing.
--
-- DECISION 17  A rostering run is recorded, not only its outcome. The
--              objective is the Strategy that ran; every candidate that was
--              considered is kept with the result of each constraint check,
--              so the supervisor can answer "why was this caregiver not
--              suggested" and the report can show it. Accepting a candidate
--              creates a visit_assignment; nothing here updates visit
--              directly.
-- ---------------------------------------------------------------------

-- The rule set, data-driven so a rule can be switched off or its threshold
-- changed without a release (screen 1b: "configurable").
CREATE TABLE rostering_constraint (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    code            VARCHAR(40)  NOT NULL COMMENT 'CERTIFICATION_VALID, DAILY_VISIT_CAP, SECTOR_BAND, DIALECT_MATCH, CONTINUITY, TRAVEL_DISTANCE, DAILY_HOURS_CAP',
    name            VARCHAR(100) NOT NULL,
    kind            ENUM('HARD','SOFT') NOT NULL COMMENT 'HARD excludes the candidate; SOFT only changes the score',
    parameter_value VARCHAR(50)  NULL COMMENT 'threshold shown on screen 4c: 8 visits, 5 km, 8.0 hours',
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rostering_constraint_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One click of "Re-roster" or "find a caregiver". A run covers one or many
-- visits: an absence vacates several at once (screen 4c re-rosters seven).
CREATE TABLE rostering_run (
    id                   BIGINT   NOT NULL AUTO_INCREMENT,
    trigger_type         ENUM('NEW_VISIT','ABSENCE','MANUAL') NOT NULL,
    absence_id           BIGINT   NULL COMMENT 'the absence that vacated the visits, when trigger_type = ABSENCE',
    objective            ENUM('CONTINUITY','TRAVEL_TIME','EVEN_WORKLOAD','COST') NOT NULL COMMENT 'the Strategy that ran; screen 4c objective tabs',
    requested_by_user_id BIGINT   NULL COMMENT 'soft FK to app_user.id',
    status               ENUM('PROPOSED','COMMITTED','DISCARDED') NOT NULL DEFAULT 'PROPOSED',
    visits_total         INT      NOT NULL DEFAULT 0,
    visits_covered       INT      NOT NULL DEFAULT 0,
    continuity_kept      INT      NOT NULL DEFAULT 0,
    added_travel_km      DECIMAL(6,1) NULL,
    ran_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    committed_at         DATETIME NULL,
    PRIMARY KEY (id),
    KEY idx_rostering_run_absence (absence_id),
    CONSTRAINT fk_rostering_run_absence FOREIGN KEY (absence_id) REFERENCES absence_report (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Every caregiver considered for every visit in the run, including those a
-- HARD constraint excluded. "Suggestion 1 of 4" is option_rank 1..4.
CREATE TABLE rostering_candidate (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    rostering_run_id BIGINT       NOT NULL,
    visit_id         BIGINT       NOT NULL,
    caregiver_id     BIGINT       NOT NULL,
    option_rank      INT          NULL COMMENT 'null when excluded',
    score            DECIMAL(6,2) NULL COMMENT 'objective score; its meaning depends on rostering_run.objective',
    outcome          ENUM('SELECTED','SUGGESTED','EXCLUDED') NOT NULL,
    excluded_by_code VARCHAR(40)  NULL COMMENT 'rostering_constraint.code of the HARD rule that excluded this candidate',
    PRIMARY KEY (id),
    KEY idx_candidate_run_visit (rostering_run_id, visit_id, option_rank),
    KEY idx_candidate_caregiver (caregiver_id),
    CONSTRAINT fk_candidate_run       FOREIGN KEY (rostering_run_id) REFERENCES rostering_run (id),
    CONSTRAINT fk_candidate_visit     FOREIGN KEY (visit_id) REFERENCES visit (id),
    CONSTRAINT fk_candidate_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The "constraints checked" panel on screen 1b, one row per rule per
-- candidate: Dialect match PASS, Certification valid PASS, Continuity
-- "2 prior visits", Daily hours cap "6.5 / 8.0".
CREATE TABLE rostering_candidate_check (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    rostering_candidate_id  BIGINT       NOT NULL,
    rostering_constraint_id BIGINT       NOT NULL,
    result                  ENUM('PASS','FAIL','NOT_APPLICABLE') NOT NULL,
    detail                  VARCHAR(100) NULL COMMENT 'what the screen shows next to the result',
    PRIMARY KEY (id),
    UNIQUE KEY uk_check_candidate_constraint (rostering_candidate_id, rostering_constraint_id),
    CONSTRAINT fk_check_candidate  FOREIGN KEY (rostering_candidate_id) REFERENCES rostering_candidate (id),
    CONSTRAINT fk_check_constraint FOREIGN KEY (rostering_constraint_id) REFERENCES rostering_constraint (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From the caregiver analysis (VisitTaskExecution). The caregiver ticks tasks off
-- during the visit; each row is one care_plan_node of type TASK as executed on
-- this visit. UC-CG03, UC-CG05.
CREATE TABLE visit_task (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id           BIGINT       NOT NULL,
    care_plan_node_id  BIGINT       NULL COMMENT 'soft FK to care_plan_node.id; the TASK this executes',
    name               VARCHAR(150) NOT NULL,
    status             ENUM('PENDING','DONE','SKIPPED','REFUSED') NOT NULL DEFAULT 'PENDING',
    outcome            VARCHAR(255) NULL,
    caregiver_note     VARCHAR(500) NULL,
    completed_at       DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_visit_task_visit (visit_id),
    CONSTRAINT fk_visit_task_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE vital_sign (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id    BIGINT       NOT NULL,
    metric      VARCHAR(50)  NOT NULL COMMENT 'systolic, diastolic, pulse, temperature …',
    value       DECIMAL(8,2) NOT NULL,
    unit        VARCHAR(20)  NULL,
    out_of_range BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'story C6: highlighted at entry time',
    recorded_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_vital_visit (visit_id),
    CONSTRAINT fk_vital_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From the caregiver analysis (VisitEvidence). What kinds are required comes from
-- care_plan_node.evidence_type; the visit cannot close until they are present.
CREATE TABLE visit_evidence (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    visit_id            BIGINT       NOT NULL,
    kind                ENUM('PHOTO','SIGNATURE','CHECKLIST','READING','NOTE') NOT NULL,
    reference           VARCHAR(255) NOT NULL COMMENT 'stored file reference',
    verification_status ENUM('UNVERIFIED','VERIFIED','REJECTED') NOT NULL DEFAULT 'UNVERIFIED',
    captured_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_evidence_visit (visit_id),
    CONSTRAINT fk_evidence_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================================
-- 4. Incidents
-- =====================================================================

-- Merges manager.incident + elder.emergency_alert.
--
-- DECISION 10  A one-touch SOS from the elder is an incident, not a separate
--              kind of record. Keeping emergency_alert apart would mean the
--              escalation chain and its timeout (SYS02) had to be built twice.
--              `source` records where it came from; the location columns from
--              emergency_alert are kept because an SOS carries them.
CREATE TABLE incident (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    elder_id             BIGINT       NOT NULL,
    visit_id             BIGINT       NULL COMMENT 'set when raised during a visit',
    reported_by_user_id  BIGINT       NULL COMMENT 'soft FK to app_user.id',
    responder_user_id    BIGINT       NULL COMMENT 'soft FK to app_user.id; current owner',
    source               ENUM('CAREGIVER','ELDER_SOS','SYSTEM_MISSED_CHECKIN') NOT NULL,
    category             ENUM('SOS','MEDICAL','FALL','SERVICE','OTHER') NOT NULL DEFAULT 'OTHER',
    severity             ENUM('LOW','MEDIUM','HIGH') NOT NULL,
    status               ENUM('OPEN','ACKNOWLEDGED','IN_PROGRESS','RESOLVED','UNRESOLVED_ESCALATED')
                         NOT NULL DEFAULT 'OPEN',
    latitude             DECIMAL(10,7) NULL,
    longitude            DECIMAL(10,7) NULL,
    location_text        VARCHAR(255) NULL,
    description          VARCHAR(500) NULL,
    respond_by           DATETIME     NULL COMMENT 'countdown deadline; SYS02 escalates past this',
    reported_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at          DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_incident_elder (elder_id, status),
    KEY idx_incident_deadline (status, respond_by),
    CONSTRAINT fk_incident_elder FOREIGN KEY (elder_id) REFERENCES elder (id),
    CONSTRAINT fk_incident_visit FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From manager.incident_log. Story D8 requires every notification and every
-- timeout to be an immutable record, so nothing here is ever updated.
CREATE TABLE incident_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    incident_id   BIGINT       NOT NULL,
    actor         VARCHAR(150) NOT NULL,
    action        VARCHAR(255) NOT NULL,
    detail        VARCHAR(500) NULL,
    occurred_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_incident_log_incident (incident_id, occurred_at),
    CONSTRAINT fk_incident_log_incident FOREIGN KEY (incident_id) REFERENCES incident (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- From family.urgent_alert_acknowledgments, retargeted at incident.
CREATE TABLE incident_acknowledgement (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    incident_id      BIGINT       NOT NULL,
    family_member_id BIGINT       NOT NULL,
    viewed_at        DATETIME     NULL,
    acknowledged_at  DATETIME     NULL,
    response_note    VARCHAR(255) NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_incident_family (incident_id, family_member_id),
    CONSTRAINT fk_ack_incident FOREIGN KEY (incident_id) REFERENCES incident (id),
    CONSTRAINT fk_ack_family   FOREIGN KEY (family_member_id) REFERENCES family_member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================================
-- 5. Spot checks
-- =====================================================================

-- Merges manager.spot_check + family.home_inspection_consents.
--
-- DECISION 11  These were the two ends of one flow: the manager proposes a
--              check, the family approves it. Two tables would have meant two
--              statuses that could disagree with each other.
CREATE TABLE spot_check (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    elder_id               BIGINT       NOT NULL,
    caregiver_id           BIGINT       NULL,
    visit_id               BIGINT       NULL,
    raised_by_user_id      BIGINT       NULL COMMENT 'soft FK to app_user.id',
    approving_family_member_id    BIGINT       NULL,
    proposed_time          DATETIME     NOT NULL,
    reason                 VARCHAR(255) NULL,
    approval_status        ENUM('PENDING_APPROVAL','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING_APPROVAL',
    decided_at             DATETIME     NULL,
    finding                VARCHAR(500) NULL,
    caregiver_response     VARCHAR(500) NULL COMMENT 'caregiver analysis: the caregiver may respond to a finding',
    checked_at             DATETIME     NULL,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_spot_check_status (approval_status),
    CONSTRAINT fk_spot_check_elder     FOREIGN KEY (elder_id) REFERENCES elder (id),
    CONSTRAINT fk_spot_check_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id),
    CONSTRAINT fk_spot_check_family    FOREIGN KEY (approving_family_member_id) REFERENCES family_member (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================================
-- 6. Reports
-- =====================================================================

-- From manager.report. `audience` is what the redaction design problem
-- switches on, and the scope actually applied comes from
-- elder_family_binding.access_scope at generation time.
CREATE TABLE report (
    id                 BIGINT   NOT NULL AUTO_INCREMENT,
    elder_id           BIGINT   NOT NULL,
    generated_by_user_id BIGINT NULL COMMENT 'soft FK to app_user.id',
    audience           ENUM('FAMILY','REGULATOR','INTERNAL') NOT NULL,
    period_start       DATE     NOT NULL,
    period_end         DATE     NOT NULL,
    status             ENUM('DRAFT','PUBLISHED','ARCHIVED') NOT NULL DEFAULT 'DRAFT',
    content            JSON     NULL COMMENT 'rendered sections, already filtered for the audience',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_report_elder (elder_id, period_start),
    CONSTRAINT fk_report_elder FOREIGN KEY (elder_id) REFERENCES elder (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================================
-- 7. Value-added services and feedback
-- =====================================================================

-- From elder.value_added_service. A catalogue, edited by the provider.
CREATE TABLE value_added_service (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    name         VARCHAR(100)  NOT NULL,
    description  TEXT          NULL,
    status       ENUM('AVAILABLE','UNAVAILABLE') NOT NULL DEFAULT 'AVAILABLE',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Merges elder.value_added_service_request + family.value_added_service_orders.
--
-- DECISION 12  One table, not two. The elder requests (UC-EL02) and the family
--              approves (UC-FM08) — the same row, two states. Two tables would
--              have made "which request did this order come from" a join
--              nobody had modelled.
--
-- DECISION 13  No price column. The proposal puts billing and settlement out
--              of scope, and elder.value_added_service carried a price.
CREATE TABLE value_added_service_request (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    elder_id                BIGINT       NOT NULL,
    value_added_service_id  BIGINT       NOT NULL,
    requested_by_family_member_id  BIGINT       NULL COMMENT 'null when the elder requested it directly',
    approving_family_member_id     BIGINT       NULL,
    visit_id                BIGINT       NULL COMMENT 'set once dispatched as a visit',
    requested_schedule      DATETIME     NULL,
    special_instructions    TEXT         NULL,
    status                  ENUM('PENDING_APPROVAL','APPROVED','REJECTED','DISPATCHED','COMPLETED','CANCELLED')
                            NOT NULL DEFAULT 'PENDING_APPROVAL',
    decided_at              DATETIME     NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_vas_request_elder (elder_id, status),
    CONSTRAINT fk_vas_request_elder   FOREIGN KEY (elder_id) REFERENCES elder (id),
    CONSTRAINT fk_vas_request_service FOREIGN KEY (value_added_service_id) REFERENCES value_added_service (id),
    CONSTRAINT fk_vas_request_visit   FOREIGN KEY (visit_id) REFERENCES visit (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Merges elder.service_feedback with the caregiver analysis's ElderConfirmation,
-- revised after the elder-module owner's review ("elder_confirmation design
-- recommendation", 2026-09-05).
--
-- DECISION 16  The elder's confirmation is its own record, not two columns on
--              visit, and it records ONLY the elder's own act. The caregiver
--              analysis: "submitted by the Elder independently; the Caregiver
--              may not confirm on their behalf". The elder-module review went
--              further and removed confirmed_by: a caregiver-recorded verbal
--              acknowledgement and a timeout closure are not confirmations and
--              must never be stored as one. A row exists only once the elder
--              has answered, so there is no NOT_RESPONDED state here.
--                caregiver checks out        -> visit COMPLETED (awaiting the elder)
--                elder answers YES           -> row CONFIRMED (+ rating, comment)
--                elder answers NO            -> row DISPUTED, incident opened
--                elder never answers         -> visit AUTO_CLOSED, no row here
--                caregiver notes a verbal OK -> visit_state_transition / audit_log
--              If a family proxy is ever allowed, add confirmation_source
--              (ELDER_SELF / FAMILY_PROXY) and confirmed_by_user_id at that time.
--              EL01 rates one visit here; FM09 rates a period in caregiver_review.
CREATE TABLE elder_confirmation (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    visit_id            BIGINT   NOT NULL,
    elder_id            BIGINT   NOT NULL COMMENT 'the elder who answered; must equal visit.elder_id',
    confirmation_status ENUM('CONFIRMED','DISPUTED') NOT NULL,
    rating              TINYINT  NULL COMMENT 'EL01: the elder rates this one visit',
    comment             TEXT     NULL,
    confirmed_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_elder_confirmation_visit (visit_id),
    KEY idx_elder_confirmation_elder (elder_id),
    CONSTRAINT chk_elder_confirmation_rating CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT fk_elder_confirmation_visit FOREIGN KEY (visit_id) REFERENCES visit (id),
    CONSTRAINT fk_elder_confirmation_elder FOREIGN KEY (elder_id) REFERENCES elder (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Merges family.periodic_caregiver_reviews with elder.renewal_decision.
--
-- DECISION 14  The renewal decision is a column here, not a table of its own.
--              elder.renewal_decision and family.periodic_caregiver_reviews
--              recorded the same judgement twice; a periodic review that ends
--              in a renewal decision is one act, not two.
CREATE TABLE caregiver_review (
    id                 BIGINT   NOT NULL AUTO_INCREMENT,
    family_member_id   BIGINT   NOT NULL,
    elder_id           BIGINT   NOT NULL,
    caregiver_id       BIGINT   NOT NULL,
    period_start       DATE     NOT NULL,
    period_end         DATE     NOT NULL,
    overall_rating     TINYINT  NOT NULL,
    punctuality_score  TINYINT  NULL,
    care_quality_score TINYINT  NULL,
    feedback_notes     TEXT     NULL,
    renewal_decision   ENUM('RENEW_CURRENT','REQUEST_CHANGE','CANCEL_SERVICE') NOT NULL DEFAULT 'RENEW_CURRENT',
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_review_caregiver (caregiver_id),
    KEY idx_review_period (period_start, period_end),
    CONSTRAINT chk_review_rating CHECK (overall_rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_family    FOREIGN KEY (family_member_id) REFERENCES family_member (id),
    CONSTRAINT fk_review_elder     FOREIGN KEY (elder_id) REFERENCES elder (id),
    CONSTRAINT fk_review_caregiver FOREIGN KEY (caregiver_id) REFERENCES caregiver (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================================
-- 8. Platform: audit
-- =====================================================================

-- From the caregiver analysis (AuditLog), and promised in the proposal as the
-- non-functional requirement "we can always answer who knew what, and when".
-- Append-only: rows are never updated or deleted. Owned by the platform layer.
CREATE TABLE audit_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT       NULL COMMENT 'soft FK to app_user.id; null for scheduled jobs',
    action        VARCHAR(50)  NOT NULL COMMENT 'READ, CREATE, UPDATE, STATE_CHANGE, EXPORT …',
    resource_type VARCHAR(50)  NOT NULL COMMENT 'table or aggregate name',
    resource_id   BIGINT       NULL,
    result        ENUM('OK','DENIED','FAILED') NOT NULL DEFAULT 'OK',
    detail        VARCHAR(500) NULL,
    occurred_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_audit_resource (resource_type, resource_id, occurred_at),
    KEY idx_audit_actor (actor_user_id, occurred_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================================
-- 9. Notifications  (Observer)
-- =====================================================================

-- NOT in any submission. UC-FM03 (live feed) and UC-FM05 (know about an
-- incident at once) both need a record of what was sent to whom. Proof of
-- concept sends IN_APP only; the channel column exists so SMS or email can
-- be added as another Observer without touching the tables.
--
-- DECISION 18  A notification is one row per recipient, created when a
--              domain event happens (visit state change, incident raised or
--              escalated, credential expiring, spot check requested, roster
--              changed). What a family member receives is decided by their
--              subscription AND by elder_family_binding.access_scope: the
--              subscription says what they want, the binding says what they
--              may see.
CREATE TABLE notification_subscription (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL COMMENT 'soft FK to app_user.id',
    elder_id   BIGINT      NULL COMMENT 'whose events; null = events about the user themself, e.g. credential expiring',
    event_type VARCHAR(40) NOT NULL COMMENT 'VISIT_STARTED, VISIT_COMPLETED, INCIDENT_RAISED, INCIDENT_ESCALATED, CREDENTIAL_EXPIRING, SPOT_CHECK_REQUESTED, ROSTER_CHANGED',
    channel    ENUM('IN_APP','SMS','EMAIL') NOT NULL DEFAULT 'IN_APP',
    enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subscription (user_id, elder_id, event_type, channel),
    CONSTRAINT fk_subscription_elder FOREIGN KEY (elder_id) REFERENCES elder (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE notification (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT        NOT NULL COMMENT 'soft FK to app_user.id',
    event_type        VARCHAR(40)   NOT NULL,
    channel           ENUM('IN_APP','SMS','EMAIL') NOT NULL DEFAULT 'IN_APP',
    title             VARCHAR(150)  NOT NULL,
    body              VARCHAR(1000) NULL,
    resource_type     VARCHAR(50)   NULL COMMENT 'what it links to: visit, incident, credential …',
    resource_id       BIGINT        NULL,
    status            ENUM('PENDING','SENT','READ','FAILED') NOT NULL DEFAULT 'PENDING',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at           DATETIME      NULL,
    read_at           DATETIME      NULL,
    PRIMARY KEY (id),
    KEY idx_notification_recipient (recipient_user_id, status, created_at),
    KEY idx_notification_resource (resource_type, resource_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================================
-- Dropped from the merge, with the reason
--
--   users                      DECISION 4 — app_user + user_role already exist
--   supervisor                 a supervisor is an app_user holding the MANAGER
--                              role, not a separate person table
--   elderly, senior            DECISION 3 — renamed to elder
--   service_order              DECISION 9 — merged into visit
--   emergency_alert            DECISION 10 — merged into incident
--   home_inspection_consents   DECISION 11 — merged into spot_check
--   value_added_service_orders DECISION 12 — merged into value_added_service_request
--   renewal_decision           DECISION 14 — a column on caregiver_review
--   service_feedback           DECISION 16 — merged into elder_confirmation
--
-- From the caregiver analysis, mapped rather than added as tables
--
--   UserAccount        = app_user + user_role
--   CaregiverProfile   = caregiver (languages -> dialects, serviceAreas -> sector)
--   Certification      = credential
--   LeaveRequest       = absence_report
--   ElderVisitView     a read model over elder, not a table — the caregiver
--                      sees a minimal projection, which is a query and an
--                      authorisation rule, not storage
--   CarePlanSnapshot   = visit.care_plan_id pointing at the plan version in
--                      force; superseded versions are immutable, so the row IS
--                      the snapshot and no copy is needed
--   CareException      = incident;  ExceptionTimeline = incident_log
--
-- Tables with no submission behind them, added by the merge because a
-- screen or a story already requires them. Each needs its owner's review.
--
--   vital_sign                                  story C6; family mock "Vitals Recorded"
--   rostering_constraint, rostering_run,
--   rostering_candidate, rostering_candidate_check
--                                               supervisor screens 1b and 4c; stories B1-B4
--   notification_subscription, notification     UC-FM03, UC-FM05
-- =====================================================================
