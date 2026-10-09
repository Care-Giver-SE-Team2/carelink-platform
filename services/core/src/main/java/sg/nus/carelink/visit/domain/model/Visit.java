package sg.nus.carelink.visit.domain.model;

import java.time.LocalDateTime;

/**
 * Domain model for visit.
 *
 * <p>Generated starting point: the same fields as the table, and nothing else. This is
 * where the business rules and the design patterns go — reshape it into a proper
 * aggregate (add behaviour, fold child tables in, drop columns the domain does not
 * care about). identity.domain.model.AppUser is the template. Must not import JPA or
 * Spring Data; ArchUnit rejects the build if it does.
 */
public record Visit(
		Long id,
		Long elderId,
		Long caregiverId,
		Long carePlanNodeId,
		Long absenceId,
		String serviceType,
		LocalDateTime scheduledStart,
		LocalDateTime scheduledEnd,
		LocalDateTime checkedInAt,
		LocalDateTime checkedOutAt,
		Visit.Status status,
		LocalDateTime stateDeadline,
		Long carePlanId,
		Integer version,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		@com.fasterxml.jackson.annotation.JsonIgnore HealthObservation.Flag healthFlag,
		@com.fasterxml.jackson.annotation.JsonIgnore String healthNote) {

    public Visit(Long id, Long elderId, Long caregiverId, Long carePlanNodeId, Long absenceId, String serviceType,
            LocalDateTime scheduledStart, LocalDateTime scheduledEnd, LocalDateTime checkedInAt, LocalDateTime checkedOutAt,
            Status status, LocalDateTime stateDeadline, Long carePlanId, Integer version, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this(id, elderId, caregiverId, carePlanNodeId, absenceId, serviceType, scheduledStart, scheduledEnd,
                checkedInAt, checkedOutAt, status, stateDeadline, carePlanId, version, createdAt, updatedAt, null, null);
    }

    public Visit observedHealth(HealthMeasurement measurement) {
        if (status != Status.IN_PROGRESS || checkedInAt == null) {
            throw new sg.nus.carelink.shared.error.BusinessRuleViolation("VISIT_HEALTH_NOT_ALLOWED", "Check in before recording health observations in an active visit.");
        }
        return new Visit(id, elderId, caregiverId, carePlanNodeId, absenceId, serviceType, scheduledStart, scheduledEnd,
                checkedInAt, checkedOutAt, status, stateDeadline, carePlanId, version, createdAt, updatedAt,
                measurement.healthFlag(), measurement.healthNote());
    }

	/**
	 * A new visit generated from a care plan task (UC-MG03): SCHEDULED, not yet versioned, and
	 * with no state deadline. SYS03 derives its boundary from scheduledStart plus the late threshold.
	 * {@code caregiverId} may be null, leaving the visit to be covered.
	 */
	public static Visit scheduled(Long elderId, Long caregiverId, Long carePlanId, Long carePlanNodeId,
			String serviceType, LocalDateTime start, LocalDateTime end) {
		java.util.Objects.requireNonNull(elderId, "elderId");
		java.util.Objects.requireNonNull(start, "start");
		if (end != null && !end.isAfter(start)) {
			throw new IllegalArgumentException("A visit must end after it starts: " + start + " to " + end);
		}
		return new Visit(null, elderId, caregiverId, carePlanNodeId, null, serviceType, start, end,
				null, null, Status.SCHEDULED, null, carePlanId, null, null, null);
	}

	/**
	 * True while nobody has started on the visit, so it may still be reassigned or called off.
	 * Not named isX: controllers serialise this record as-is, and Jackson would add an isX()
	 * method to the JSON as a property.
	 */
	public boolean hasNotStarted() {
		return status == Status.SCHEDULED;
	}

	/**
	 * True for a visit no care plan produced, such as the work order of an approved extra
	 * service: it has one task, the service itself, rather than a plan task. Not named isX, for
	 * the reason given on {@link #hasNotStarted}.
	 */
	public boolean standalone() {
		return carePlanId == null && carePlanNodeId == null;
	}

	/** The one task a standalone visit carries out: its service, by name. */
	public VisitTask standaloneTask() {
		if (!standalone()) {
			throw new IllegalStateException("Visit " + id + " follows a care plan; its tasks come from the plan");
		}
		String name = serviceType == null || serviceType.isBlank() ? "Visit" : serviceType.strip();
		return new VisitTask(null, id, null, name.length() > 150 ? name.substring(0, 150) : name,
				VisitTask.Status.PENDING, null, null, null);
	}

	/** Gives an unassigned, untouched visit to a caregiver. */
	public Visit coveredBy(Long newCaregiverId) {
		if (!hasNotStarted() || caregiverId != null) {
			throw new IllegalStateException("Visit " + id + " is already " + (caregiverId != null ? "assigned" : status));
		}
		return withAssignmentAndStatus(newCaregiverId, status);
	}

	/** Calls off a visit nobody has started, e.g. because its care plan changed or stopped. */
	public Visit cancelled() {
		if (!hasNotStarted()) {
			throw new IllegalStateException("Visit " + id + " is " + status + " and can no longer be cancelled");
		}
		return withAssignmentAndStatus(caregiverId, Status.CANCELLED);
	}

	/**
	 * Turns a visit that reached its start time with nobody assigned into an exception: nobody
	 * can check in to it, so it needs a manager now, not a caregiver later.
	 */
	public Visit uncoveredAtStart() {
		if (!hasNotStarted() || caregiverId != null) {
			throw new IllegalStateException("Visit " + id + " is " + (caregiverId != null ? "assigned" : status));
		}
		return withAssignmentAndStatus(null, Status.EXCEPTION);
	}

	/**
	 * True for a visit an absence left with nobody on it (UC-MG04 exception 3a): the one kind of
	 * exception a replacement can still fix, as long as nobody has checked in. Not named isX, for
	 * the reason given on {@link #hasNotStarted}.
	 */
	public boolean leftUncoveredByAbsence() {
		return status == Status.EXCEPTION && caregiverId == null && absenceId != null && checkedInAt == null;
	}

	/**
	 * UC-MG04: gives the visit to another caregiver because its own is absent. Also takes back a
	 * visit an absence left uncovered, once somebody is free to do it.
	 */
	public Visit reassignedForAbsence(Long newCaregiverId, Long forAbsenceId) {
		java.util.Objects.requireNonNull(newCaregiverId, "newCaregiverId");
		requireOpenToAbsenceChange("reassigned");
		return withAbsence(newCaregiverId, Status.SCHEDULED, forAbsenceId);
	}

	/** UC-MG04 exception 3a: nobody can take the visit, so it becomes an exception rather than a gap. */
	public Visit uncoveredForAbsence(Long forAbsenceId) {
		if (!hasNotStarted()) {
			throw new IllegalStateException("Visit " + id + " is " + status + " and can no longer be uncovered");
		}
		return withAbsence(null, Status.EXCEPTION, forAbsenceId);
	}

	/** UC-MG04 alternatives 4b and 4c: called off because of an absence, to be skipped or moved. */
	public Visit calledOffForAbsence(Long forAbsenceId) {
		requireOpenToAbsenceChange("called off");
		return withAbsence(caregiverId, Status.CANCELLED, forAbsenceId);
	}

	/**
	 * UC-MG04 alternative 4b: the same visit at another time, as a new visit for whoever covers it
	 * then - same elder, plan, task and length. This visit is called off on its own.
	 */
	public Visit movedTo(LocalDateTime newStart, Long newCaregiverId, Long forAbsenceId) {
		java.util.Objects.requireNonNull(newStart, "newStart");
		LocalDateTime newEnd = scheduledEnd == null
				? null
				: newStart.plus(java.time.Duration.between(scheduledStart.atZone(WALL_CLOCK),
						scheduledEnd.atZone(WALL_CLOCK)));
		return new Visit(null, elderId, newCaregiverId, carePlanNodeId, forAbsenceId, serviceType, newStart, newEnd,
				null, null, Status.SCHEDULED, null, carePlanId, null, null, null);
	}

	/** The zone visit times are wall-clock times in; a moved visit keeps its length as a clock there shows it. */
	private static final java.time.ZoneId WALL_CLOCK = java.time.ZoneId.of("Asia/Singapore");

	private void requireOpenToAbsenceChange(String verb) {
		if (!hasNotStarted() && !leftUncoveredByAbsence()) {
			throw new IllegalStateException("Visit " + id + " is " + status + " and can no longer be " + verb);
		}
	}

	private Visit withAbsence(Long newCaregiverId, Status newStatus, Long forAbsenceId) {
		return new Visit(id, elderId, newCaregiverId, carePlanNodeId, forAbsenceId, serviceType, scheduledStart,
				scheduledEnd, checkedInAt, checkedOutAt, newStatus, stateDeadline, carePlanId, version,
				createdAt, updatedAt, healthFlag, healthNote);
	}

	private Visit withAssignmentAndStatus(Long newCaregiverId, Status newStatus) {
		return new Visit(id, elderId, newCaregiverId, carePlanNodeId, absenceId, serviceType, scheduledStart,
				scheduledEnd, checkedInAt, checkedOutAt, newStatus, stateDeadline, carePlanId, version,
				createdAt, updatedAt, healthFlag, healthNote);
	}

	public enum Status {
		SCHEDULED, ARRIVED, IN_PROGRESS, COMPLETED, VERIFIED, AUTO_CLOSED, EXCEPTION, CANCELLED
	}

    public Visit arrivedAt(LocalDateTime now) {
        if (status != Status.SCHEDULED) throw new sg.nus.carelink.shared.error.BusinessRuleViolation("VISIT_EXECUTION_NOT_ALLOWED", "Visit is not scheduled.");
        return new Visit(id, elderId, caregiverId, carePlanNodeId, absenceId, serviceType, scheduledStart, scheduledEnd,
                now, checkedOutAt, Status.ARRIVED, null, carePlanId, version, createdAt, updatedAt, healthFlag, healthNote);
    }
    public Visit started() {
        if (status != Status.ARRIVED) throw new sg.nus.carelink.shared.error.BusinessRuleViolation("VISIT_EXECUTION_NOT_ALLOWED", "Visit has not arrived.");
        return withAssignmentAndStatus(caregiverId, Status.IN_PROGRESS);
    }
    /** CG04: operational reports pause open work, never undo a completed visit. */
    public Visit reportedException(LocalDateTime now) {
        if (status == Status.CANCELLED || (status == Status.SCHEDULED && now.isBefore(scheduledStart))) {
            throw new sg.nus.carelink.shared.error.BusinessRuleViolation("VISIT_REPORT_NOT_ALLOWED", "This visit cannot currently receive a new report.");
        }
        return switch (status) {
            case SCHEDULED, ARRIVED, IN_PROGRESS -> withAssignmentAndStatus(caregiverId, Status.EXCEPTION);
            default -> this;
        };
    }
}
