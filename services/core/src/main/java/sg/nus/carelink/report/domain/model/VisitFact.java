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
 */
public record VisitFact(
		Long id,
		Long caregiverId,
		String caregiverName,
		String serviceType,
		LocalDateTime scheduledStart,
		VisitFact.Status status,
		int evidenceCount,
		int verifiedEvidenceCount) {

	public VisitFact {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(scheduledStart, "scheduledStart");
		Objects.requireNonNull(status, "status");
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

	public boolean hasCaregiver() {
		return caregiverId != null;
	}

	/** The visit table's own states, restated here because the visit module's enum is not ours to import. */
	public enum Status {
		SCHEDULED, ARRIVED, IN_PROGRESS, COMPLETED, VERIFIED, AUTO_CLOSED, EXCEPTION, CANCELLED
	}
}
