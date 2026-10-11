package sg.nus.carelink.rostering.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.client.ResourceAccessException;

import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.rostering.domain.model.RosterChange;
import sg.nus.carelink.rostering.domain.model.VacatedSlot;
import sg.nus.carelink.rostering.domain.model.VisitChangeRetries;
import sg.nus.carelink.rostering.domain.model.VisitChangeStep;
import sg.nus.carelink.rostering.domain.repository.VisitReassignment;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * The saga's machinery on its own, with visit answering from a script: a decision's changes are made
 * and settled before it returns; a refusal is the answer of whoever is waiting, or a notification to
 * whoever decided; no answer is tried again with the same key, and given up into a manager's hands; and
 * what follows a change happens once. VisitChangeSagaIT runs the use case through it against MySQL.
 */
class VisitChangeSagaTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
	private static final VacatedSlot VISIT = new VacatedSlot(30L, 7L, 4L, "Personal care", NOW.plusDays(1),
			NOW.plusDays(1).plusMinutes(45), 5L);
	private static final VacatedSlot OTHER = new VacatedSlot(31L, 7L, 4L, null, NOW.plusDays(2), null, 5L);

	private final ReRosteringFakes.Steps steps = new ReRosteringFakes.Steps();
	private final Scripted visits = new Scripted();
	private final IncidentService incidents = mock(IncidentService.class);
	private final ReRosteringFakes.Alerts alerts = new ReRosteringFakes.Alerts();
	private final ReRosteringFakes.Profiles profiles = new ReRosteringFakes.Profiles().elder(7L, "Mdm Tan");
	private final ReRosteringFakes.MutableClock clock = new ReRosteringFakes.MutableClock(NOW, ZONE);
	private final VisitChangeSaga saga = new VisitChangeSaga(steps, visits, incidents, alerts, profiles,
			new RollingBack(steps), VisitChangeRetries.DEFAULT, clock);
	private final List<Long> settled = new ArrayList<>();
	private final VisitChangeSaga.Settlement settle = made -> {
		settled.add(made.id());
		return made.rosterChangeId() == null ? 500L + made.id() : made.rosterChangeId();
	};

	@BeforeEach
	void incidentsGetIds() {
		when(incidents.raiseForUnrosteredLeaveVisit(any(), any(), any())).thenAnswer(call -> IncidentFixtures.withId(
				Incident.raisedForUncoveredVisit(call.getArgument(0), call.getArgument(1), call.getArgument(2), NOW),
				900L));
	}

	@Test
	void aDecisionsChangesAreMadeAndSettledBeforeItReturns() {
		String decided = saga.decideThenChange(() -> {
			handOver(VISIT, 41L, 100L);
			uncover(OTHER, null);
			return "decided";
		}, settle);

		assertThat(decided).isEqualTo("decided");
		assertThat(visits.calls).containsExactly("REASSIGN 30", "MARK_UNCOVERED 31");
		assertThat(visits.keys).hasSize(2).doesNotHaveDuplicates().allSatisfy(key -> assertThat(key).startsWith("visit-change-"));
		assertThat(settled).containsExactly(1L, 2L);
		assertThat(steps.rows.values()).extracting(VisitChangeStep::status)
				.containsOnly(VisitChangeStep.Status.DONE);
		assertThat(steps.rows.get(1L).rosterChangeId()).isEqualTo(100L);
		assertThat(steps.rows.get(2L).rosterChangeId()).as("the change recorded on settling").isEqualTo(502L);
	}

	@Test
	void aMoveIsSettledWithTheVisitItCreated() {
		visits.moved = 777L;

		saga.decideThenChange(() -> saga.recordStep(12L, 100L, VISIT,
				VisitChangeStep.Request.move(NOW.plusDays(3), 9L, 41L, "Moved"),
				new VisitChangeStep.Decision(3L, RosterChange.DecidedBy.FAMILY, 41L, "Farah", "Moved")), settle);

		assertThat(steps.rows.get(1L).resultVisitId()).isEqualTo(777L);
		assertThat(visits.calls).containsExactly("MOVE 30");
	}

	@Test
	void aRefusalOfAPersonsDecisionIsTheirAnswerAndASystemDecisionsIsKeptQuiet() {
		visits.answers.add(new BusinessRuleViolation("VISIT_NOT_OPEN", "Visit 30 is IN_PROGRESS"));
		visits.answers.add(new ResourceNotFound("Visit", 31L));

		assertThatThrownBy(() -> saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), settle))
				.isInstanceOfSatisfying(BusinessRuleViolation.class,
						refused -> assertThat(refused.code()).isEqualTo("VISIT_NOT_OPEN"));
		saga.decideThenChange(() -> uncover(OTHER, null), settle);

		assertThat(steps.rows.values()).extracting(VisitChangeStep::status)
				.containsOnly(VisitChangeStep.Status.REJECTED);
		assertThat(steps.rows.get(2L).lastError()).startsWith("ResourceNotFound");
		assertThat(settled).isEmpty();
		assertThat(alerts.sent).as("a waiting person hears it as the answer").isEmpty();
	}

	@Test
	void aRefusalAfterTheRequestIsToldToWhoeverDecided() {
		visits.answers.add(new ResourceAccessException("Connection refused"));
		visits.answers.add(new BusinessRuleViolation("VISIT_NOT_OPEN", "Visit 30 is IN_PROGRESS"));
		saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), settle);
		clock.set(NOW.plusMinutes(1));

		saga.tryAgain(saga.due().get(0), settle);

		assertThat(steps.rows.get(1L).status()).isEqualTo(VisitChangeStep.Status.REJECTED);
		assertThat(steps.rows.get(1L).attempts()).isEqualTo(1);
		assertThat(alerts.sent).containsExactly("not made visit 30 for user 41");
		assertThat(alerts.notices.get(0).elderName()).isEqualTo("Mdm Tan");
		assertThat(alerts.notices.get(0).why()).isEqualTo("Visit 30 is IN_PROGRESS");
	}

	@Test
	void noAnswerLeavesTheChangeAndTheOnesAfterItForTheSweep() {
		visits.answers.add(new ResourceAccessException("Read timed out"));

		saga.decideThenChange(() -> {
			handOver(VISIT, 41L, 100L);
			handOver(OTHER, 41L, 101L);
			return null;
		}, settle);

		assertThat(visits.calls).as("visit is down; the second waits rather than times out too").hasSize(1);
		assertThat(steps.rows.get(1L).attempts()).isEqualTo(1);
		assertThat(steps.rows.get(1L).nextTryAt()).isEqualTo(NOW.plusMinutes(1));
		assertThat(steps.rows.get(2L).attempts()).isZero();
		assertThat(saga.due()).isEmpty();

		clock.set(NOW.plusMinutes(1));
		List<Long> due = saga.due();
		due.forEach(id -> saga.tryAgain(id, settle));

		assertThat(due).containsExactly(1L, 2L);
		assertThat(visits.calls).containsExactly("REASSIGN 30", "REASSIGN 30", "REASSIGN 31");
		assertThat(visits.keys.get(1)).as("the retry carries the first try's key").isEqualTo(visits.keys.get(0));
		assertThat(settled).containsExactly(1L, 2L);
	}

	@Test
	void aConcurrentUpdateAndARefusedCallAreTriedAgainNotTakenAsRefusals() {
		visits.answers.add(new BusinessRuleViolation("CONCURRENT_UPDATE", "The record changed concurrently"));
		visits.answers.add(new AccessDeniedException("Not permitted"));

		saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), settle);
		clock.set(NOW.plusMinutes(1));
		saga.tryAgain(1L, settle);
		clock.set(NOW.plusMinutes(3));
		saga.tryAgain(1L, settle);

		assertThat(steps.rows.get(1L).status()).isEqualTo(VisitChangeStep.Status.DONE);
		assertThat(steps.rows.get(1L).lastError()).startsWith("AccessDeniedException");
		assertThat(visits.calls).hasSize(3);
	}

	@Test
	void whatFollowsAChangeThatCouldNotBeRecordedIsTriedAgainWithTheSameKey() {
		boolean[] failOnce = {true};
		VisitChangeSaga.Settlement flaky = made -> {
			if (failOnce[0]) {
				failOnce[0] = false;
				throw new IllegalStateException("database went away");
			}
			return settle.settle(made);
		};

		saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), flaky);

		assertThat(steps.rows.get(1L).status()).isEqualTo(VisitChangeStep.Status.PENDING);
		assertThat(steps.rows.get(1L).lastError()).isEqualTo("IllegalStateException: database went away");
		clock.set(NOW.plusMinutes(1));
		saga.tryAgain(1L, flaky);
		assertThat(settled).containsExactly(1L);
		assertThat(visits.keys).hasSize(2).containsOnly(visits.keys.get(0));
	}

	@Test
	void aStepAlreadyFinishedIsNeitherSentNorSettledAgain() {
		saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), settle);

		saga.tryAgain(1L, settle);
		saga.tryAgain(99L, settle);

		assertThat(visits.calls).hasSize(1);
		assertThat(settled).containsExactly(1L);
	}

	@Test
	void retriesRunOutAManagerGetsAnIncidentAndAReRosterSendsItAgain() {
		for (int failure = 0; failure < 6; failure++) {
			visits.answers.add(new ResourceAccessException("Connection refused"));
		}
		saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), settle);
		for (int retry = 0; retry < 5; retry++) {
			clock.set(steps.rows.get(1L).nextTryAt());
			saga.tryAgain(1L, settle);
		}

		VisitChangeStep abandoned = steps.rows.get(1L);
		assertThat(abandoned.status()).isEqualTo(VisitChangeStep.Status.ABANDONED);
		assertThat(abandoned.attempts()).isEqualTo(6);
		assertThat(abandoned.incidentId()).isEqualTo(900L);
		verify(incidents).raiseForUnrosteredLeaveVisit(eq(7L), eq(30L),
				contains("Personal care visit at 8 Oct 09:00: the visit service could not be reached to hand it over after 6 tries"));
		assertThat(saga.heldVisits(12L)).containsExactly(30L);

		assertThat(saga.sendAgain(12L)).as("outside a decision: left to the sweep").isEqualTo(1);
		assertThat(steps.rows.get(1L).status()).isEqualTo(VisitChangeStep.Status.PENDING);
		clock.set(steps.rows.get(1L).nextTryAt());
		saga.tryAgain(saga.due().get(0), settle);
		assertThat(settled).containsExactly(1L);
		assertThat(saga.holding(12L)).isEmpty();
	}

	@Test
	void aStepGivenUpWhileItsIncidentCannotBeRaisedGetsItOnALaterSweep() {
		for (int failure = 0; failure < 6; failure++) {
			visits.answers.add(new ResourceAccessException("Connection refused"));
		}
		doThrow(new ResourceAccessException("visit is down: no latest caregiver"))
				.doAnswer(call -> IncidentFixtures.withId(Incident.raisedForUncoveredVisit(call.getArgument(0),
						call.getArgument(1), call.getArgument(2), NOW), 901L))
				.when(incidents).raiseForUnrosteredLeaveVisit(any(), any(), any());
		saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), settle);
		for (int retry = 0; retry < 5; retry++) {
			clock.set(steps.rows.get(1L).nextTryAt());
			saga.tryAgain(1L, settle);
		}

		assertThat(steps.rows.get(1L).status()).isEqualTo(VisitChangeStep.Status.ABANDONED);
		assertThat(steps.rows.get(1L).incidentId()).isNull();
		assertThat(saga.handOverGivenUp()).isEqualTo(1);
		assertThat(steps.rows.get(1L).incidentId()).isEqualTo(901L);
		assertThat(saga.handOverGivenUp()).as("raised once").isZero();
	}

	@Test
	void aStepSentAgainWhileItsIncidentIsRaisedGetsNone() {
		for (int failure = 0; failure < 6; failure++) {
			visits.answers.add(new ResourceAccessException("Connection refused"));
		}
		doAnswer(call -> {
			// The manager re-rosters at that moment, and the step is sent again
			VisitChangeStep given = steps.rows.get(1L);
			steps.rows.put(1L, given.sentAgain(NOW, NOW));
			return IncidentFixtures.withId(Incident.raisedForUncoveredVisit(call.getArgument(0), call.getArgument(1),
					call.getArgument(2), NOW), 902L);
		}).when(incidents).raiseForUnrosteredLeaveVisit(any(), any(), any());
		saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), settle);
		for (int retry = 0; retry < 5; retry++) {
			clock.set(steps.rows.get(1L).nextTryAt());
			saga.tryAgain(1L, settle);
		}

		assertThat(steps.rows.get(1L).incidentId()).as("rolled back with the incident").isNull();
	}

	@Test
	void anAbandonedStepIsSentAgainWithinTheDecisionThatAsksForIt() {
		for (int failure = 0; failure < 6; failure++) {
			visits.answers.add(new ResourceAccessException("Connection refused"));
		}
		saga.decideThenChange(() -> uncover(VISIT, 11L), settle);
		for (int retry = 0; retry < 5; retry++) {
			clock.set(steps.rows.get(1L).nextTryAt());
			saga.tryAgain(1L, settle);
		}

		saga.decideThenChange(() -> saga.sendAgain(12L), settle);

		assertThat(steps.rows.get(1L).status()).isEqualTo(VisitChangeStep.Status.DONE);
		assertThat(visits.keys).hasSize(7).containsOnly(visits.keys.get(0));
	}

	@Test
	void aChangeRecordedWhileSettlingAnotherIsMadeNextAndADecisionInsideAnotherJoinsIt() {
		VisitChangeSaga.Settlement leadsOn = made -> {
			if (made.id() == 1L) {
				uncover(OTHER, null);
			}
			return settle.settle(made);
		};

		saga.decideThenChange(() -> saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), leadsOn), leadsOn);

		assertThat(visits.calls).containsExactly("REASSIGN 30", "MARK_UNCOVERED 31");
		assertThat(settled).containsExactly(1L, 2L);
	}

	@Test
	void aChangeMadeInsideADecisionLeavesTheDecisionsOwnChangesToIt() {
		visits.answers.add(new ResourceAccessException("Connection refused"));
		saga.decideThenChange(() -> handOver(VISIT, 41L, 100L), settle);
		clock.set(NOW.plusMinutes(1));

		saga.decideThenChange(() -> {
			saga.tryAgain(1L, settle);
			return uncover(OTHER, null);
		}, settle);

		assertThat(settled).containsExactly(1L, 2L);
	}

	private VisitChangeStep handOver(VacatedSlot visit, Long decidedBy, Long changeId) {
		return saga.recordStep(12L, changeId, visit, VisitChangeStep.Request.reassign(9L, decidedBy, 77L, "Chosen"),
				new VisitChangeStep.Decision(3L, RosterChange.DecidedBy.FAMILY, decidedBy, "Farah", "kept"));
	}

	private VisitChangeStep uncover(VacatedSlot visit, Long decidedBy) {
		return saga.recordStep(12L, null, visit, VisitChangeStep.Request.markUncovered("Nobody was free"),
				new VisitChangeStep.Decision(3L, null, decidedBy, null, "Nobody was free"));
	}

	/** Transactions over the in-memory steps: a callback that throws leaves them as they were. */
	private static final class RollingBack implements TransactionOperations {

		private final ReRosteringFakes.Steps steps;

		RollingBack(ReRosteringFakes.Steps steps) {
			this.steps = steps;
		}

		@Override
		public <T> T execute(TransactionCallback<T> action) {
			Map<Long, VisitChangeStep> before = new LinkedHashMap<>(steps.rows);
			SimpleTransactionStatus status = new SimpleTransactionStatus();
			try {
				T result = action.doInTransaction(status);
				if (status.isRollbackOnly()) {
					restore(before);
				}
				return result;
			}
			catch (RuntimeException failure) {
				restore(before);
				throw failure;
			}
		}

		private void restore(Map<Long, VisitChangeStep> before) {
			steps.rows.clear();
			steps.rows.putAll(before);
		}
	}

	/** visit's changes answered from a script: each scripted failure in turn, then success. */
	private static final class Scripted implements VisitReassignment {

		final Deque<RuntimeException> answers = new ArrayDeque<>();
		final List<String> calls = new ArrayList<>();
		final List<String> keys = new ArrayList<>();
		Long moved = 600L;

		@Override
		public void reassign(Long visitId, Long toCaregiverId, Change why, String requestKey) {
			answer("REASSIGN", visitId, requestKey);
		}

		@Override
		public void markUncovered(Long visitId, Change why, String requestKey) {
			answer("MARK_UNCOVERED", visitId, requestKey);
		}

		@Override
		public void callOff(Long visitId, Change why, String requestKey) {
			answer("CALL_OFF", visitId, requestKey);
		}

		@Override
		public Long moveTo(Long visitId, LocalDateTime newStart, Long toCaregiverId, Change why, String requestKey) {
			answer("MOVE", visitId, requestKey);
			return moved;
		}

		private void answer(String what, Long visitId, String requestKey) {
			calls.add(what + " " + visitId);
			keys.add(requestKey);
			RuntimeException failure = answers.poll();
			if (failure != null) {
				throw failure;
			}
		}

		@Override
		public List<VisitSlot> unstartedFor(Long caregiverId, LocalDateTime from, LocalDateTime until) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Optional<VisitSlot> find(Long visitId) {
			throw new UnsupportedOperationException();
		}

		@Override
		public List<Booking> bookingsBetween(LocalDateTime from, LocalDateTime until) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<Long, Integer> finishedVisitsWith(Long elderId, LocalDateTime since, LocalDateTime until) {
			throw new UnsupportedOperationException();
		}
	}
}
