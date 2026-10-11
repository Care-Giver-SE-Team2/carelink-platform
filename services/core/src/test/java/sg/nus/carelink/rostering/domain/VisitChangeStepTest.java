package sg.nus.carelink.rostering.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.model.VisitChangeRetries;
import sg.nus.carelink.rostering.domain.model.VisitChangeStep;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * The re-rostering saga's step: made, refused or retried with the same key, and given up early enough
 * for a manager to act. A step holds its visit until visit has answered, or a manager has taken over.
 */
class VisitChangeStepTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final VacatedSlot TOMORROW = new VacatedSlot(30L, 7L, 4L, "Personal care", NOW.plusDays(1),
			NOW.plusDays(1).plusMinutes(45), 5L);
	private static final VisitChangeStep.Decision FAMILY_KEPT = new VisitChangeStep.Decision(3L,
			RosterChange.DecidedBy.FAMILY, 41L, "Farah", "The family kept the suggested replacement");

	@Test
	void aDecidedChangeWaitsWithAKeyOfItsOwnAndHoldsItsVisit() {
		VisitChangeStep step = handOver(TOMORROW);
		VisitChangeStep other = handOver(TOMORROW);

		assertThat(step.status()).isEqualTo(VisitChangeStep.Status.PENDING);
		assertThat(step.requestKey()).startsWith("visit-change-").isNotEqualTo(other.requestKey());
		assertThat(step.attempts()).isZero();
		assertThat(step.nextTryAt()).isEqualTo(NOW.plusMinutes(1));
		assertThat(step.holdsVisit()).isTrue();
		assertThat(step.isPending()).isTrue();
		assertThat(step.isAbandoned()).isFalse();
	}

	@Test
	void madeAndRefusedStepsLetGoOfTheirVisit() {
		VisitChangeStep made = handOver(TOMORROW).made(null, NOW.plusSeconds(1));
		VisitChangeStep refused = handOver(TOMORROW).refused("VISIT_NOT_OPEN: Visit 30 is IN_PROGRESS", NOW);

		assertThat(made.status()).isEqualTo(VisitChangeStep.Status.DONE);
		assertThat(made.updatedAt()).isEqualTo(NOW.plusSeconds(1));
		assertThat(made.holdsVisit()).isFalse();
		assertThat(refused.status()).isEqualTo(VisitChangeStep.Status.REJECTED);
		assertThat(refused.lastError()).isEqualTo("VISIT_NOT_OPEN: Visit 30 is IN_PROGRESS");
		assertThat(refused.holdsVisit()).isFalse();
		assertThatThrownBy(() -> made.refused("late", NOW)).isInstanceOf(BusinessRuleViolation.class)
				.hasMessageContaining("cannot be refused: it is DONE");
	}

	@Test
	void aMoveIsMadeWithTheVisitItCreated() {
		VisitChangeStep move = VisitChangeStep.decided(12L, 100L, TOMORROW,
				VisitChangeStep.Request.move(NOW.plusDays(3), 9L, 41L, "Moved"), FAMILY_KEPT, NOW, NOW);

		assertThat(move.made(501L, NOW).resultVisitId()).isEqualTo(501L);
		assertThatThrownBy(() -> move.made(null, NOW)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void anUnansweredStepIsTriedAgainAfterEachWaitAndThenGivenUp() {
		VisitChangeStep step = handOver(TOMORROW);
		LocalDateTime at = NOW;
		List<Long> waits = new ArrayList<>();
		for (int tried = 1; tried <= 5; tried++) {
			step = step.notThrough("ResourceAccessException: Connection refused", at, VisitChangeRetries.DEFAULT);
			assertThat(step.status()).isEqualTo(VisitChangeStep.Status.PENDING);
			assertThat(step.attempts()).isEqualTo(tried);
			waits.add(Duration.between(at.atZone(VacatedSlot.ZONE), step.nextTryAt().atZone(VacatedSlot.ZONE)).toMinutes());
			at = step.nextTryAt();
		}
		step = step.notThrough("ResourceAccessException: Connection refused", at, VisitChangeRetries.DEFAULT);

		assertThat(waits).containsExactly(1L, 2L, 5L, 10L, 20L);
		assertThat(step.status()).isEqualTo(VisitChangeStep.Status.ABANDONED);
		assertThat(step.attempts()).isEqualTo(6);
		assertThat(step.holdsVisit()).as("a manager takes it over").isTrue();
		assertThat(step.lastError()).isEqualTo("ResourceAccessException: Connection refused");
	}

	@Test
	void aStepIsGivenUpEarlyWhenTheNextTryWouldComeWithinAnHourOfTheVisit() {
		VacatedSlot soon = new VacatedSlot(30L, 7L, 4L, null, NOW.plusMinutes(70), null, 5L);

		VisitChangeStep once = handOver(soon).notThrough("timed out", NOW, VisitChangeRetries.DEFAULT);
		VisitChangeStep tooClose = handOver(soon).notThrough("timed out", NOW.plusMinutes(9), VisitChangeRetries.DEFAULT);

		assertThat(once.status()).as("again at 09:01, before 09:10").isEqualTo(VisitChangeStep.Status.PENDING);
		assertThat(tooClose.status()).as("again at 09:10 would be an hour before").isEqualTo(
				VisitChangeStep.Status.ABANDONED);
	}

	@Test
	void anAbandonedStepGoesToAManagerAndIsSentAgainWithItsKey() {
		VisitChangeStep abandoned = handOver(TOMORROW);
		for (int tried = 0; tried < 6; tried++) {
			abandoned = abandoned.notThrough("down", NOW, VisitChangeRetries.DEFAULT);
		}
		VisitChangeStep handedOver = abandoned.handedToManager(900L);
		VisitChangeStep again = handedOver.sentAgain(NOW.plusHours(1), NOW.plusMinutes(59));

		assertThat(handedOver.incidentId()).isEqualTo(900L);
		assertThat(again.status()).isEqualTo(VisitChangeStep.Status.PENDING);
		assertThat(again.attempts()).isZero();
		assertThat(again.requestKey()).isEqualTo(abandoned.requestKey());
		assertThat(again.nextTryAt()).isEqualTo(NOW.plusHours(1));
		assertThat(again.incidentId()).isEqualTo(900L);
		assertThatThrownBy(() -> again.sentAgain(NOW, NOW)).isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> again.handedToManager(901L)).isInstanceOf(BusinessRuleViolation.class);
		VisitChangeStep made = again.made(null, NOW);
		assertThatThrownBy(() -> made.notThrough("late", NOW, VisitChangeRetries.DEFAULT))
				.isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void aSettledStepNamesItsChangeAndALongErrorIsCut() {
		VisitChangeStep uncover = VisitChangeStep.decided(12L, null, TOMORROW,
				VisitChangeStep.Request.markUncovered("Nobody was free"),
				new VisitChangeStep.Decision(3L, null, 11L, null, "Nobody was free"), NOW, NOW);

		assertThat(uncover.settling(150L).rosterChangeId()).isEqualTo(150L);
		assertThat(uncover.refused("x".repeat(600), NOW).lastError()).hasSize(500).endsWith("...");
	}

	@Test
	void eachRequestCarriesWhatVisitNeeds() {
		VisitChangeStep.Request handOver = VisitChangeStep.Request.reassign(9L, 41L, 77L, "Chosen by the family");
		VisitChangeStep.Request uncover = VisitChangeStep.Request.markUncovered("Nobody was free");
		VisitChangeStep.Request callOff = VisitChangeStep.Request.callOff(41L, "Skipped");
		VisitChangeStep.Request move = VisitChangeStep.Request.move(NOW.plusDays(2), 9L, 41L, "Moved");

		assertThat(handOver).isEqualTo(new VisitChangeStep.Request(VisitChangeStep.Kind.REASSIGN, 9L, null, 41L, 77L,
				"Chosen by the family"));
		assertThat(uncover.kind()).isEqualTo(VisitChangeStep.Kind.MARK_UNCOVERED);
		assertThat(uncover.toCaregiverId()).isNull();
		assertThat(callOff.kind()).isEqualTo(VisitChangeStep.Kind.CALL_OFF);
		assertThat(callOff.byUserId()).isEqualTo(41L);
		assertThat(move.newStart()).isEqualTo(NOW.plusDays(2));
		assertThatThrownBy(() -> VisitChangeStep.Request.reassign(null, 41L, null, "x"))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void theRetriesAreAtLeastOneAndEveryWaitIsPositive() {
		List<Duration> none = List.of();
		List<Duration> zero = List.of(Duration.ZERO);
		Duration hour = Duration.ofHours(1);

		assertThatThrownBy(() -> new VisitChangeRetries(none, hour)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new VisitChangeRetries(zero, hour)).isInstanceOf(IllegalArgumentException.class);
		assertThat(VisitChangeRetries.DEFAULT.firstWait()).isEqualTo(Duration.ofMinutes(1));
		assertThat(VisitChangeRetries.DEFAULT.nextTry(0, NOW, NOW.plusDays(1))).isEmpty();
		assertThat(VisitChangeRetries.DEFAULT.nextTry(6, NOW, NOW.plusDays(1))).isEmpty();
	}

	private static VisitChangeStep handOver(VacatedSlot visit) {
		return VisitChangeStep.decided(12L, 100L, visit, VisitChangeStep.Request.reassign(9L, 41L, 77L,
				"Chosen by the family (absence 12): kept"), FAMILY_KEPT, NOW.plusMinutes(1), NOW);
	}
}
