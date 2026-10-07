package sg.nus.carelink.incident.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * A care exception: the aggregate root of UC-MG05.
 *
 * <p>An incident reaches the system from a caregiver report, an elder emergency call,
 * an elder service dispute or a missed check-in. From that moment it always has a named
 * responder and a response countdown; if the countdown expires without anyone taking
 * over, responsibility moves to the next level of the escalation chain (UC-SYS02) and
 * the previous responder's record is kept rather than replaced.
 *
 * <p>A record, so every state change returns a new instance and nothing can be mutated
 * behind a caller's back. The five transitions below are the only ones that exist; an
 * illegal one throws {@link BusinessRuleViolation}, which the web layer renders as
 * HTTP 409. Rejected transitions are still written to the timeline by the application
 * layer, because the use case requires that a refused take-over leaves a trace.
 *
 * <p>Plain Java on purpose: no JPA, no Spring, so the whole escalation flow can be
 * exercised in a unit test without a database. ArchUnit fails the build if that changes.
 */
public record Incident(
        Long id,
        Long elderId,
        Long visitId,
        Long reportedByUserId,
        Long responderUserId,
        Incident.Source source,
        Incident.Category category,
        Incident.Severity severity,
        Incident.Status status,
        BigDecimal latitude,
        BigDecimal longitude,
        String locationText,
        String description,
        LocalDateTime respondBy,
        LocalDateTime reportedAt,
        LocalDateTime resolvedAt) {

    public static final ZoneId CARELINK_ZONE =
            ZoneId.of("Asia/Singapore");

    public Incident {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(status, "status");
    }

    /**
     * Creates an emergency incident raised by an elder through the one-tap SOS.
     *
     * <p>An elder SOS is always:
     * source   = ELDER_SOS
     * category = SOS
     * severity = HIGH
     * status   = OPEN
     *
     * <p>It is created without a responder and without a countdown. Both are set by the
     * escalation chain immediately afterwards, through {@link #assignTo}; no incident is
     * allowed to stay unassigned, and routing is not this factory's job.
     *
     * <p>This is the original signature, kept so the elder module's call site does not have
     * to change. It reads the system clock. The overload below takes the moment instead,
     * which is what the manager flow uses: an incident's timestamps and its response
     * deadline have to come from the same clock, or the two disagree - as the integration
     * test showed when both paths read {@code now()} independently.
     */
    public static Incident createElderSos(
            Long elderId,
            Long reportedByUserId,
            BigDecimal latitude,
            BigDecimal longitude,
            String locationText,
            String description) {

        return createElderSos(
                elderId,
                reportedByUserId,
                latitude,
                longitude,
                locationText,
                description,
                LocalDateTime.now(CARELINK_ZONE)
        );
    }

    /** As above, but measured against a clock the caller controls. */
    public static Incident createElderSos(
            Long elderId,
            Long reportedByUserId,
            BigDecimal latitude,
            BigDecimal longitude,
            String locationText,
            String description,
            LocalDateTime now) {

        if (elderId == null) {
            throw new IllegalArgumentException(
                    "elderId must not be null"
            );
        }

        return new Incident(
                null,
                elderId,
                null,
                reportedByUserId,
                null,
                Source.ELDER_SOS,
                Category.SOS,
                Severity.HIGH,
                Status.OPEN,
                latitude,
                longitude,
                locationText,
                description,
                null,
                Objects.requireNonNull(
                        now,
                        "now"
                ),
                null
        );
    }

    /**
     * Creates an incident reported by a caregiver during or after a visit (UC-CG04).
     * Severity and category are the reporter's judgement; the chain is assembled from them.
     */
    public static Incident reportedByCaregiver(
            Long elderId,
            Long visitId,
            Long reportedByUserId,
            Category category,
            Severity severity,
            String description,
            LocalDateTime now) {

        if (elderId == null) {
            throw new IllegalArgumentException(
                    "elderId must not be null"
            );
        }

        return new Incident(
                null,
                elderId,
                visitId,
                reportedByUserId,
                null,
                Source.CAREGIVER,
                category == null
                        ? Category.OTHER
                        : category,
                severity == null
                        ? Severity.MEDIUM
                        : severity,
                Status.OPEN,
                null,
                null,
                null,
                description,
                null,
                Objects.requireNonNull(
                        now,
                        "now"
                ),
                null
        );
    }

    /**
     * UC-MG03:
     * Raised by the system when a visit reaches its start time with nobody assigned. Nobody
     * can check in to it, so it enters the workflow the same way a missed check-in does:
     * source SYSTEM_MISSED_CHECKIN, category SERVICE, severity MEDIUM, reported by nobody.
     */
    public static Incident raisedForUncoveredVisit(
            Long elderId,
            Long visitId,
            String description,
            LocalDateTime now) {

        if (elderId == null) {
            throw new IllegalArgumentException(
                    "elderId must not be null"
            );
        }

        if (visitId == null) {
            throw new IllegalArgumentException(
                    "visitId must not be null"
            );
        }

        return new Incident(
                null,
                elderId,
                visitId,
                null,
                null,
                Source.SYSTEM_MISSED_CHECKIN,
                Category.SERVICE,
                Severity.MEDIUM,
                Status.OPEN,
                null,
                null,
                null,
                description,
                null,
                Objects.requireNonNull(
                        now,
                        "now"
                ),
                null
        );
    }

    /**
     * UC-EL01:
     * Creates an incident when an elder disputes a completed service.
     *
     * <p>The incident is linked to the exact visit being disputed. It enters
     * the existing incident workflow as:
     *
     * <ul>
     *   <li>source = ELDER_SERVICE_DISPUTE</li>
     *   <li>category = SERVICE</li>
     *   <li>severity = MEDIUM</li>
     *   <li>status = OPEN</li>
     * </ul>
     *
     * <p>The escalation service assigns the responder and countdown after
     * this incident has been persisted.
     */
    public static Incident reportedByElderServiceDispute(
            Long elderId,
            Long visitId,
            Long reportedByUserId,
            String description,
            LocalDateTime now) {

        if (elderId == null) {
            throw new IllegalArgumentException(
                    "elderId must not be null"
            );
        }

        if (visitId == null) {
            throw new IllegalArgumentException(
                    "visitId must not be null"
            );
        }

        if (reportedByUserId == null) {
            throw new IllegalArgumentException(
                    "reportedByUserId must not be null"
            );
        }

        return new Incident(
                null,
                elderId,
                visitId,
                reportedByUserId,
                null,
                Source.ELDER_SERVICE_DISPUTE,
                Category.SERVICE,
                Severity.MEDIUM,
                Status.OPEN,
                null,
                null,
                null,
                description,
                null,
                Objects.requireNonNull(
                        now,
                        "now"
                ),
                null
        );
    }

    // ------------------------------------------------------------------ transitions ---

    /**
     * Hands the incident to a named responder and starts that level's countdown.
     *
     * <p>Used both for the first assignment and for every escalation hand-off. It does not
     * erase who was responsible before: that is in the timeline, and responsibility in this
     * use case accumulates rather than transfers.
     */
    public Incident assignTo(
            Long newResponderUserId,
            LocalDateTime respondBy) {

        requireOpenForResponse(
                "assign a responder"
        );

        Objects.requireNonNull(
                newResponderUserId,
                "responderUserId"
        );

        Objects.requireNonNull(
                respondBy,
                "respondBy"
        );

        return copy(
                newResponderUserId,
                severity,
                Status.OPEN,
                respondBy,
                null
        );
    }

    /**
     * A manager takes the incident over. The countdown stops, which is what makes the
     * scheduled scan skip it from here on.
     */
    public Incident claimBy(Long userId) {
        Objects.requireNonNull(
                userId,
                "userId"
        );

        if (status == Status.IN_PROGRESS) {
            throw new BusinessRuleViolation(
                    "INCIDENT_ALREADY_CLAIMED",
                    "This incident has already been taken over by responder "
                            + responderUserId
            );
        }

        requireOpenForResponse(
                "take the incident over"
        );

        return copy(
                userId,
                severity,
                Status.IN_PROGRESS,
                null,
                null
        );
    }

    /**
     * Changes the severity part way through handling. The caller re-assembles the chain for
     * the new severity; the timeline continues rather than a second incident being opened.
     */
    public Incident changeSeverityTo(
            Severity newSeverity) {

        Objects.requireNonNull(
                newSeverity,
                "severity"
        );

        requireOpenForResponse(
                "change the severity"
        );

        if (newSeverity == severity) {
            throw new BusinessRuleViolation(
                    "INCIDENT_SEVERITY_UNCHANGED",
                    "The incident is already at severity "
                            + severity
            );
        }

        return copy(
                responderUserId,
                newSeverity,
                status,
                respondBy,
                null
        );
    }

    /** Closes the incident. Only the responder who took it over may do this. */
    public Incident resolveAt(
            LocalDateTime resolvedAt) {

        Objects.requireNonNull(
                resolvedAt,
                "resolvedAt"
        );

        if (status != Status.IN_PROGRESS) {
            throw new BusinessRuleViolation(
                    "INCIDENT_NOT_CLAIMED",
                    "An incident must be taken over before it can be resolved; it is "
                            + status
            );
        }

        return copy(
                responderUserId,
                severity,
                Status.RESOLVED,
                null,
                resolvedAt
        );
    }

    /**
     * The escalation chain ran out of levels and nobody took the incident over. It is pinned
     * to the top-level view and the family is told; it is never closed automatically.
     */
    public Incident markUnresolvedEscalated() {
        requireOpenForResponse(
                "mark the incident unresolved"
        );

        return copy(
                responderUserId,
                severity,
                Status.UNRESOLVED_ESCALATED,
                null,
                null
        );
    }

    // --------------------------------------------------------------------- questions ---

    /** True while the incident is still waiting for someone to take it over. */
    public boolean awaitingTakeOver() {
        return status == Status.OPEN
                || status == Status.ACKNOWLEDGED;
    }

    /** True when the response countdown has run out and nobody has taken over. */
    public boolean isOverdue(
            LocalDateTime now) {

        return awaitingTakeOver()
                && respondBy != null
                && !now.isBefore(respondBy);
    }

    /** True once the incident can no longer change hands. */
    public boolean isClosed() {
        return status == Status.RESOLVED
                || status
                == Status.UNRESOLVED_ESCALATED;
    }

    public boolean isHandledBy(
            Long userId) {

        return responderUserId != null
                && responderUserId.equals(
                        userId
                );
    }

    // ----------------------------------------------------------------------- helpers ---

    private void requireOpenForResponse(
            String attemptedAction) {

        if (isClosed()) {
            throw new BusinessRuleViolation(
                    "INCIDENT_CLOSED",
                    "Cannot %s: the incident is %s"
                            .formatted(
                                    attemptedAction,
                                    status
                            )
            );
        }
    }

    /**
     * The only five components that ever change after an incident is created. Everything
     * else — who raised it, where, when, about whom — is written once.
     */
    private Incident copy(
            Long newResponder,
            Severity newSeverity,
            Status newStatus,
            LocalDateTime newRespondBy,
            LocalDateTime newResolvedAt) {

        return new Incident(
                id,
                elderId,
                visitId,
                reportedByUserId,
                newResponder,
                source,
                category,
                newSeverity,
                newStatus,
                latitude,
                longitude,
                locationText,
                description,
                newRespondBy,
                reportedAt,
                newResolvedAt
        );
    }

    public enum Source {
        CAREGIVER,
        ELDER_SOS,
        ELDER_SERVICE_DISPUTE,
        SYSTEM_MISSED_CHECKIN
    }

    public enum Category {
        SOS,
        MEDICAL,
        FALL,
        SERVICE,
        OTHER
    }

    public enum Severity {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum Status {
        OPEN,
        ACKNOWLEDGED,
        IN_PROGRESS,
        RESOLVED,
        UNRESOLVED_ESCALATED
    }
}