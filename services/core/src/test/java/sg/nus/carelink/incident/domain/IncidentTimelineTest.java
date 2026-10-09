package sg.nus.carelink.incident.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.ContactAttempt;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.model.Playbook;
import sg.nus.carelink.incident.domain.model.Responder;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.shared.error.BusinessRuleViolation;

/** The small types the timeline is written from: log entries, contact attempts, playbooks. */
class IncidentTimelineTest {

	@Test
	void anAssignmentEntryCarriesTheResponderInAReadableWay() {
		IncidentLog entry = IncidentLog.assignment(
				1L, "system", 42L, "first responder", IncidentFixtures.RAISED_AT);

		assertThat(entry.action()).isEqualTo("ASSIGNED");
		assertThat(entry.assignedResponderId()).contains(42L);
		assertThat(entry.detail()).contains("first responder");
	}

	@Test
	void anEntryThatIsNotAnAssignmentNamesNobody() {
		IncidentLog claimed = IncidentLog.entry(
				1L, "Alice", IncidentLog.Action.CLAIMED, "taken over", IncidentFixtures.RAISED_AT);

		assertThat(claimed.assignedResponderId()).isEmpty();
	}

	@Test
	void anAssignmentEntryWithADamagedDetailDoesNotBlowUpTheTimeline() {
		IncidentLog damaged = new IncidentLog(
				1L, 1L, "system", "ASSIGNED", "responder=not-a-number", IncidentFixtures.RAISED_AT);
		IncidentLog empty = new IncidentLog(
				2L, 1L, "system", "ASSIGNED", null, IncidentFixtures.RAISED_AT);

		assertThat(damaged.assignedResponderId()).isEmpty();
		assertThat(empty.assignedResponderId()).isEmpty();
	}

	@Test
	void aVeryLongReasonIsTrimmedRatherThanRejectedByTheDatabase() {
		String tooLong = "x".repeat(900);

		IncidentLog entry = IncidentLog.systemEntry(
				1L, IncidentLog.Action.ESCALATED, tooLong, IncidentFixtures.RAISED_AT);

		assertThat(entry.detail()).hasSize(500).endsWith("...");
		assertThat(entry.actor()).isEqualTo(IncidentLog.SYSTEM_ACTOR);
	}

	@Test
	void aShortReasonIsLeftExactlyAsItWasWritten() {
		IncidentLog entry = IncidentLog.systemEntry(
				1L, IncidentLog.Action.ESCALATED, "countdown expired", IncidentFixtures.RAISED_AT);

		assertThat(entry.detail()).isEqualTo("countdown expired");
	}

	@Test
	void aFailedContactAttemptMustSayWhy() {
		assertThatThrownBy(() -> new ContactAttempt(
				ContactAttempt.Channel.PHONE, ContactAttempt.Outcome.NOT_REACHED, "  "))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting(violation -> ((BusinessRuleViolation) violation).code())
				.isEqualTo("CONTACT_REASON_REQUIRED");
	}

	@Test
	void aSuccessfulContactAttemptNeedsNoExplanation() {
		ContactAttempt reached =
				new ContactAttempt(ContactAttempt.Channel.PUSH, ContactAttempt.Outcome.REACHED, null);

		assertThat(reached.reachedTheFamily()).isTrue();
		assertThat(reached.describe()).isEqualTo("Reached via PUSH");
	}

	@Test
	void aFailedAttemptDescribesItselfWithItsReason() {
		ContactAttempt missed = new ContactAttempt(
				ContactAttempt.Channel.EMAIL, ContactAttempt.Outcome.NOT_REACHED, "mailbox full");

		assertThat(missed.reachedTheFamily()).isFalse();
		assertThat(missed.describe()).isEqualTo("Did not reach via EMAIL - mailbox full");
	}

	@Test
	void everyPlaybookIsReachableByCodeAndByCategory() {
		assertThat(Playbook.byCode("pb-sos")).contains(Playbook.SOS_IMMEDIATE);
		assertThat(Playbook.byCode("nope")).isEmpty();
		assertThat(Playbook.forCategory(Incident.Category.FALL)).contains(Playbook.FALL_CHECK);
		assertThat(Playbook.forCategory(Incident.Category.OTHER)).isEmpty();
	}

	@Test
	void aPlaybookCarriesStepsAManagerCanFollow() {
		assertThat(Playbook.MEDICAL_REVIEW.steps()).isNotEmpty();
		assertThat(Playbook.MEDICAL_REVIEW.title()).isNotBlank();
		assertThat(Playbook.SERVICE_FOLLOW_UP.category()).isEqualTo(Incident.Category.SERVICE);
	}

	@Test
	void aResponderWithoutANameIsStillIdentifiable() {
		assertThat(new Responder(5L, "  ").displayName()).isEqualTo("user 5");
		assertThat(new Responder(5L, "Ben").displayName()).isEqualTo("Ben");
	}
}
