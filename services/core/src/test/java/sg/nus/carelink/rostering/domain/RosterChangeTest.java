package sg.nus.carelink.rostering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.FamilyResponseWindow;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * UC-MG04: the states a vacated visit's change goes through, and the ones it may not. A family
 * answers only while the change waits for them; the default plan only once their time is up;
 * a manager only takes up a visit nobody could cover.
 */
class RosterChangeTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final VacatedSlot SLOT = new VacatedSlot(30L, 7L, 4L, "Personal care", NOW.plusDays(1),
			NOW.plusDays(1).plusMinutes(45), 5L);

	@Test
	void anOfferWaitsForTheFamilyUntilItsDeadline() {
		RosterChange offered = offered();

		assertThat(offered.status()).isEqualTo(RosterChange.Status.AWAITING_FAMILY);
		assertThat(offered.proposedCaregiverId()).isEqualTo(9L);
		assertThat(offered.originalCaregiverId()).isEqualTo(5L);
		assertThat(offered.visitStart()).isEqualTo(SLOT.start());
		assertThat(offered.awaitingFamily()).isTrue();
		assertThat(offered.familyMayAnswer(NOW.plusMinutes(119))).isTrue();
		assertThat(offered.defaultPlanDue(NOW.plusMinutes(119))).isFalse();
		assertThat(offered.familyMayAnswer(NOW.plusHours(2))).isFalse();
		assertThat(offered.defaultPlanDue(NOW.plusHours(2))).isTrue();
	}

	@Test
	void theFamilyKeepsTheSuggestionAndItIsRecordedAsTheirs() {
		RosterChange settled = offered().replacedBy(9L, 3L, RosterChange.DecidedBy.FAMILY, 41L, "kept", NOW);

		assertThat(settled.status()).isEqualTo(RosterChange.Status.RESOLVED);
		assertThat(settled.outcome()).isEqualTo(RosterChange.Outcome.REPLACED);
		assertThat(settled.decidedBy()).isEqualTo(RosterChange.DecidedBy.FAMILY);
		assertThat(settled.decidedByUserId()).isEqualTo(41L);
		assertThat(settled.assignedCaregiverId()).isEqualTo(9L);
		assertThat(settled.rosteringRunId()).isEqualTo(3L);
		assertThat(settled.decidedAt()).isEqualTo(NOW);
		assertThat(settled.awaitingFamily()).isFalse();
	}

	@Test
	void theDefaultPlanIsToldApartFromAChoice() {
		RosterChange settled = offered().replacedBy(9L, null, RosterChange.DecidedBy.DEFAULT_PLAN, null, "no answer", NOW);

		assertThat(settled.decidedBy()).isEqualTo(RosterChange.DecidedBy.DEFAULT_PLAN);
		assertThat(settled.decidedByUserId()).isNull();
		assertThat(settled.rosteringRunId()).isEqualTo(2L);
		assertThat(settled.note()).isEqualTo("no answer");
	}

	@Test
	void theFamilyMayMoveOrSkipTheVisit() {
		RosterChange moved = offered().rescheduled(31L, 5L, 4L, 41L, "moved", NOW);
		assertThat(moved.outcome()).isEqualTo(RosterChange.Outcome.RESCHEDULED);
		assertThat(moved.rescheduledVisitId()).isEqualTo(31L);
		assertThat(moved.assignedCaregiverId()).isEqualTo(5L);

		RosterChange skipped = offered().skipped(41L, "skipped", NOW);
		assertThat(skipped.outcome()).isEqualTo(RosterChange.Outcome.SKIPPED);
		assertThat(skipped.decidedBy()).isEqualTo(RosterChange.DecidedBy.FAMILY);
		assertThat(skipped.assignedCaregiverId()).isNull();
	}

	@Test
	void aSettledChangeCannotBeSettledAgain() {
		RosterChange settled = offered().skipped(41L, "skipped", NOW);

		assertThatThrownBy(() -> settled.replacedBy(9L, 2L, RosterChange.DecidedBy.DEFAULT_PLAN, null, "late", NOW))
				.isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("already settled (SKIPPED by FAMILY)")
				.extracting("code").isEqualTo("ROSTER_CHANGE_NOT_OPEN");
		assertThatThrownBy(() -> settled.rescheduled(31L, 5L, 2L, 41L, "again", NOW))
				.isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> settled.skipped(41L, "again", NOW)).isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> settled.withdrawn("gone", NOW)).isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void nobodyFreeLeavesTheVisitUncoveredWithAnIncident() {
		RosterChange uncovered = saved(RosterChange.uncovered(12L, SLOT, 2L, 77L, NOW));

		assertThat(uncovered.status()).isEqualTo(RosterChange.Status.UNCOVERED);
		assertThat(uncovered.isUncovered()).isTrue();
		assertThat(uncovered.incidentId()).isEqualTo(77L);
		assertThat(uncovered.proposedCaregiverId()).isNull();
		assertThat(uncovered.defaultPlanDue(NOW.plusDays(9))).isFalse();
		assertThatThrownBy(() -> uncovered.skipped(41L, "skip", NOW)).isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> uncovered.replacedBy(9L, 2L, RosterChange.DecidedBy.FAMILY, 41L, "pick", NOW))
				.isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void aManagerTakesUpAnUncoveredVisitOnceSomebodyIsFree() {
		RosterChange uncovered = saved(RosterChange.uncovered(12L, SLOT, 2L, 77L, NOW));
		RosterChange searched = uncovered.searchedAgain(3L, NOW.plusHours(1));
		assertThat(searched.status()).isEqualTo(RosterChange.Status.UNCOVERED);
		assertThat(searched.rosteringRunId()).isEqualTo(3L);
		assertThat(searched.updatedAt()).isEqualTo(NOW.plusHours(1));

		RosterChange settled = searched.replacedBy(9L, 4L, RosterChange.DecidedBy.MANAGER, 11L, "found", NOW);
		assertThat(settled.outcome()).isEqualTo(RosterChange.Outcome.REPLACED);
		assertThat(settled.incidentId()).isEqualTo(77L);

		RosterChange stillOffered = offered();
		assertThatThrownBy(() -> stillOffered.replacedBy(9L, 4L, RosterChange.DecidedBy.MANAGER, 11L, "x", NOW))
				.isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> stillOffered.searchedAgain(3L, NOW)).isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void anOfferWhoseReplacementVanishedBecomesUncovered() {
		RosterChange uncovered = offered().leftUncovered(78L, 5L, "nobody free any more", NOW);

		assertThat(uncovered.status()).isEqualTo(RosterChange.Status.UNCOVERED);
		assertThat(uncovered.incidentId()).isEqualTo(78L);
		assertThat(uncovered.rosteringRunId()).isEqualTo(5L);
		assertThat(offered().leftUncovered(78L, null, "x", NOW).rosteringRunId()).isEqualTo(2L);
	}

	@Test
	void aVisitCalledOffElsewhereWithdrawsTheChangeWithoutNamingAnybody() {
		RosterChange withdrawn = offered().withdrawn("care plan stopped", NOW);

		assertThat(withdrawn.status()).isEqualTo(RosterChange.Status.RESOLVED);
		assertThat(withdrawn.outcome()).isEqualTo(RosterChange.Outcome.WITHDRAWN);
		assertThat(withdrawn.decidedBy()).isNull();
		assertThat(saved(RosterChange.uncovered(12L, SLOT, 2L, 77L, NOW)).withdrawn("gone", NOW).outcome())
				.isEqualTo(RosterChange.Outcome.WITHDRAWN);
	}

	@Test
	void theFamilysTimeIsCutShortByTheVisitItself() {
		FamilyResponseWindow window = FamilyResponseWindow.DEFAULT;

		assertThat(window.respondBy(NOW, NOW.plusDays(1))).contains(NOW.plusHours(2));
		assertThat(window.respondBy(NOW, NOW.plusMinutes(150))).contains(NOW.plusMinutes(90));
		assertThat(window.respondBy(NOW, NOW.plusMinutes(60))).isEmpty();
		assertThat(window.respondBy(NOW, NOW.plusMinutes(30))).isEmpty();
	}

	@Test
	void theWindowMustBePositive() {
		Duration hour = Duration.ofHours(1);
		Duration negative = Duration.ofMinutes(-1);
		assertThatThrownBy(() -> new FamilyResponseWindow(Duration.ZERO, hour))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new FamilyResponseWindow(hour, negative))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aVisitWithNoEndIsGivenTheDefaultLengthAndKeepsItWhenMoved() {
		VacatedSlot open = new VacatedSlot(30L, 7L, null, null, NOW, null, 5L);
		assertThat(open.effectiveEnd()).isEqualTo(NOW.plus(VacatedSlot.DEFAULT_LENGTH));
		assertThat(open.length()).isEqualTo(VacatedSlot.DEFAULT_LENGTH);
		assertThat(open.day()).isEqualTo(NOW.toLocalDate());

		VacatedSlot moved = SLOT.movedTo(NOW.plusDays(3));
		assertThat(moved.start()).isEqualTo(NOW.plusDays(3));
		assertThat(moved.end()).isEqualTo(NOW.plusDays(3).plusMinutes(45));
		assertThat(moved.visitId()).isEqualTo(30L);
	}

	private static RosterChange offered() {
		return saved(RosterChange.offered(12L, SLOT, 2L, 9L, NOW.plusHours(2), NOW));
	}

	private static RosterChange saved(RosterChange change) {
		return new RosterChange(100L, change.absenceId(), change.visitId(), change.elderId(),
				change.originalCaregiverId(), change.visitStart(), change.visitEnd(), change.rosteringRunId(),
				change.proposedCaregiverId(), change.status(), change.outcome(), change.decidedBy(),
				change.decidedByUserId(), change.assignedCaregiverId(), change.rescheduledVisitId(), change.incidentId(),
				change.respondBy(), change.decidedAt(), change.note(), change.createdAt(), change.updatedAt());
	}
}
