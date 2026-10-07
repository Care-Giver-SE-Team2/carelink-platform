package sg.nus.carelink.incident.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;
import sg.nus.carelink.incident.support.FakeManagerDirectory;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.incident.support.InMemoryIncidentLogRepository;
import sg.nus.carelink.incident.support.InMemoryIncidentRepository;
import sg.nus.carelink.incident.support.RecordingAlert;

/**
 * UC-SYS02: the scan that turns an expired countdown into an escalation.
 *
 * <p>Time is a fixed {@link Clock} the test moves by hand, so a five-minute countdown can be
 * stepped over in a millisecond and the result is the same every run.
 */
class EscalationScanServiceTest {

	private static final LocalDateTime RAISED_AT = IncidentFixtures.RAISED_AT;

	private final InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
	private final InMemoryIncidentLogRepository timeline = new InMemoryIncidentLogRepository();
	private final RecordingAlert alert = new RecordingAlert();
	private final FakeManagerDirectory directory =
			FakeManagerDirectory.with(IncidentFixtures.ALICE, IncidentFixtures.BEN);

	@Test
	void anIncidentWhoseCountdownHasNotExpiredIsLeftAlone() {
		Incident raised = raiseAt(RAISED_AT);

		int escalated = scanAt(RAISED_AT.plusMinutes(4)).sweep();

		assertThat(escalated).isZero();
		assertThat(incidents.findById(raised.id()).orElseThrow().responderUserId())
				.isEqualTo(IncidentFixtures.ALICE.userId());
	}

	@Test
	void anExpiredCountdownMovesTheIncidentToTheNextLevel() {
		Incident raised = raiseAt(RAISED_AT);

		int escalated = scanAt(RAISED_AT.plusMinutes(6)).sweep();

		assertThat(escalated).isEqualTo(1);
		Incident after = incidents.findById(raised.id()).orElseThrow();
		assertThat(after.responderUserId()).isEqualTo(IncidentFixtures.BEN.userId());
		assertThat(timeline.actionsFor(raised.id())).contains("ESCALATED", "ASSIGNED");
	}

	@Test
	void theNewLevelGetsItsOwnDeadlineRatherThanInheritingTheOldOne() {
		Incident raised = raiseAt(RAISED_AT);

		scanAt(RAISED_AT.plusMinutes(6)).sweep();

		assertThat(incidents.findById(raised.id()).orElseThrow().respondBy())
				.isAfter(RAISED_AT.plusMinutes(6));
	}

	@Test
	void anIncidentTakenOverBeforeTheSweepRunsIsNeverSelectedAtAll() {
		Incident raised = raiseAt(RAISED_AT);
		incidents.save(incidents.findById(raised.id()).orElseThrow().claimBy(IncidentFixtures.ALICE.userId()));

		int escalated = scanAt(RAISED_AT.plusMinutes(6)).sweep();

		assertThat(escalated).isZero();
		assertThat(incidents.findById(raised.id()).orElseThrow().status())
				.isEqualTo(Incident.Status.IN_PROGRESS);
	}

	/**
	 * UC-SYS02 alternative 2a. The responder takes the incident over in the window between
	 * the sweep selecting it and the sweep reaching it, so the second read inside the
	 * per-incident transaction finds it already handled. The escalation is abandoned and the
	 * near miss is written down, because a timeline that hides an escalation that almost
	 * happened is not a record of what went on.
	 */
	@Test
	void anIncidentTakenOverDuringTheSweepCancelsItsOwnEscalationAndSaysSo() {
		Incident raised = raiseAt(RAISED_AT);
		EscalationService escalation = escalationAt(RAISED_AT.plusMinutes(6));
		incidents.save(incidents.findById(raised.id()).orElseThrow().claimBy(IncidentFixtures.ALICE.userId()));

		boolean escalated = escalation.escalateIfStillOverdue(raised.id(), RAISED_AT.plusMinutes(6));

		assertThat(escalated).isFalse();
		assertThat(incidents.findById(raised.id()).orElseThrow().status())
				.isEqualTo(Incident.Status.IN_PROGRESS);
		assertThat(timeline.actionsFor(raised.id())).contains("ESCALATION_CANCELLED");
	}

	@Test
	void escalatingTwiceRunsOutOfManagersAndPinsTheIncidentForTheFamily() {
		Incident raised = raiseAt(RAISED_AT);

		scanAt(RAISED_AT.plusMinutes(6)).sweep();
		scanAt(RAISED_AT.plusHours(2)).sweep();

		Incident after = incidents.findById(raised.id()).orElseThrow();
		assertThat(after.status()).isEqualTo(Incident.Status.UNRESOLVED_ESCALATED);
		assertThat(after.resolvedAt()).isNull();
		assertThat(timeline.actionsFor(raised.id())).contains("CHAIN_EXHAUSTED");
	}

	@Test
	void anEmptyDatabaseIsSweptWithoutComplaint() {
		assertThat(scanAt(RAISED_AT).sweep()).isZero();
	}

	@Test
	void anIncidentThatDisappearedBetweenSelectionAndEscalationIsSkipped() {
		assertThat(escalationAt(RAISED_AT).escalateIfStillOverdue(404L, RAISED_AT)).isFalse();
	}

	// -------------------------------------------------------------------- helpers ---

	private Incident raiseAt(LocalDateTime moment) {
		return serviceAt(moment).reportByCaregiver(
				7L, null, 20L, Incident.Category.SOS, Incident.Severity.HIGH, "SOS");
	}

	private IncidentService serviceAt(LocalDateTime moment) {
		Clock clock = IncidentFixtures.clockAt(moment);
		EscalationService escalation =
				new EscalationService(incidents, timeline, directory, alert, EscalationPolicy.defaults(), clock);
		return new IncidentService(incidents, timeline, escalation, clock);
	}

	private EscalationScanService scanAt(LocalDateTime moment) {
		return new EscalationScanService(incidents, escalationAt(moment), IncidentFixtures.clockAt(moment));
	}

	private EscalationService escalationAt(LocalDateTime moment) {
		return new EscalationService(
				incidents, timeline, directory, alert, EscalationPolicy.defaults(), IncidentFixtures.clockAt(moment));
	}
}
