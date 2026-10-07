package sg.nus.carelink.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.SpotCheck;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * UC-MG08: a spot check goes nowhere without the family's consent, closes one of four ways,
 * and only the caregiver who was checked may answer its conclusion.
 */
class SpotCheckTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final LocalDateTime VISIT = LocalDateTime.of(2026, 10, 9, 10, 0);

	@Test
	void aRequestWatchesAVisitThatHasNotStartedAndWaitsForTheFamily() {
		SpotCheck asked = asked();

		assertThat(asked.stage()).isEqualTo(SpotCheck.Stage.AWAITING_FAMILY);
		assertThat(asked.isOpen()).isTrue();
		assertThat(asked.caregiverId()).isEqualTo(5L);
		assertThat(asked.proposedTime()).isEqualTo(VISIT);
		assertThat(asked.reason()).isEqualTo("Follow-up on a missed medication");
		assertThat(asked.raisedByUserId()).isEqualTo(11L);
		assertThat(SpotCheck.requested(7L, 30L, 5L, VISIT, "x".repeat(300), 11L, NOW).reason())
				.hasSize(SpotCheck.SHORT_TEXT);
	}

	@Test
	void aRequestNeedsACaregiverAFutureVisitAndAPurpose() {
		assertThatThrownBy(() -> SpotCheck.requested(7L, 30L, null, VISIT, "why", 11L, NOW))
				.extracting("code").isEqualTo("SPOT_CHECK_NEEDS_A_CAREGIVER");
		assertThatThrownBy(() -> SpotCheck.requested(7L, 30L, 5L, NOW, "why", 11L, NOW))
				.extracting("code").isEqualTo("SPOT_CHECK_IN_THE_PAST");
		assertThatThrownBy(() -> SpotCheck.requested(7L, 30L, 5L, VISIT, "  ", 11L, NOW))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("SPOT_CHECK_PURPOSE_REQUIRED");
	}

	@Test
	void theFamilyAgreesBeforeTheVisitOrDeclinesWithAReason() {
		SpotCheck asked = asked();

		SpotCheck approved = asked.approvedBy(21L, NOW);
		assertThat(approved.stage()).isEqualTo(SpotCheck.Stage.SCHEDULED);
		assertThat(approved.approvingFamilyMemberId()).isEqualTo(21L);
		assertThat(approved.decidedAt()).isEqualTo(NOW);
		assertThatThrownBy(() -> approved.approvedBy(21L, NOW)).extracting("code").isEqualTo("SPOT_CHECK_NOT_OPEN");

		SpotCheck declined = asked.declinedBy(21L, "  Mother is unwell this week ", NOW);
		assertThat(declined.stage()).isEqualTo(SpotCheck.Stage.DECLINED);
		assertThat(declined.closingReason()).isEqualTo("Mother is unwell this week");
		assertThat(declined.isOpen()).isFalse();
		assertThatThrownBy(() -> asked.declinedBy(21L, null, NOW)).extracting("code").isEqualTo("SPOT_CHECK_REASON_REQUIRED");
		assertThatThrownBy(() -> asked.approvedBy(21L, VISIT)).extracting("code").isEqualTo("SPOT_CHECK_TIME_PASSED");
	}

	@Test
	void nothingIsRecordedOnSiteUntilTheFamilyHasAgreed() {
		SpotCheck asked = asked();

		assertThatThrownBy(() -> asked.concluded(SpotCheck.Result.MEETS_STANDARD, null, VISIT))
				.extracting("code").isEqualTo("SPOT_CHECK_NOT_APPROVED");
		assertThatThrownBy(asked::ensureScheduled).extracting("code").isEqualTo("SPOT_CHECK_NOT_APPROVED");

		SpotCheck concluded = asked.approvedBy(21L, NOW).concluded(SpotCheck.Result.NEEDS_IMPROVEMENT, " Gloves not worn ", VISIT);
		assertThat(concluded.stage()).isEqualTo(SpotCheck.Stage.COMPLETED);
		assertThat(concluded.result()).isEqualTo(SpotCheck.Result.NEEDS_IMPROVEMENT);
		assertThat(concluded.finding()).isEqualTo("Gloves not worn");
		assertThat(concluded.checkedAt()).isEqualTo(VISIT);
		assertThatThrownBy(concluded::ensureScheduled).extracting("code").isEqualTo("SPOT_CHECK_NOT_OPEN");
	}

	@Test
	void aCaregiverWhoDidNotComeClosesTheCheckWithAnIncident() {
		SpotCheck scheduled = asked().approvedBy(21L, NOW);

		SpotCheck noShow = scheduled.caregiverDidNotTurnUp(900L, "", VISIT);
		assertThat(noShow.stage()).isEqualTo(SpotCheck.Stage.CAREGIVER_NO_SHOW);
		assertThat(noShow.incidentId()).isEqualTo(900L);
		assertThat(noShow.finding()).isNull();
		assertThat(noShow.result()).isNull();
		assertThatThrownBy(() -> noShow.caregiverDidNotTurnUp(901L, null, VISIT))
				.extracting("code").isEqualTo("SPOT_CHECK_NOT_OPEN");
	}

	@Test
	void anElderWhoWasOutMovesTheCheckAndTheFamilyIsAskedAgain() {
		LocalDateTime nextWeek = VISIT.plusDays(7);
		SpotCheck moved = asked().approvedBy(21L, NOW).movedTo(31L, 6L, nextWeek, VISIT);

		assertThat(moved.stage()).isEqualTo(SpotCheck.Stage.AWAITING_FAMILY);
		assertThat(moved.visitId()).isEqualTo(31L);
		assertThat(moved.caregiverId()).isEqualTo(6L);
		assertThat(moved.proposedTime()).isEqualTo(nextWeek);
		assertThat(moved.approvingFamilyMemberId()).isNull();
		assertThat(moved.id()).isEqualTo(100L);
		assertThat(moved.reason()).isEqualTo("Follow-up on a missed medication");

		SpotCheck concluded = asked().approvedBy(21L, NOW).concluded(SpotCheck.Result.MEETS_STANDARD, null, VISIT);
		assertThatThrownBy(() -> concluded.movedTo(31L, 6L, nextWeek, VISIT)).extracting("code").isEqualTo("SPOT_CHECK_NOT_OPEN");
	}

	@Test
	void aManagerWithdrawsAnOpenRequestAndSaysWhy() {
		SpotCheck withdrawn = asked().withdrawn("Caregiver changed elders", NOW);

		assertThat(withdrawn.stage()).isEqualTo(SpotCheck.Stage.WITHDRAWN);
		assertThat(withdrawn.closingReason()).isEqualTo("Caregiver changed elders");
		SpotCheck declined = asked().declinedBy(21L, "no", NOW);
		assertThatThrownBy(() -> declined.withdrawn("late", NOW)).extracting("code").isEqualTo("SPOT_CHECK_NOT_OPEN");
		SpotCheck asked = asked();
		assertThatThrownBy(() -> asked.withdrawn(" ", NOW)).extracting("code").isEqualTo("SPOT_CHECK_REASON_REQUIRED");
	}

	@Test
	void onlyTheCheckedCaregiverAnswersAConclusion() {
		SpotCheck concluded = asked().approvedBy(21L, NOW).concluded(SpotCheck.Result.NEEDS_IMPROVEMENT, "notes", VISIT);

		SpotCheck answered = concluded.respondedBy(5L, "The gloves ran out; I have asked for more.");
		assertThat(answered.caregiverResponse()).isEqualTo("The gloves ran out; I have asked for more.");
		assertThat(answered.stage()).isEqualTo(SpotCheck.Stage.COMPLETED);
		assertThatThrownBy(() -> concluded.respondedBy(6L, "not mine"))
				.extracting("code").isEqualTo("SPOT_CHECK_NOT_YOURS");
		assertThatThrownBy(() -> concluded.respondedBy(5L, ""))
				.extracting("code").isEqualTo("SPOT_CHECK_RESPONSE_REQUIRED");
		SpotCheck asked = asked();
		assertThatThrownBy(() -> asked.respondedBy(5L, "early"))
				.extracting("code").isEqualTo("SPOT_CHECK_NOT_CONCLUDED");
	}

	private static SpotCheck asked() {
		SpotCheck asked = SpotCheck.requested(7L, 30L, 5L, VISIT, " Follow-up on a missed medication ", 11L, NOW);
		return new SpotCheck(100L, asked.elderId(), asked.caregiverId(), asked.visitId(), asked.raisedByUserId(),
				asked.approvingFamilyMemberId(), asked.proposedTime(), asked.reason(), asked.approvalStatus(),
				asked.decidedAt(), asked.finding(), asked.caregiverResponse(), asked.checkedAt(), null, asked.result(),
				asked.outcome(), asked.closingReason(), asked.incidentId());
	}
}
