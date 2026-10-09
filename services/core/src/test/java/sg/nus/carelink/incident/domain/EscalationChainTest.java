package sg.nus.carelink.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.EscalationChain;
import sg.nus.carelink.incident.domain.model.EscalationLevel;
import sg.nus.carelink.incident.domain.model.EscalationTier;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.service.EscalationChainBuilder;
import sg.nus.carelink.incident.domain.service.EscalationOutcome;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;
import sg.nus.carelink.incident.domain.service.EscalationRequest;
import sg.nus.carelink.incident.domain.service.ResponderHandler;
import sg.nus.carelink.incident.support.FakeManagerDirectory;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.incident.support.InMemoryIncidentRepository;

/**
 * The design problem itself: who an incident is offered to, in what order, and what happens
 * when a level cannot take it.
 *
 * <p>These are the tests the report's Chain of Responsibility section argues from. Each one
 * states a rule from UC-MG05 or UC-SYS02 and checks the chain honours it, without a
 * database, a scheduler or a real clock.
 */
class EscalationChainTest {

	private static final EscalationPolicy POLICY = EscalationPolicy.defaults();

	private final InMemoryIncidentRepository history = new InMemoryIncidentRepository();

	// ------------------------------------------------------------------ assembly ---

	@Test
	void aHighSeverityChainTriesContinuityBeforeItTriesAnybody() {
		EscalationChain chain = describe(savedSos(1L), directoryOf(2));

		assertThat(chain.levels()).extracting(EscalationLevel::tier).containsExactly(
				EscalationTier.ASSIGNED_RESPONDER,
				EscalationTier.FAMILIAR_MANAGER,
				EscalationTier.ANY_MANAGER,
				EscalationTier.FAMILY_ESCALATION);
	}

	@Test
	void aLowSeverityIncidentIsNotWorthWaitingForAParticularManager() {
		EscalationChain chain = describe(savedWith(1L, Incident.Severity.LOW), directoryOf(2));

		assertThat(chain.levels()).extracting(EscalationLevel::tier).containsExactly(
				EscalationTier.ASSIGNED_RESPONDER,
				EscalationTier.ANY_MANAGER,
				EscalationTier.FAMILY_ESCALATION);
	}

	@Test
	void everyChainKeepsTheAnyManagerTierSoNobodyIsEverLeftHolding() {
		for (Incident.Severity severity : Incident.Severity.values()) {
			EscalationChain chain = describe(savedWith(1L, severity), directoryOf(1));

			assertThat(chain.levels())
					.as("severity %s", severity)
					.extracting(EscalationLevel::tier)
					.contains(EscalationTier.ANY_MANAGER);
		}
	}

	@Test
	void theChainAlwaysEndsWithTheTerminalTier() {
		EscalationChain chain = describe(savedSos(1L), FakeManagerDirectory.empty());

		assertThat(chain.terminalLevel().tier()).isEqualTo(EscalationTier.FAMILY_ESCALATION);
		assertThat(chain.terminalLevel().isFillable()).isFalse();
	}

	@Test
	void theChainRecordsWhatItWasAssembledFrom() {
		EscalationChain chain = describe(savedSos(1L), directoryOf(2));

		assertThat(chain.assembledFrom())
				.contains("HIGH")
				.contains("continuity tier included")
				.contains("2 manager(s)");
		assertThat(chain.assembledAt()).isEqualTo(IncidentFixtures.RAISED_AT);
	}

	@Test
	void oneManagerWhoQualifiesForTwoTiersIsListedOnlyOnce() {
		Incident incident = savedSos(1L);
		givenTheElderWasHandledBefore(incident, IncidentFixtures.ALICE.userId());

		EscalationChain chain = describe(incident, directoryOf(1));

		assertThat(chain.levels())
				.filteredOn(EscalationLevel::isFillable)
				.extracting(EscalationLevel::responderUserId)
				.containsExactly(IncidentFixtures.ALICE.userId());
	}

	@Test
	void theBuilderRefusesToProduceAChainItCannotFillIn() {
		Incident incident = savedSos(1L);

		assertThatThrownBy(() -> EscalationChainBuilder.forIncident(incident).build())
				.isInstanceOf(NullPointerException.class)
				.hasMessageContaining("at:");
	}

	// ------------------------------------------------------------------- routing ---

	@Test
	void aFirstEverIncidentForAnElderGoesToTheFirstAvailableManager() {
		EscalationOutcome outcome = route(savedSos(1L), directoryOf(2));

		assertThat(outcome.assigned()).isTrue();
		assertThat(outcome.tier()).isEqualTo(EscalationTier.ANY_MANAGER);
		assertThat(outcome.responder()).isEqualTo(IncidentFixtures.ALICE);
		assertThat(outcome.describeRoute()).contains("no manager has handled this elder before");
	}

	@Test
	void anElderWhoHasBeenHandledBeforeGoesBackToTheSameManager() {
		Incident incident = savedSos(1L);
		givenTheElderWasHandledBefore(incident, IncidentFixtures.BEN.userId());

		EscalationOutcome outcome = route(incident, directoryOf(2));

		assertThat(outcome.tier()).isEqualTo(EscalationTier.FAMILIAR_MANAGER);
		assertThat(outcome.responder()).isEqualTo(IncidentFixtures.BEN);
	}

	@Test
	void theFirstLevelsCountdownComesFromTheSeverity() {
		EscalationOutcome high = route(savedSos(1L), directoryOf(1));
		EscalationOutcome low = route(savedWith(2L, Incident.Severity.LOW), directoryOf(1));

		assertThat(high.countdown()).isEqualTo(Duration.ofMinutes(5));
		assertThat(low.countdown()).isEqualTo(Duration.ofMinutes(60));
	}

	@Test
	void steppingOverATierNobodyFillsDoesNotExtendTheNextDeadline() {
		EscalationOutcome outcome = route(savedSos(1L), directoryOf(1));

		assertThat(outcome.tier()).isEqualTo(EscalationTier.ANY_MANAGER);
		assertThat(outcome.countdown()).isEqualTo(Duration.ofMinutes(5));
	}

	// ----------------------------------------------------------------- escalating ---

	@Test
	void anEscalationDoesNotHandTheIncidentBackToWhoeverJustTimedOut() {
		Incident timedOut = savedSos(1L)
				.assignTo(IncidentFixtures.ALICE.userId(), IncidentFixtures.RAISED_AT.plusMinutes(5));

		EscalationOutcome outcome = chain(timedOut, directoryOf(2)).handle(EscalationRequest.afterTimeout(
				timedOut, IncidentFixtures.RAISED_AT.plusMinutes(6), POLICY,
				Set.of(IncidentFixtures.ALICE.userId())));

		assertThat(outcome.assigned()).isTrue();
		assertThat(outcome.responder()).isEqualTo(IncidentFixtures.BEN);
	}

	@Test
	void aResponderReachedAfterSomebodyTimedOutGetsLongerThanTheFirstDid() {
		Incident incident = savedSos(1L);

		EscalationOutcome outcome = chain(incident, directoryOf(2)).handle(EscalationRequest.afterTimeout(
				incident, IncidentFixtures.RAISED_AT, POLICY, Set.of(IncidentFixtures.ALICE.userId())));

		assertThat(outcome.responder()).isEqualTo(IncidentFixtures.BEN);
		assertThat(outcome.countdown()).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void theResponderAlreadyNamedKeepsTheIncidentWhenTheChainIsMerelyRebuilt() {
		Incident held = savedSos(1L)
				.assignTo(IncidentFixtures.ALICE.userId(), IncidentFixtures.RAISED_AT.plusMinutes(5));

		EscalationOutcome outcome = route(held, directoryOf(2));

		assertThat(outcome.tier()).isEqualTo(EscalationTier.ASSIGNED_RESPONDER);
		assertThat(outcome.responder()).isEqualTo(IncidentFixtures.ALICE);
	}

	@Test
	void whenEveryManagerHasAlreadyHeldItTheChainIsExhausted() {
		Incident incident = savedSos(1L);

		EscalationOutcome outcome = chain(incident, directoryOf(2)).handle(EscalationRequest.afterTimeout(
				incident, IncidentFixtures.RAISED_AT, POLICY,
				Set.of(IncidentFixtures.ALICE.userId(), IncidentFixtures.BEN.userId())));

		assertThat(outcome.assigned()).isFalse();
		assertThat(outcome.tier()).isEqualTo(EscalationTier.FAMILY_ESCALATION);
		assertThat(outcome.describeRoute()).startsWith("chain exhausted");
	}

	@Test
	void anInstitutionWithNoManagersAtAllExhaustsImmediately() {
		EscalationOutcome outcome = route(savedSos(1L), FakeManagerDirectory.empty());

		assertThat(outcome.assigned()).isFalse();
		assertThat(outcome.skipped()).hasSize(3);
	}

	// --------------------------------------------------------------- value objects ---

	@Test
	void aChainWithoutLevelsIsNotAChain() {
		assertThatThrownBy(() -> new EscalationChain(
				1L, IncidentFixtures.RAISED_AT, Incident.Severity.HIGH, "none", List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void aLevelKnowsWhetherAnybodyFillsIt() {
		EscalationLevel filled = EscalationLevel.pending(
				1, EscalationTier.FAMILIAR_MANAGER, IncidentFixtures.ALICE, Duration.ofMinutes(5));
		EscalationLevel empty = EscalationLevel.skipped(
				2, EscalationTier.ANY_MANAGER, Duration.ofMinutes(10));

		assertThat(filled.isFillable()).isTrue();
		assertThat(filled.responderUserId()).isEqualTo(IncidentFixtures.ALICE.userId());
		assertThat(empty.isFillable()).isFalse();
		assertThat(empty.responderUserId()).isNull();
		assertThat(empty.state()).isEqualTo(EscalationLevel.State.SKIPPED_UNAVAILABLE);
	}

	@Test
	void takingALevelCurrentFixesItsDeadline() {
		EscalationLevel current = EscalationLevel
				.pending(1, EscalationTier.FAMILIAR_MANAGER, IncidentFixtures.ALICE, Duration.ofMinutes(5))
				.takeCurrentFrom(IncidentFixtures.RAISED_AT);

		assertThat(current.state()).isEqualTo(EscalationLevel.State.CURRENT);
		assertThat(current.respondBy()).isEqualTo(IncidentFixtures.RAISED_AT.plusMinutes(5));
		assertThat(current.timedOut().state()).isEqualTo(EscalationLevel.State.TIMED_OUT);
		assertThat(current.claimed().state()).isEqualTo(EscalationLevel.State.CLAIMED);
	}

	@Test
	void aLevelPositionStartsAtOne() {
		assertThatThrownBy(() -> EscalationLevel.pending(
				0, EscalationTier.ANY_MANAGER, IncidentFixtures.ALICE, Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void theChainCanReportWhatComesAfterALevelAndWhenItIsSpent() {
		EscalationChain chain = describe(savedSos(1L), directoryOf(2));
		EscalationLevel first = chain.levels().get(0);

		assertThat(chain.size()).isEqualTo(4);
		assertThat(chain.after(first)).isPresent();
		assertThat(chain.isExhaustedFrom(chain.size() + 1)).isTrue();
		assertThat(chain.current()).isEmpty();
		assertThat(chain.with(first.claimed()).levels().get(0).state())
				.isEqualTo(EscalationLevel.State.CLAIMED);
	}

	@Test
	void aTierKnowsWhetherItIsTheLastOne() {
		assertThat(EscalationTier.FAMILY_ESCALATION.isTerminal()).isTrue();
		assertThat(EscalationTier.FAMILIAR_MANAGER.isTerminal()).isFalse();
		assertThat(EscalationTier.FAMILIAR_MANAGER.label()).isEqualTo("Manager who knows this elder");
	}

	// -------------------------------------------------------------------- helpers ---

	private static FakeManagerDirectory directoryOf(int howMany) {
		return switch (howMany) {
			case 0 -> FakeManagerDirectory.empty();
			case 1 -> FakeManagerDirectory.with(IncidentFixtures.ALICE);
			case 2 -> FakeManagerDirectory.with(IncidentFixtures.ALICE, IncidentFixtures.BEN);
			default -> FakeManagerDirectory.with(
					IncidentFixtures.ALICE, IncidentFixtures.BEN, IncidentFixtures.CARA);
		};
	}

	private Incident savedSos(Long id) {
		Incident incident = IncidentFixtures.savedSos(id);
		history.save(incident);
		return incident;
	}

	private Incident savedWith(Long id, Incident.Severity severity) {
		Incident incident = IncidentFixtures.savedWithSeverity(id, severity);
		history.save(incident);
		return incident;
	}

	/** An earlier incident for the same elder that this manager handled. */
	private void givenTheElderWasHandledBefore(Incident incident, Long responderUserId) {
		history.save(IncidentFixtures.handledEarlier(99L, incident.elderId(), responderUserId));
	}

	private ResponderHandler chain(Incident incident, FakeManagerDirectory directory) {
		return builder(incident, directory).build();
	}

	private EscalationOutcome route(Incident incident, FakeManagerDirectory directory) {
		return chain(incident, directory)
				.handle(EscalationRequest.routing(incident, IncidentFixtures.RAISED_AT, POLICY));
	}

	private EscalationChain describe(Incident incident, FakeManagerDirectory directory) {
		return builder(incident, directory).describe();
	}

	private EscalationChainBuilder builder(Incident incident, FakeManagerDirectory directory) {
		return EscalationChainBuilder.forIncident(incident)
				.at(IncidentFixtures.RAISED_AT)
				.withPolicy(POLICY)
				.from(directory)
				.withHistory(history);
	}
}
