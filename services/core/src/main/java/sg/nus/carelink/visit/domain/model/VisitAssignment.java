package sg.nus.carelink.visit.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Who was put on a visit, by whom, why, and until when (visit_assignment). The history of a
 * visit's caregivers: a change of caregiver ends one row and starts another, so both are kept
 * (UC-MG04 exception 5a, "两次指派记录均保留").
 *
 * <p>Visits rostered before anybody wrote these rows have none; the first change records the
 * caregiver it takes the visit from as well, so the history starts with them.
 *
 * @param rosteringCandidateId the suggestion this assignment came from, when it came from a search
 */
public record VisitAssignment(
		Long id,
		Long visitId,
		Long caregiverId,
		Long assignedByUserId,
		VisitAssignment.Status status,
		String reason,
		LocalDateTime assignedAt,
		LocalDateTime endedAt,
		Long rosteringCandidateId) {

	/** The width of visit_assignment.reason. */
	public static final int REASON_LENGTH = 255;

	public VisitAssignment {
		reason = reason == null || reason.length() <= REASON_LENGTH
				? reason
				: reason.substring(0, REASON_LENGTH - 3) + "...";
	}

	/** A caregiver is now on the visit. */
	public static VisitAssignment active(Long visitId, Long caregiverId, Long byUserId, String reason,
			Long rosteringCandidateId, LocalDateTime now) {
		return new VisitAssignment(null, Objects.requireNonNull(visitId, "visitId"),
				Objects.requireNonNull(caregiverId, "caregiverId"), byUserId, Status.ACTIVE, reason,
				Objects.requireNonNull(now, "now"), null, rosteringCandidateId);
	}

	/**
	 * The record of a caregiver who held the visit before any assignment row existed, already
	 * ended: how a visit rostered by the plan gets a history the moment it first changes hands.
	 */
	public static VisitAssignment earlier(Long visitId, Long caregiverId, Status how, String reason,
			LocalDateTime assignedAt, LocalDateTime now) {
		return new VisitAssignment(null, visitId, caregiverId, null, how, reason,
				assignedAt == null ? now : assignedAt, now, null);
	}

	/** Somebody else has the visit now. */
	public VisitAssignment replaced(String why, LocalDateTime now) {
		return ended(Status.REPLACED, why, now);
	}

	/** Nobody has the visit now: it was called off, or left uncovered. */
	public VisitAssignment cancelled(String why, LocalDateTime now) {
		return ended(Status.CANCELLED, why, now);
	}

	private VisitAssignment ended(Status how, String why, LocalDateTime now) {
		if (status != Status.ACTIVE) {
			throw new IllegalStateException("Assignment " + id + " has already ended as " + status);
		}
		String reasonNow = reason == null ? why : reason + "; ended: " + why;
		return new VisitAssignment(id, visitId, caregiverId, assignedByUserId, how, reasonNow, assignedAt,
				Objects.requireNonNull(now, "now"), rosteringCandidateId);
	}

	public enum Status {
		ACTIVE, REPLACED, CANCELLED
	}
}
