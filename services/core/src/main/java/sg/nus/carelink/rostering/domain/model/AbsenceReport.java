package sg.nus.carelink.rostering.domain.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * A caregiver's absence: what UC-CG02 submits and what triggers UC-MG04.
 *
 * <p>There are two ways in. A caregiver asks for leave ahead of time and a manager approves
 * it; or a manager hears by phone that a caregiver is off sick today and records it, and
 * that record is approved the moment it is written, because the manager is the reviewer.
 * Only an approved absence vacates visits.
 *
 * <p>Dates are whole days with both ends included, because that is how leave is asked for.
 * The time an absence blocks therefore runs from midnight on the first day to midnight after
 * the last.
 *
 * <p>{@code coverageConfirmedAt} is UC-MG04 step 7: the manager has looked at every visit
 * the absence vacated and confirmed each one is accounted for.
 */
public record AbsenceReport(
		Long id,
		Long caregiverId,
		Long reviewedByUserId,
		AbsenceReport.Type type,
		LocalDate startDate,
		LocalDate endDate,
		String reason,
		AbsenceReport.Status status,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime coverageConfirmedAt,
		Long coverageConfirmedByUserId) {

	/** UC-CG02: the caregiver asks for leave; it waits for a manager. */
	public static AbsenceReport requested(Long caregiverId, Type type, LocalDate startDate, LocalDate endDate,
			String reason, LocalDate today) {
		return create(caregiverId, type, startDate, endDate, reason, today, Status.PENDING, null);
	}

	/**
	 * UC-MG04 step 1: the manager is told a caregiver will be away and records it, approved at
	 * once - nobody else would review it.
	 */
	public static AbsenceReport recordedByManager(Long caregiverId, Type type, LocalDate startDate,
			LocalDate endDate, String reason, Long managerUserId, LocalDate today) {
		Objects.requireNonNull(managerUserId, "managerUserId");
		return create(caregiverId, type, startDate, endDate, reason, today, Status.APPROVED, managerUserId);
	}

	private static AbsenceReport create(Long caregiverId, Type type, LocalDate startDate, LocalDate endDate,
			String reason, LocalDate today, Status status, Long reviewedBy) {
		Objects.requireNonNull(caregiverId, "caregiverId");
		Objects.requireNonNull(startDate, "startDate");
		Objects.requireNonNull(endDate, "endDate");
		if (endDate.isBefore(startDate)) {
			throw new BusinessRuleViolation("ABSENCE_ENDS_BEFORE_IT_STARTS",
					"An absence cannot end (%s) before it starts (%s)".formatted(endDate, startDate));
		}
		if (endDate.isBefore(today)) {
			throw new BusinessRuleViolation("ABSENCE_ALREADY_OVER",
					"An absence that ended on %s vacates nothing; only current and future absences are recorded"
							.formatted(endDate));
		}
		return new AbsenceReport(null, caregiverId, reviewedBy, type == null ? Type.OTHER : type, startDate, endDate,
				blankToNull(reason), status, null, null, null, null);
	}

	/** A manager accepts a requested absence; from now on it vacates the caregiver's visits. */
	public AbsenceReport approvedBy(Long managerUserId) {
		requirePending("approved");
		return reviewed(Status.APPROVED, managerUserId);
	}

	/** A manager turns a requested absence down; the caregiver's visits stay theirs. */
	public AbsenceReport rejectedBy(Long managerUserId) {
		requirePending("rejected");
		return reviewed(Status.REJECTED, managerUserId);
	}

	/** UC-MG04 step 7: every visit the absence vacated has been looked at by a manager. */
	public AbsenceReport coverageConfirmedBy(Long managerUserId, LocalDateTime now) {
		if (!isApproved()) {
			throw new BusinessRuleViolation("ABSENCE_NOT_APPROVED",
					"Only an approved absence has visits to confirm; this one is " + status);
		}
		return new AbsenceReport(id, caregiverId, reviewedByUserId, type, startDate, endDate, reason, status,
				createdAt, updatedAt, Objects.requireNonNull(now, "now"),
				Objects.requireNonNull(managerUserId, "managerUserId"));
	}

	/**
	 * The confirmation no longer holds: the nightly roster only looks a fortnight ahead, so visits
	 * on the absence's later days can appear after a manager confirmed the earlier ones. The
	 * absence goes back to needing re-rostering and a fresh confirmation.
	 */
	public AbsenceReport coverageReopened() {
		return new AbsenceReport(id, caregiverId, reviewedByUserId, type, startDate, endDate, reason, status,
				createdAt, updatedAt, null, null);
	}

	public boolean isCoverageConfirmed() {
		return coverageConfirmedAt != null;
	}

	public boolean isApproved() {
		return status == Status.APPROVED;
	}

	/** Whether this absence keeps its caregiver away on that day: approved, and the day within it. */
	public boolean keepsAwayOn(LocalDate day) {
		return isApproved() && !day.isBefore(startDate) && !day.isAfter(endDate);
	}

	/** Whether the two absences share at least one day. */
	public boolean overlaps(AbsenceReport other) {
		return !other.endDate.isBefore(startDate) && !other.startDate.isAfter(endDate);
	}

	/** Midnight at the start of the first day away. */
	public LocalDateTime windowStart() {
		return startDate.atStartOfDay();
	}

	/** Midnight after the last day away, exclusive. */
	public LocalDateTime windowEnd() {
		return endDate.plusDays(1).atStartOfDay();
	}

	private void requirePending(String verb) {
		if (status != Status.PENDING) {
			throw new BusinessRuleViolation("ABSENCE_ALREADY_REVIEWED",
					"Only a requested absence can be %s; this one is %s".formatted(verb, status));
		}
	}

	private AbsenceReport reviewed(Status newStatus, Long managerUserId) {
		return new AbsenceReport(id, caregiverId, Objects.requireNonNull(managerUserId, "managerUserId"), type,
				startDate, endDate, reason, newStatus, createdAt, updatedAt, coverageConfirmedAt,
				coverageConfirmedByUserId);
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

	public enum Type {
		SICK, ANNUAL, EMERGENCY, OTHER
	}

	public enum Status {
		PENDING, APPROVED, REJECTED
	}
}
