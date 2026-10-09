package sg.nus.carelink.incident.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * A home service spot check (UC-MG08): a manager asks to watch one of an elder's visits, the
 * family consents or declines, and on the day the manager records a conclusion - or that the
 * caregiver did not turn up, or that the elder was out and the check moves to another visit.
 *
 * <p>The family's consent is the gate the use case insists on: "抽查必须经家属同意，机构不得单方面
 * 上门". Nothing is recorded on site until they have approved, and approving is only possible
 * before the visit starts.
 *
 * <pre>
 *   AWAITING_FAMILY --approved--> SCHEDULED --conclusion--> COMPLETED
 *         |                          |---caregiver absent--> CAREGIVER_NO_SHOW (incident raised)
 *         |                          |---elder absent------> AWAITING_FAMILY (another visit, asked again)
 *         |--declined--> DECLINED    |
 *         '--withdrawn-------------- '--withdrawn----------> WITHDRAWN
 * </pre>
 *
 * <p>A record: every step returns a new instance, and a step that is not allowed throws
 * {@link BusinessRuleViolation}, HTTP 409.
 *
 * @param result the conclusion, once COMPLETED
 * @param outcome how the request ended; null while open, and for a declined one
 * @param closingReason why the family declined, or why the manager withdrew
 * @param incidentId the missed-visit incident when the caregiver did not turn up
 */
public record SpotCheck(
		Long id,
		Long elderId,
		Long caregiverId,
		Long visitId,
		Long raisedByUserId,
		Long approvingFamilyMemberId,
		LocalDateTime proposedTime,
		String reason,
		SpotCheck.ApprovalStatus approvalStatus,
		LocalDateTime decidedAt,
		String finding,
		String caregiverResponse,
		LocalDateTime checkedAt,
		LocalDateTime createdAt,
		SpotCheck.Result result,
		SpotCheck.Outcome outcome,
		String closingReason,
		Long incidentId) {

	/** The widths of reason and closing_reason, and of finding and caregiver_response. */
	public static final int SHORT_TEXT = 255;
	public static final int LONG_TEXT = 500;

	/**
	 * Step 1: a manager asks to watch this visit, and says why. The caregiver is whoever has the
	 * visit; the time is the visit's.
	 */
	public static SpotCheck requested(Long elderId, Long visitId, Long caregiverId, LocalDateTime visitStart,
			String purpose, Long managerUserId, LocalDateTime now) {
		Objects.requireNonNull(elderId, "elderId");
		Objects.requireNonNull(visitId, "visitId");
		Objects.requireNonNull(visitStart, "visitStart");
		if (caregiverId == null) {
			throw new BusinessRuleViolation("SPOT_CHECK_NEEDS_A_CAREGIVER",
					"A spot check watches a caregiver at work; this visit has nobody on it yet");
		}
		if (!visitStart.isAfter(now)) {
			throw new BusinessRuleViolation("SPOT_CHECK_IN_THE_PAST",
					"Only a visit that has not started yet can be spot-checked");
		}
		return new SpotCheck(null, elderId, caregiverId, visitId, managerUserId, null, visitStart,
				required(purpose, "SPOT_CHECK_PURPOSE_REQUIRED", "Say why the visit is being checked", SHORT_TEXT),
				ApprovalStatus.PENDING_APPROVAL, null, null, null, null, null, null, null, null, null);
	}

	/** Step 3: the family agrees. Too late once the visit has started. */
	public SpotCheck approvedBy(Long familyMemberId, LocalDateTime now) {
		requireAwaitingFamily("approved");
		requireNotStarted(now);
		return withConsent(ApprovalStatus.APPROVED, familyMemberId, now, null);
	}

	/** Alternative 3a: the family declines and says why. The request closes; normal service goes on. */
	public SpotCheck declinedBy(Long familyMemberId, String why, LocalDateTime now) {
		requireAwaitingFamily("declined");
		return withConsent(ApprovalStatus.REJECTED, familyMemberId, now,
				required(why, "SPOT_CHECK_REASON_REQUIRED", "Say why the spot check is declined", SHORT_TEXT));
	}

	/** Steps 4 and 5: the conclusion recorded on site, with notes. */
	public SpotCheck concluded(Result how, String notes, LocalDateTime now) {
		Objects.requireNonNull(how, "result");
		requireScheduled("concluded");
		return new SpotCheck(id, elderId, caregiverId, visitId, raisedByUserId, approvingFamilyMemberId, proposedTime,
				reason, approvalStatus, decidedAt, optional(notes, LONG_TEXT), caregiverResponse, now, createdAt, how,
				Outcome.COMPLETED, closingReason, incidentId);
	}

	/**
	 * Exception 4a: the caregiver did not turn up. The check closes and the missed visit goes to
	 * the exception queue as an incident (UC-MG05).
	 */
	public SpotCheck caregiverDidNotTurnUp(Long missedVisitIncidentId, String notes, LocalDateTime now) {
		Objects.requireNonNull(missedVisitIncidentId, "missedVisitIncidentId");
		requireScheduled("closed as a no-show");
		return new SpotCheck(id, elderId, caregiverId, visitId, raisedByUserId, approvingFamilyMemberId, proposedTime,
				reason, approvalStatus, decidedAt, optional(notes, LONG_TEXT), caregiverResponse, now, createdAt, null,
				Outcome.CAREGIVER_NO_SHOW, closingReason, missedVisitIncidentId);
	}

	/**
	 * Exception 4b: the elder was out, so the check moves to another of their visits. Not held
	 * against the caregiver; the family is asked again, because they agreed to a time.
	 */
	public SpotCheck movedTo(Long newVisitId, Long newCaregiverId, LocalDateTime newVisitStart, LocalDateTime now) {
		requireOpen("moved");
		SpotCheck moved = requested(elderId, newVisitId, newCaregiverId, newVisitStart, reason, raisedByUserId, now);
		return new SpotCheck(id, moved.elderId, moved.caregiverId, moved.visitId, raisedByUserId, null,
				moved.proposedTime, reason, ApprovalStatus.PENDING_APPROVAL, null, null, null, null, createdAt, null, null,
				null, null);
	}

	/** The manager calls the request off and says why. */
	public SpotCheck withdrawn(String why, LocalDateTime now) {
		requireOpen("withdrawn");
		return new SpotCheck(id, elderId, caregiverId, visitId, raisedByUserId, approvingFamilyMemberId, proposedTime,
				reason, approvalStatus, decidedAt, finding, caregiverResponse, now, createdAt, null, Outcome.WITHDRAWN,
				required(why, "SPOT_CHECK_REASON_REQUIRED", "Say why the spot check is withdrawn", SHORT_TEXT), incidentId);
	}

	/** The checked caregiver answers the conclusion; it is theirs to see, unlike an elder's rating. */
	public SpotCheck respondedBy(Long respondingCaregiverId, String response) {
		if (stage() != Stage.COMPLETED) {
			throw new BusinessRuleViolation("SPOT_CHECK_NOT_CONCLUDED", "Only a concluded spot check can be answered");
		}
		if (!Objects.equals(respondingCaregiverId, caregiverId)) {
			throw new BusinessRuleViolation("SPOT_CHECK_NOT_YOURS", "Only the caregiver who was checked can answer it");
		}
		return new SpotCheck(id, elderId, caregiverId, visitId, raisedByUserId, approvingFamilyMemberId, proposedTime,
				reason, approvalStatus, decidedAt, finding,
				required(response, "SPOT_CHECK_RESPONSE_REQUIRED", "Write a response", LONG_TEXT), checkedAt, createdAt,
				result, outcome, closingReason, incidentId);
	}

	/** Where the request stands, read from consent and outcome together. */
	public Stage stage() {
		if (outcome != null) {
			return switch (outcome) {
				case COMPLETED -> Stage.COMPLETED;
				case CAREGIVER_NO_SHOW -> Stage.CAREGIVER_NO_SHOW;
				case WITHDRAWN -> Stage.WITHDRAWN;
			};
		}
		return switch (approvalStatus) {
			case PENDING_APPROVAL -> Stage.AWAITING_FAMILY;
			case APPROVED -> Stage.SCHEDULED;
			case REJECTED -> Stage.DECLINED;
		};
	}

	public boolean isOpen() {
		Stage stage = stage();
		return stage == Stage.AWAITING_FAMILY || stage == Stage.SCHEDULED;
	}

	/**
	 * Throws unless the family has agreed and nothing has been recorded yet - the only time
	 * anything may be recorded on site. For a caller that has work to do before the step itself.
	 */
	public void ensureScheduled() {
		requireScheduled("recorded on site");
	}

	private void requireAwaitingFamily(String verb) {
		if (stage() != Stage.AWAITING_FAMILY) {
			throw notAllowed(verb);
		}
	}

	private void requireScheduled(String verb) {
		if (stage() != Stage.SCHEDULED) {
			throw stage() == Stage.AWAITING_FAMILY
					? new BusinessRuleViolation("SPOT_CHECK_NOT_APPROVED",
							"The family has not agreed to this spot check yet, so nothing can be recorded on site")
					: notAllowed(verb);
		}
	}

	private void requireOpen(String verb) {
		if (!isOpen()) {
			throw notAllowed(verb);
		}
	}

	private void requireNotStarted(LocalDateTime now) {
		if (!proposedTime.isAfter(now)) {
			throw new BusinessRuleViolation("SPOT_CHECK_TIME_PASSED",
					"The visit has already started; the spot check can be moved to another visit");
		}
	}

	private BusinessRuleViolation notAllowed(String verb) {
		return new BusinessRuleViolation("SPOT_CHECK_NOT_OPEN",
				"Spot check %s cannot be %s: it is %s".formatted(id, verb, stage()));
	}

	private SpotCheck withConsent(ApprovalStatus consent, Long familyMemberId, LocalDateTime now, String why) {
		return new SpotCheck(id, elderId, caregiverId, visitId, raisedByUserId, familyMemberId, proposedTime, reason,
				consent, Objects.requireNonNull(now, "now"), finding, caregiverResponse, checkedAt, createdAt, result,
				outcome, why, incidentId);
	}

	private static String required(String text, String code, String message, int max) {
		if (text == null || text.isBlank()) {
			throw new BusinessRuleViolation(code, message);
		}
		return optional(text, max);
	}

	private static String optional(String text, int max) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String stripped = text.strip();
		return stripped.length() <= max ? stripped : stripped.substring(0, max - 3) + "...";
	}

	public enum ApprovalStatus {
		PENDING_APPROVAL, APPROVED, REJECTED
	}

	/** "达标或待改进". */
	public enum Result {
		MEETS_STANDARD, NEEDS_IMPROVEMENT
	}

	public enum Outcome {
		COMPLETED, CAREGIVER_NO_SHOW, WITHDRAWN
	}

	public enum Stage {
		AWAITING_FAMILY, SCHEDULED, DECLINED, COMPLETED, CAREGIVER_NO_SHOW, WITHDRAWN
	}
}
