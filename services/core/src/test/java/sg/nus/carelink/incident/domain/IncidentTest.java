package sg.nus.carelink.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/**
 * The transition rules of the incident aggregate.
 *
 * <p>Every test here runs without Spring, a database or a clock that has to really advance,
 * which is the whole point of keeping the rules in a plain record.
 */
class IncidentTest {

	private static final LocalDateTime NOW = IncidentFixtures.RAISED_AT;

	@Test
	void createsElderSosWithRequiredDefaults() {
		Incident incident = Incident.createElderSos(
				7L, 99L, new BigDecimal("1.3521000"), new BigDecimal("103.8198000"), "Blk 123", "fell", NOW);

		assertThat(incident.source()).isEqualTo(Incident.Source.ELDER_SOS);
		assertThat(incident.category()).isEqualTo(Incident.Category.SOS);
		assertThat(incident.severity()).isEqualTo(Incident.Severity.HIGH);
		assertThat(incident.status()).isEqualTo(Incident.Status.OPEN);
		assertThat(incident.elderId()).isEqualTo(7L);
		assertThat(incident.reportedAt()).isEqualTo(NOW);
	}

	@Test
	void createsElderSosWithoutOptionalDetails() {
		Incident incident = Incident.createElderSos(7L, null, null, null, null, null, NOW);

		assertThat(incident.reportedByUserId()).isNull();
		assertThat(incident.latitude()).isNull();
		assertThat(incident.description()).isNull();
	}

	@Test
	void rejectsElderSosWithoutElderId() {
		assertThatThrownBy(() -> Incident.createElderSos(null, 1L, null, null, null, null, NOW))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("elderId");
	}

	@Test
	void anSosIsBornWithoutAResponderOrACountdown() {
		Incident incident = IncidentFixtures.newSos();

		assertThat(incident.responderUserId()).isNull();
		assertThat(incident.respondBy()).isNull();
		assertThat(incident.awaitingTakeOver()).isTrue();
	}

	@Test
	void aCaregiverReportKeepsTheSeverityTheCaregiverChose() {
		Incident incident = Incident.reportedByCaregiver(
				7L, 4L, 20L, Incident.Category.FALL, Incident.Severity.MEDIUM, "slipped", NOW);

		assertThat(incident.source()).isEqualTo(Incident.Source.CAREGIVER);
		assertThat(incident.category()).isEqualTo(Incident.Category.FALL);
		assertThat(incident.severity()).isEqualTo(Incident.Severity.MEDIUM);
		assertThat(incident.visitId()).isEqualTo(4L);
	}

	@Test
	void aCaregiverReportWithoutAJudgementFallsBackToOtherAndMedium() {
		Incident incident = Incident.reportedByCaregiver(7L, null, 20L, null, null, null, NOW);

		assertThat(incident.category()).isEqualTo(Incident.Category.OTHER);
		assertThat(incident.severity()).isEqualTo(Incident.Severity.MEDIUM);
	}

	@Test
	void rejectsACaregiverReportWithoutAnElder() {
		assertThatThrownBy(() -> Incident.reportedByCaregiver(null, null, 1L, null, null, null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	// ------------------------------------------------------------------ assigning ---

	@Test
	void assigningNamesTheResponderAndStartsTheCountdown() {
		Incident assigned = IncidentFixtures.newSos().assignTo(11L, NOW.plusMinutes(5));

		assertThat(assigned.responderUserId()).isEqualTo(11L);
		assertThat(assigned.respondBy()).isEqualTo(NOW.plusMinutes(5));
		assertThat(assigned.status()).isEqualTo(Incident.Status.OPEN);
	}

	@Test
	void anIncidentIsOnlyOverdueOnceItsDeadlineHasPassed() {
		Incident assigned = IncidentFixtures.newSos().assignTo(11L, NOW.plusMinutes(5));

		assertThat(assigned.isOverdue(NOW.plusMinutes(4))).isFalse();
		assertThat(assigned.isOverdue(NOW.plusMinutes(5))).isTrue();
		assertThat(assigned.isOverdue(NOW.plusMinutes(6))).isTrue();
	}

	@Test
	void anIncidentWithNoDeadlineIsNeverOverdue() {
		assertThat(IncidentFixtures.newSos().isOverdue(NOW.plusYears(1))).isFalse();
	}

	// -------------------------------------------------------------------- claiming ---

	@Test
	void takingOverStopsTheCountdown() {
		Incident claimed = IncidentFixtures.newSos()
				.assignTo(11L, NOW.plusMinutes(5))
				.claimBy(12L);

		assertThat(claimed.status()).isEqualTo(Incident.Status.IN_PROGRESS);
		assertThat(claimed.responderUserId()).isEqualTo(12L);
		assertThat(claimed.respondBy()).isNull();
		assertThat(claimed.isOverdue(NOW.plusDays(1))).isFalse();
	}

	@Test
	void aSecondTakeOverIsRefusedRatherThanSilentlyOverwriting() {
		Incident claimed = IncidentFixtures.newSos().assignTo(11L, NOW.plusMinutes(5)).claimBy(11L);

		assertThatThrownBy(() -> claimed.claimBy(12L))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(violation -> ((BusinessRuleViolation) violation).code())
				.isEqualTo("INCIDENT_ALREADY_CLAIMED");
	}

	// ------------------------------------------------------------------- severity ---

	@Test
	void raisingTheSeverityKeepsTheResponderAndTheTimeline() {
		Incident assigned = IncidentFixtures.newSos().assignTo(11L, NOW.plusMinutes(5));

		Incident raised = assigned.changeSeverityTo(Incident.Severity.LOW);

		assertThat(raised.severity()).isEqualTo(Incident.Severity.LOW);
		assertThat(raised.responderUserId()).isEqualTo(11L);
		assertThat(raised.reportedAt()).isEqualTo(assigned.reportedAt());
	}

	@Test
	void changingTheSeverityToWhatItAlreadyIsIsRefused() {
		Incident incident = IncidentFixtures.newSos();

		assertThatThrownBy(() -> incident.changeSeverityTo(Incident.Severity.HIGH))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(violation -> ((BusinessRuleViolation) violation).code())
				.isEqualTo("INCIDENT_SEVERITY_UNCHANGED");
	}

	// ------------------------------------------------------------------- resolving ---

	@Test
	void resolvingRequiresSomebodyToHaveTakenItOver() {
		Incident open = IncidentFixtures.newSos().assignTo(11L, NOW.plusMinutes(5));

		assertThatThrownBy(() -> open.resolveAt(NOW))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(violation -> ((BusinessRuleViolation) violation).code())
				.isEqualTo("INCIDENT_NOT_CLAIMED");
	}

	@Test
	void resolvingRecordsTheMomentAndClosesTheIncident() {
		Incident resolved = IncidentFixtures.newSos()
				.assignTo(11L, NOW.plusMinutes(5))
				.claimBy(11L)
				.resolveAt(NOW.plusMinutes(20));

		assertThat(resolved.status()).isEqualTo(Incident.Status.RESOLVED);
		assertThat(resolved.resolvedAt()).isEqualTo(NOW.plusMinutes(20));
		assertThat(resolved.isClosed()).isTrue();
	}

	@Test
	void onlyTheResponderHandlingItCountsAsHandlingIt() {
		Incident claimed = IncidentFixtures.newSos().assignTo(11L, NOW.plusMinutes(5)).claimBy(11L);

		assertThat(claimed.isHandledBy(11L)).isTrue();
		assertThat(claimed.isHandledBy(12L)).isFalse();
		assertThat(IncidentFixtures.newSos().isHandledBy(11L)).isFalse();
	}

	// ----------------------------------------------------------------- escalation ---

	@Test
	void anExhaustedChainLeavesTheIncidentOpenAndPinned() {
		Incident unresolved = IncidentFixtures.newSos().markUnresolvedEscalated();

		assertThat(unresolved.status()).isEqualTo(Incident.Status.UNRESOLVED_ESCALATED);
		assertThat(unresolved.resolvedAt()).isNull();
		assertThat(unresolved.isClosed()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = Incident.Status.class, names = {"RESOLVED", "UNRESOLVED_ESCALATED"})
	void aClosedIncidentRefusesEveryFurtherTransition(Incident.Status closedStatus) {
		Incident closed = closedIncident(closedStatus);

		assertThatThrownBy(() -> closed.assignTo(11L, NOW)).isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> closed.claimBy(11L)).isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(() -> closed.changeSeverityTo(Incident.Severity.LOW))
				.isInstanceOf(BusinessRuleViolation.class);
		assertThatThrownBy(closed::markUnresolvedEscalated).isInstanceOf(BusinessRuleViolation.class);
	}

	@Test
	void anIncidentAlwaysKnowsWhichSourceRaisedIt() {
		assertThat(Incident.Source.values()).contains(
				Incident.Source.CAREGIVER, Incident.Source.ELDER_SOS, Incident.Source.SYSTEM_MISSED_CHECKIN);
	}

	private static Incident closedIncident(Incident.Status status) {
		Incident base = IncidentFixtures.newSos();
		return new Incident(
				1L, base.elderId(), null, base.reportedByUserId(), 11L, base.source(), base.category(),
				base.severity(), status, null, null, null, null, null, base.reportedAt(), NOW);
	}
}
