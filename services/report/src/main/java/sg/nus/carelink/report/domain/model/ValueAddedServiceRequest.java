package sg.nus.carelink.report.domain.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * One value-added service request shared by UC-EL02 and UC-FM08.
 * The elder creates the row; a bound family member later approves or rejects the same row.
 */
public record ValueAddedServiceRequest(
        Long id,
        Long elderId,
        Long valueAddedServiceId,
        Long requestedByFamilyMemberId,
        Long approvingFamilyMemberId,
        Long visitId,
        LocalDateTime requestedSchedule,
        String specialInstructions,
        Status status,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** How far ahead a service must be asked for, so there is time to answer and staff it. */
    public static final Duration MIN_NOTICE = Duration.ofHours(2);

    /** How long before the requested time the family must have answered; after that it lapses. */
    public static final Duration ANSWER_CUTOFF = Duration.ofMinutes(30);

    public ValueAddedServiceRequest {
        Objects.requireNonNull(elderId, "elderId");
        Objects.requireNonNull(valueAddedServiceId, "valueAddedServiceId");
        Objects.requireNonNull(status, "status");
    }

    /** How far ahead a service can be booked; past this the care team cannot promise staff. */
    public static final Duration MAX_ADVANCE = Duration.ofDays(90);

    /** Visits run within these hours, Singapore time: starting no earlier, finishing no later. */
    public static final LocalTime DAY_START = LocalTime.of(8, 0);
    public static final LocalTime DAY_END = LocalTime.of(20, 0);

    /** Start times fall on the hour or the half hour. */
    public static final int STEP_MINUTES = 30;

    /**
     * A new request must start at least {@link #MIN_NOTICE} and at most {@link #MAX_ADVANCE} from
     * now, on a {@link #STEP_MINUTES} step, and fit between {@link #DAY_START} and {@link #DAY_END}
     * for the service's whole length.
     */
    public static void requireBookableTime(LocalDateTime requestedSchedule, Duration length, LocalDateTime now) {
        Objects.requireNonNull(requestedSchedule, "requestedSchedule");
        Objects.requireNonNull(length, "length");
        if (requestedSchedule.isBefore(now.plus(MIN_NOTICE))) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_TOO_SOON",
                    "Choose a time at least " + MIN_NOTICE.toHours() + " hours from now.");
        }
        if (requestedSchedule.isAfter(now.plus(MAX_ADVANCE))) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_TOO_FAR",
                    "Choose a time within the next " + MAX_ADVANCE.toDays() + " days.");
        }
        if (requestedSchedule.getMinute() % STEP_MINUTES != 0 || requestedSchedule.getSecond() != 0) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_OFF_STEP",
                    "Choose a time on the hour or half hour.");
        }
        LocalDateTime end = requestedSchedule.plus(length);
        if (requestedSchedule.toLocalTime().isBefore(DAY_START)
                || !end.toLocalDate().equals(requestedSchedule.toLocalDate())
                || end.toLocalTime().isAfter(DAY_END)) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_OUTSIDE_HOURS",
                    "Choose a time between " + DAY_START + " and " + DAY_END + "; this service needs to finish by "
                            + DAY_END + ".");
        }
    }

    /** Whether the family can still answer: until {@link #ANSWER_CUTOFF} before the requested time. */
    public boolean answerableAt(LocalDateTime now) {
        return status == Status.PENDING_APPROVAL && requestedSchedule != null
                && now.isBefore(requestedSchedule.minus(ANSWER_CUTOFF));
    }

    /**
     * UC-FM08 on the elder's behalf: a family member asks for the service themselves. It still
     * starts pending, and is approved by the same family member as it is dispatched.
     */
    public static ValueAddedServiceRequest requestedByFamily(
            Long elderId,
            Long valueAddedServiceId,
            Long familyMemberId,
            LocalDateTime requestedSchedule,
            String specialInstructions) {
        Objects.requireNonNull(familyMemberId, "familyMemberId");
        ValueAddedServiceRequest request = requestedByElder(
                elderId, valueAddedServiceId, requestedSchedule, specialInstructions);
        return new ValueAddedServiceRequest(
                null, elderId, valueAddedServiceId, familyMemberId, null, null,
                request.requestedSchedule(), request.specialInstructions(), Status.PENDING_APPROVAL,
                null, null, null);
    }

    /** UC-EL02: the elder requests an available catalogue service. */
    public static ValueAddedServiceRequest requestedByElder(
            Long elderId,
            Long valueAddedServiceId,
            LocalDateTime requestedSchedule,
            String specialInstructions) {
        Objects.requireNonNull(elderId, "elderId");
        Objects.requireNonNull(valueAddedServiceId, "valueAddedServiceId");
        Objects.requireNonNull(requestedSchedule, "requestedSchedule");
        return new ValueAddedServiceRequest(
                null, elderId, valueAddedServiceId, null, null, null,
                requestedSchedule, normalise(specialInstructions), Status.PENDING_APPROVAL,
                null, null, null);
    }

    /** UC-FM08: approval creates a visit work order, so the request becomes DISPATCHED. */
    public ValueAddedServiceRequest approveAndDispatch(
            Long familyMemberId,
            Long dispatchedVisitId,
            LocalDateTime now) {
        requirePending();
        Objects.requireNonNull(familyMemberId, "familyMemberId");
        Objects.requireNonNull(dispatchedVisitId, "dispatchedVisitId");
        Objects.requireNonNull(now, "now");
        return new ValueAddedServiceRequest(
                id, elderId, valueAddedServiceId, requestedByFamilyMemberId,
                familyMemberId, dispatchedVisitId, requestedSchedule, specialInstructions,
                Status.DISPATCHED, now, createdAt, updatedAt);
    }

    /** UC-FM08: family declines the request without creating a visit. */
    public ValueAddedServiceRequest reject(Long familyMemberId, LocalDateTime now) {
        requirePending();
        Objects.requireNonNull(familyMemberId, "familyMemberId");
        Objects.requireNonNull(now, "now");
        return new ValueAddedServiceRequest(
                id, elderId, valueAddedServiceId, requestedByFamilyMemberId,
                familyMemberId, null, requestedSchedule, specialInstructions,
                Status.REJECTED, now, createdAt, updatedAt);
    }

    /**
     * Called off before it is carried out: by a manager, or because its visit was called off.
     * Only a request still waiting for the family or dispatched and not yet done can be.
     */
    public ValueAddedServiceRequest cancelled() {
        if (status != Status.PENDING_APPROVAL && status != Status.DISPATCHED) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_REQUEST_CLOSED",
                    "Only a pending or dispatched value-added service request can be cancelled.");
        }
        return withStatus(Status.CANCELLED);
    }

    /** Its visit was carried out. */
    public ValueAddedServiceRequest completed() {
        if (status != Status.DISPATCHED) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_REQUEST_NOT_DISPATCHED",
                    "Only a dispatched value-added service request can be completed.");
        }
        return withStatus(Status.COMPLETED);
    }

    private ValueAddedServiceRequest withStatus(Status next) {
        return new ValueAddedServiceRequest(
                id, elderId, valueAddedServiceId, requestedByFamilyMemberId,
                approvingFamilyMemberId, visitId, requestedSchedule, specialInstructions,
                next, decidedAt, createdAt, updatedAt);
    }

    private void requirePending() {
        if (status != Status.PENDING_APPROVAL) {
            throw new BusinessRuleViolation(
                    "VALUE_ADDED_SERVICE_REQUEST_ALREADY_DECIDED",
                    "Only a pending value-added service request can be decided.");
        }
    }

    private static String normalise(String text) {
        if (text == null || text.isBlank()) return null;
        return text.trim();
    }

    public enum Status {
        PENDING_APPROVAL, APPROVED, REJECTED, DISPATCHED, COMPLETED, CANCELLED
    }
}
