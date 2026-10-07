package sg.nus.carelink.incident.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import sg.nus.carelink.incident.domain.model.ContactAttempt;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.model.PageSlice;
import sg.nus.carelink.incident.domain.model.Playbook;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;
import sg.nus.carelink.incident.support.FakeManagerDirectory;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.incident.support.InMemoryIncidentLogRepository;
import sg.nus.carelink.incident.support.InMemoryIncidentRepository;
import sg.nus.carelink.incident.support.RecordingAlert;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * UC-MG05 from the application layer down: real domain rules, real chain, fake storage.
 *
 * <p>No Spring, no database, no scheduler. That is only possible because the ports are
 * interfaces the domain owns, which is the argument the report's layering section makes.
 */
class IncidentServiceTest {

	private final InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
	private final InMemoryIncidentLogRepository timeline = new InMemoryIncidentLogRepository();
	private final RecordingAlert alert = new RecordingAlert();
	private final Clock clock = IncidentFixtures.clockAt(IncidentFixtures.RAISED_AT);

	private IncidentService service;

	@BeforeEach
	void setUp() {
		service = buildService(FakeManagerDirectory.with(IncidentFixtures.ALICE, IncidentFixtures.BEN));
	}

	/** An incident raised through the manager module's own entry point, so it is routed. */
	private Incident raise() {
		return service.reportByCaregiver(
				7L, null, 20L, Incident.Category.SOS, Incident.Severity.HIGH, "SOS");
	}

	private IncidentService buildService(FakeManagerDirectory directory) {
		EscalationService escalation = new EscalationService(
				incidents, timeline, directory, alert, EscalationPolicy.defaults(), clock);
		return new IncidentService(incidents, timeline, escalation, clock);
	}

	// -------------------------------------------------------------------- raising ---

	// ------------------------------------------------------- telling people ---

	@Test
	void everybodyWhoCouldActIsToldBeforeTheChainDecidesAnything() {
		Incident raised = raise();

		assertThat(alert.broadcasts())
				.as("one broadcast, at the moment the incident was raised")
				.containsExactly(raised.id());
		assertThat(timeline.actionsFor(raised.id()))
				.as("the broadcast lands on the timeline before the first assignment")
				.containsSubsequence("BROADCAST", "ASSIGNED");
	}

	@Test
	void anEscalationTellsOnlyTheTwoPeopleWhoseResponsibilityChanged() {
		Incident raised = raise();
		int broadcastsAfterRaising = alert.broadcasts().size();

		service.escalate(raised.id(), "no answer", "Manager");

		assertThat(alert.broadcasts())
				.as("the family already knows; an escalation does not tell everyone again")
				.hasSize(broadcastsAfterRaising);
		assertThat(alert.handOvers()).last()
				.satisfies(handOver -> {
					assertThat(handOver.from()).isEqualTo(IncidentFixtures.ALICE.userId());
					assertThat(handOver.to()).isEqualTo(IncidentFixtures.BEN.userId());
				});
	}

	@Test
	void theFamilyIsToldWhenNobodyTakesTheIncidentAtAll() {
		service = buildService(FakeManagerDirectory.empty());

		Incident raised = raise();

		assertThat(raised.status()).isEqualTo(Incident.Status.UNRESOLVED_ESCALATED);
		assertThat(alert.exhausted()).containsExactly(raised.id());
	}

	// ------------------------------------------------------------- routing ---

	@Test
	void aCaregiverReportIsRoutedTheMomentItIsRaised() {
		Incident raised = service.reportByCaregiver(
				7L, 4L, 20L, Incident.Category.FALL, Incident.Severity.MEDIUM, "slipped");

		assertThat(raised.responderUserId()).isEqualTo(IncidentFixtures.ALICE.userId());
		assertThat(raised.respondBy()).isEqualTo(IncidentFixtures.RAISED_AT.plusMinutes(15));
		assertThat(timeline.actionsFor(raised.id())).containsExactly("REPORTED", "BROADCAST", "ASSIGNED");
	}

	/**
	 * Records the gap rather than papering over it. UC-EL03 belongs to the elder module and
	 * still stops at "saved"; routing it is one line in that module's own method, and is
	 * raised there rather than changed from here.
	 */
	@Test
	void anElderSosIsNotRoutedYetAndThatIsTheKnownGap() {
		Incident raised = service.createElderEmergency(7L, 99L, null, null, "Blk 123", "fell");

		assertThat(raised.responderUserId()).isNull();
		assertThat(raised.respondBy()).isNull();
	}

	@Test
	void withNoManagersAtAllTheIncidentIsPinnedForTheFamilyInsteadOfVanishing() {
		service = buildService(FakeManagerDirectory.empty());

		Incident raised = raise();

		assertThat(raised.status()).isEqualTo(Incident.Status.UNRESOLVED_ESCALATED);
		assertThat(raised.resolvedAt()).isNull();
		assertThat(timeline.actionsFor(raised.id())).contains("CHAIN_EXHAUSTED");
	}

	// ------------------------------------------------------------------- handling ---

	@Test
	void takingOverStopsTheCountdownAndIsRecorded() {
		Incident raised = raise();

		Incident claimed = service.claim(raised.id(), IncidentFixtures.BEN.userId(), "Ben");

		assertThat(claimed.status()).isEqualTo(Incident.Status.IN_PROGRESS);
		assertThat(claimed.respondBy()).isNull();
		assertThat(timeline.actionsFor(raised.id())).endsWith("CLAIMED");
	}

	@Test
	void aRefusedTakeOverIsWrittenToTheTimelineBeforeItIsRejected() {
		Incident raised = raise();
		service.claim(raised.id(), IncidentFixtures.ALICE.userId(), "Alice");

		Long incidentId = raised.id();
		Long ben = IncidentFixtures.BEN.userId();
		assertThatThrownBy(() -> service.claim(incidentId, ben, "Ben"))
				.isInstanceOf(BusinessRuleViolation.class);

		assertThat(timeline.actionsFor(raised.id())).endsWith("CLAIM_REJECTED");
	}

	@Test
	void anIncidentThatHasBeenTakenOverCannotBeEscalated() {
		Incident raised = raise();
		service.claim(raised.id(), IncidentFixtures.ALICE.userId(), "Alice");

		Long incidentId = raised.id();
		assertThatThrownBy(() -> service.escalate(incidentId, "too slow", "Manager"))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(violation -> ((BusinessRuleViolation) violation).code())
				.isEqualTo("INCIDENT_NOT_AWAITING_TAKE_OVER");
	}

	@Test
	void escalatingByHandMovesTheIncidentToSomebodyElse() {
		Incident raised = raise();

		Incident escalated = service.escalate(raised.id(), "no answer", "Manager");

		assertThat(escalated.responderUserId()).isEqualTo(IncidentFixtures.BEN.userId());
		assertThat(timeline.actionsFor(raised.id())).contains("ESCALATED", "ASSIGNED");
	}

	// -------------------------------------------------------------------- contact ---

	@Test
	void reachingTheFamilyIsRecordedAndOffersNoFallback() {
		Incident raised = raise();

		IncidentService.ContactOutcome outcome = service.recordContactAttempt(
				raised.id(),
				new ContactAttempt(ContactAttempt.Channel.PHONE, ContactAttempt.Outcome.REACHED, "daughter"),
				"Alice");

		assertThat(outcome.hasFallback()).isFalse();
		assertThat(timeline.actionsFor(raised.id())).contains("CONTACT_ATTEMPTED");
	}

	@Test
	void failingToReachTheFamilyOffersThePlaybookForThatCategory() {
		Incident raised = raise();

		IncidentService.ContactOutcome outcome = service.recordContactAttempt(
				raised.id(),
				new ContactAttempt(ContactAttempt.Channel.PHONE, ContactAttempt.Outcome.NOT_REACHED, "no answer"),
				"Alice");

		assertThat(outcome.hasFallback()).isTrue();
		assertThat(outcome.suggestedPlaybook()).isEqualTo(Playbook.SOS_IMMEDIATE);
	}

	@Test
	void applyingAPlaybookMeantForAnotherCategoryIsRefused() {
		Incident raised = raise();

		Long incidentId = raised.id();
		assertThatThrownBy(() -> service.applyPlaybook(incidentId, "PB-MED", "Alice"))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(violation -> ((BusinessRuleViolation) violation).code())
				.isEqualTo("PLAYBOOK_CATEGORY_MISMATCH");
	}

	@Test
	void applyingTheRightPlaybookIsRecorded() {
		Incident raised = raise();

		service.applyPlaybook(raised.id(), "PB-SOS", "Alice");

		assertThat(timeline.actionsFor(raised.id())).contains("PLAYBOOK_APPLIED");
	}

	@Test
	void anUnknownPlaybookIsNotFound() {
		Incident raised = raise();

		Long incidentId = raised.id();
		assertThatThrownBy(() -> service.applyPlaybook(incidentId, "PB-NOPE", "Alice"))
				.isInstanceOf(ResourceNotFound.class);
	}

	// ------------------------------------------------------------------- severity ---

	@Test
	void changingTheSeverityRebuildsTheChainAndContinuesTheSameTimeline() {
		Incident raised = raise();

		Incident changed = service.changeSeverity(raised.id(), Incident.Severity.LOW, "calmer now", "Alice");

		assertThat(changed.severity()).isEqualTo(Incident.Severity.LOW);
		assertThat(changed.responderUserId()).isEqualTo(IncidentFixtures.ALICE.userId());
		assertThat(changed.respondBy()).isEqualTo(IncidentFixtures.RAISED_AT.plusMinutes(60));
		assertThat(timeline.actionsFor(raised.id())).contains("SEVERITY_CHANGED");
		assertThat(timeline.findTimeline(raised.id()).get(0).action()).isEqualTo("REPORTED");
	}

	// ------------------------------------------------------------------ resolving ---

	@Test
	void onlyTheResponderHandlingItMayCloseIt() {
		Incident raised = raise();
		service.claim(raised.id(), IncidentFixtures.ALICE.userId(), "Alice");

		Long incidentId = raised.id();
		Long ben = IncidentFixtures.BEN.userId();
		assertThatThrownBy(() -> service.resolve(incidentId, ben, "all fine", null, "Ben"))
				.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void closingWithoutANoteIsRefused() {
		Incident raised = raise();
		service.claim(raised.id(), IncidentFixtures.ALICE.userId(), "Alice");

		Long incidentId = raised.id();
		Long alice = IncidentFixtures.ALICE.userId();
		assertThatThrownBy(() -> service.resolve(incidentId, alice, "  ", null, "Alice"))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(violation -> ((BusinessRuleViolation) violation).code())
				.isEqualTo("RESOLUTION_NOTE_REQUIRED");
	}

	@Test
	void closingRecordsTheOutcomeAndTheNote() {
		Incident raised = raise();
		service.claim(raised.id(), IncidentFixtures.ALICE.userId(), "Alice");

		Incident resolved = service.resolve(
				raised.id(), IncidentFixtures.ALICE.userId(), "ambulance called", "REFERRED_TO_MEDICAL_CARE", "Alice");

		assertThat(resolved.status()).isEqualTo(Incident.Status.RESOLVED);
		assertThat(timeline.findTimeline(raised.id()))
				.filteredOn(entry -> "RESOLVED".equals(entry.action()))
				.singleElement()
				.extracting(IncidentLog::detail)
				.asString()
				.contains("REFERRED_TO_MEDICAL_CARE")
				.contains("ambulance called");
	}

	// -------------------------------------------------------------------- reading ---

	@Test
	void readingAnIncidentThatDoesNotExistIsNotFound() {
		assertThatThrownBy(() -> service.timelineOf(404L)).isInstanceOf(ResourceNotFound.class);
		assertThatThrownBy(() -> service.escalationChainOf(404L)).isInstanceOf(ResourceNotFound.class);
	}

	@Test
	void theChainCanBeReadWithoutChangingAnything() {
		Incident raised = raise();
		int entriesBefore = timeline.actionsFor(raised.id()).size();

		assertThat(service.escalationChainOf(raised.id()).levels()).isNotEmpty();
		assertThat(timeline.actionsFor(raised.id())).hasSize(entriesBefore);
	}

	@Test
	void incidentsCanBeListedPerElderAndPlaybooksAreOffered() {
		raise();

		assertThat(service.forElder(7L)).hasSize(1);
		assertThat(service.forElder(8L)).isEmpty();
		assertThat(service.playbooks()).containsExactly(Playbook.values());
		assertThat(service.findIncident(404L)).isEmpty();
	}

	/**
	 * The queue with no status asked for is the screen a manager opens on, so what it leaves
	 * out is the interesting part: a closed incident is gone from it, and one the chain ran
	 * out on is not.
	 */
	@Test
	void theQueueShowsEverythingStillNeedingAttentionAndNothingFinishedWith() {
		Incident open = raise();
		Incident closed = raise();
		service.claim(closed.id(), IncidentFixtures.ALICE.userId(), "Alice");
		service.resolve(closed.id(), IncidentFixtures.ALICE.userId(), "all fine", null, "Alice");

		PageSlice<Incident> queue = service.queue(null, null, null, 0, 20);

		assertThat(queue.items()).extracting(Incident::id).containsExactly(open.id());
		assertThat(queue.totalElements()).isEqualTo(1);
		assertThat(queue.page()).isZero();
		assertThat(queue.size()).isEqualTo(20);
	}

	@Test
	void askingTheQueueForOneStatusShowsThatStatusEvenWhenItIsClosed() {
		Incident raised = raise();
		service.claim(raised.id(), IncidentFixtures.ALICE.userId(), "Alice");
		service.resolve(raised.id(), IncidentFixtures.ALICE.userId(), "all fine", null, "Alice");

		assertThat(service.queue(Incident.Status.RESOLVED, null, null, 0, 20).items())
				.extracting(Incident::id)
				.containsExactly(raised.id());
		assertThat(service.queue(Incident.Status.OPEN, null, null, 0, 20).items()).isEmpty();
	}

	/**
	 * A page number out of a URL is a slip, not an attack. Answering with the first page is
	 * more use than a 400, and an unbounded size is how one request reads the whole table.
	 */
	@Test
	void anImpossiblePageOrSizeIsBroughtBackIntoRangeRatherThanRefused() {
		raise();

		assertThat(service.queue(null, null, null, -3, 0).items()).hasSize(1);
		assertThat(service.queue(null, null, null, 0, 5000).size()).isEqualTo(100);
	}

	@Test
	void theQueueNarrowsToOneSeverityAndOneElderWhenAsked() {
		Incident raised = raise();

		assertThat(service.queue(null, Incident.Severity.HIGH, 7L, 0, 20).items())
				.extracting(Incident::id)
				.containsExactly(raised.id());
		assertThat(service.queue(null, Incident.Severity.LOW, null, 0, 20).items()).isEmpty();
		assertThat(service.queue(null, null, 8L, 0, 20).items()).isEmpty();
	}
}
