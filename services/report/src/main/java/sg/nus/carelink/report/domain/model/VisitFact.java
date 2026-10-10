package sg.nus.carelink.report.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * One visit in the period, as the report needs to know it.
 *
 * <p>Not the visit module's {@code Visit}: the report module may not depend on another
 * module's classes (the slice rule in {@code LayerDependencyTest}), and it would not want to.
 * This is a reading of the visit table taken at generation time - who went, when, how it
 * ended and what proof it left - and nothing that could change a visit.
 *
 * @param caregiverId           null while nobody is assigned
 * @param caregiverName         the caregiver's full name; which readers see it is decided by the
 *                              assembler for their audience, not here
 * @param evidenceCount         items of evidence captured on the visit
 * @param verifiedEvidenceCount how many of those have been verified
 * @param skippedByFamily       cancelled because the family chose to skip it while its caregiver
 *                              was away (UC-MG04 alternative 4c): not a missed visit, but still
 *                              one the period's record has to show
 * @param plannedMinutes        scheduled length, start to end; null when no end was scheduled
 * @param workedMinutes         time on site, check-in to check-out; null until both happened
 */
public record VisitFact(
		Long id,
		Long caregiverId,
		String caregiverName,
		String serviceType,
		LocalDateTime scheduledStart,
		VisitFact.Status status,
		int evidenceCount,
		int verifiedEvidenceCount,
		boolean skippedByFamily,
		Integer plannedMinutes,
		Integer workedMinutes) {

	public VisitFact {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(scheduledStart, "scheduledStart");
		Objects.requireNonNull(status, "status");
	}

	/** A visit without its durations: how visits were read before the report counted hours. */
	public VisitFact(Long id, Long caregiverId, String caregiverName, String serviceType, LocalDateTime scheduledStart,
			VisitFact.Status status, int evidenceCount, int verifiedEvidenceCount, boolean skippedByFamily) {
		this(id, caregiverId, caregiverName, serviceType, scheduledStart, status, evidenceCount, verifiedEvidenceCount,
				skippedByFamily, null, null);
	}

	/** A visit no family chose to skip, which is nearly every visit. */
	public VisitFact(Long id, Long caregiverId, String caregiverName, String serviceType, LocalDateTime scheduledStart,
			VisitFact.Status status, int evidenceCount, int verifiedEvidenceCount) {
		this(id, caregiverId, caregiverName, serviceType, scheduledStart, status, evidenceCount, verifiedEvidenceCount,
				false);
	}

	/** Cancelled at the family's request: counted in the period, never as a missed visit. */
	public boolean cancelledByFamily() {
		return status == Status.CANCELLED && skippedByFamily;
	}

	/**
	 * Whether this visit's record is final.
	 *
	 * <p>UC-MG07 exception 2a marks a report incomplete when the period holds a visit that is
	 * not yet written off ("未核销"). The state machine UC-CG03 and UC-CG05 share runs
	 * 已排班 → 已到场 → 服务中 → 已完成 → 已核销, and CG05 is explicit that a visit can stop at
	 * 已完成 without reaching 已核销 - the caregiver has checked out, the elder has not
	 * confirmed. So COMPLETED counts as not closed, alongside the three states before it.
	 *
	 * <p>The four closed states are the ones nothing more is expected to happen to: verified,
	 * closed because the elder never answered (DECISION 16 in V2), ended in an exception, or
	 * cancelled. Every state is listed so that a ninth one added to the table cannot slip
	 * through as either without somebody deciding which.
	 */
	public boolean isClosed() {
		return switch (status) {
			case SCHEDULED, ARRIVED, IN_PROGRESS, COMPLETED -> false;
			case VERIFIED, AUTO_CLOSED, EXCEPTION, CANCELLED -> true;
		};
	}

	/**
	 * Whether the visit counts as planned for the period: every visit except a cancelled one.
	 * A cancellation - at the family's request or otherwise - takes the visit out of what was
	 * meant to happen, so it never counts against the period's fulfilment.
	 */
	public boolean isPlanned() {
		return status != Status.CANCELLED;
	}

	/**
	 * Whether the service was carried out: the caregiver checked out, whether or not the elder
	 * has confirmed since (COMPLETED, VERIFIED, or AUTO_CLOSED when the elder never answered).
	 * A visit that ended in an exception was not.
	 */
	public boolean isCompleted() {
		return switch (status) {
			case COMPLETED, VERIFIED, AUTO_CLOSED -> true;
			case SCHEDULED, ARRIVED, IN_PROGRESS, EXCEPTION, CANCELLED -> false;
		};
	}

	public boolean hasCaregiver() {
		return caregiverId != null;
	}

	/** The visit table's own states, restated here because the visit module's enum is not ours to import. */
	public enum Status {
		SCHEDULED, ARRIVED, IN_PROGRESS, COMPLETED, VERIFIED, AUTO_CLOSED, EXCEPTION, CANCELLED
	}
}
