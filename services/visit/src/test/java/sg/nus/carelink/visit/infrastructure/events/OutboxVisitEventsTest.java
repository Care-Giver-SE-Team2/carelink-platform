package sg.nus.carelink.visit.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.eventtypes.ElderConfirmationSubmitted;
import sg.nus.carelink.eventtypes.VisitCancelled;
import sg.nus.carelink.eventtypes.VisitCaregiverChanged;
import sg.nus.carelink.eventtypes.VisitCheckedIn;
import sg.nus.carelink.eventtypes.VisitClosed;
import sg.nus.carelink.eventtypes.VisitCompleted;
import sg.nus.carelink.eventtypes.VisitExceptionRaised;
import sg.nus.carelink.eventtypes.VisitScheduled;
import sg.nus.carelink.eventtypes.VisitTaskRecorded;
import sg.nus.carelink.eventtypes.VitalsRecorded;
import sg.nus.carelink.events.Events;
import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.domain.model.HealthObservation;
import sg.nus.carelink.visit.domain.model.HealthRecord;
import sg.nus.carelink.visit.domain.model.Visit;
import sg.nus.carelink.visit.domain.model.VisitTask;

/**
 * Each change becomes the catalogue's event: the visit's state after it, its version, and times on
 * Singapore's clock with their offset.
 */
class OutboxVisitEventsTest {

	private static final LocalDateTime NINE = LocalDateTime.of(2026, 10, 12, 9, 0);
	private static final ZoneOffset SINGAPORE = ZoneOffset.ofHours(8);

	private final List<Object[]> published = new ArrayList<>();
	private final Events events = (type, payload) -> published.add(new Object[] { type, payload });
	/** 15:20 in Singapore */
	private final Clock clock = Clock.fixed(Instant.parse("2026-10-11T07:20:00Z"), ZoneOffset.UTC);
	private final OutboxVisitEvents outbox = new OutboxVisitEvents(events, clock);

	@Test
	void aScheduledVisitCarriesWhatItWasCreatedWith() {
		outbox.scheduled(visit(Visit.Status.SCHEDULED, 7L, null, 0), "MOVED", 811L);

		VisitScheduled event = only(VisitScheduled.TYPE, VisitScheduled.class);
		assertThat(event.visitId()).isEqualTo(812L);
		assertThat(event.caregiverId()).isEqualTo(7L);
		assertThat(event.carePlanId()).isEqualTo(21L);
		assertThat(event.carePlanNodeId()).isEqualTo(40L);
		assertThat(event.serviceType()).isEqualTo("BATHING");
		assertThat(event.scheduledStart()).isEqualTo(OffsetDateTime.of(NINE, SINGAPORE));
		assertThat(event.scheduledEnd()).isEqualTo(OffsetDateTime.of(NINE.plusHours(1), SINGAPORE));
		assertThat(event.origin()).isEqualTo("MOVED");
		assertThat(event.movedFromVisitId()).isEqualTo(811L);
		assertThat(event.version()).isZero();
	}

	@Test
	void aChangeOfHandsIsDatedWhenItIsPublished() {
		outbox.caregiverChanged(visit(Visit.Status.SCHEDULED, 9L, 33L, 1), 7L);

		VisitCaregiverChanged event = only(VisitCaregiverChanged.TYPE, VisitCaregiverChanged.class);
		assertThat(event.caregiverId()).isEqualTo(9L);
		assertThat(event.previousCaregiverId()).isEqualTo(7L);
		assertThat(event.absenceId()).isEqualTo(33L);
		assertThat(event.version()).isEqualTo(1);
		assertThat(event.changedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 11, 15, 20, 0, 0, SINGAPORE));
	}

	@Test
	void aCancelledVisitKeepsItsCaregiverAndSaysWhy() {
		outbox.cancelled(visit(Visit.Status.CANCELLED, 7L, 33L, 2), "ABSENCE", "Family skipped the visit");

		VisitCancelled event = only(VisitCancelled.TYPE, VisitCancelled.class);
		assertThat(event.caregiverId()).isEqualTo(7L);
		assertThat(event.reason()).isEqualTo("ABSENCE");
		assertThat(event.absenceId()).isEqualTo(33L);
		assertThat(event.note()).isEqualTo("Family skipped the visit");
		assertThat(event.version()).isEqualTo(2);
		assertThat(event.cancelledAt()).isEqualTo(OffsetDateTime.of(2026, 10, 11, 15, 20, 0, 0, SINGAPORE));
	}

	@Test
	void checkInAndCheckOutCarryTheirTimes() {
		Visit inProgress = visit(Visit.Status.IN_PROGRESS, 7L, null, 3, NINE.plusMinutes(3), null);
		outbox.checkedIn(inProgress, true);
		outbox.completed(visit(Visit.Status.COMPLETED, 7L, null, 4, NINE.plusMinutes(3), NINE.plusMinutes(61)));

		VisitCheckedIn checkedIn = (VisitCheckedIn) published.get(0)[1];
		assertThat(checkedIn.checkedInAt()).isEqualTo(OffsetDateTime.of(NINE.plusMinutes(3), SINGAPORE));
		assertThat(checkedIn.resumedAfterMissedCheckIn()).isTrue();
		assertThat(checkedIn.version()).isEqualTo(3);
		VisitCompleted completed = (VisitCompleted) published.get(1)[1];
		assertThat(completed.checkedOutAt()).isEqualTo(OffsetDateTime.of(NINE.plusMinutes(61), SINGAPORE));
		assertThat(completed.version()).isEqualTo(4);
		assertThat(published).extracting(entry -> entry[0]).containsExactly(VisitCheckedIn.TYPE, VisitCompleted.TYPE);
	}

	@Test
	void aClosedVisitSaysHowItClosedAndIsDatedWhenItIsPublished() {
		outbox.closed(visit(Visit.Status.VERIFIED, 7L, null, 5, NINE.plusMinutes(3), NINE.plusMinutes(61)));

		VisitClosed event = only(VisitClosed.TYPE, VisitClosed.class);
		assertThat(event.visitId()).isEqualTo(812L);
		assertThat(event.caregiverId()).isEqualTo(7L);
		assertThat(event.status()).isEqualTo("VERIFIED");
		assertThat(event.scheduledStart()).isEqualTo(OffsetDateTime.of(NINE, SINGAPORE));
		assertThat(event.version()).isEqualTo(5);
		assertThat(event.closedAt()).isEqualTo(OffsetDateTime.of(2026, 10, 11, 15, 20, 0, 0, SINGAPORE));
	}

	@Test
	void anExceptionNamesTheAbsenceOnlyWhenTheAbsenceLeftTheVisitWithNobody() {
		outbox.exceptionRaised(visit(Visit.Status.EXCEPTION, null, 33L, 5), "ABSENCE_UNCOVERED", null);
		outbox.exceptionRaised(visit(Visit.Status.EXCEPTION, 7L, 33L, 6, NINE.plusMinutes(3), null), "CAREGIVER_REPORT",
				601L);

		VisitExceptionRaised uncovered = (VisitExceptionRaised) published.get(0)[1];
		assertThat(uncovered.caregiverId()).isNull();
		assertThat(uncovered.absenceId()).isEqualTo(33L);
		assertThat(uncovered.incidentId()).isNull();
		assertThat(uncovered.checkedIn()).isFalse();
		VisitExceptionRaised reported = (VisitExceptionRaised) published.get(1)[1];
		assertThat(reported.caregiverId()).isEqualTo(7L);
		assertThat(reported.absenceId()).isNull();
		assertThat(reported.incidentId()).isEqualTo(601L);
		assertThat(reported.checkedIn()).isTrue();
		assertThat(reported.version()).isEqualTo(6);
	}

	@Test
	void aTaskResultIsFiledUnderItsVisit() {
		VisitTask done = new VisitTask(3051L, 812L, 40L, "Bathing", VisitTask.Status.DONE, null, "Enjoyed the bath",
				NINE.plusMinutes(40));
		outbox.taskRecorded(done, visit(Visit.Status.IN_PROGRESS, 7L, null, 2));

		VisitTaskRecorded event = only(VisitTaskRecorded.TYPE, VisitTaskRecorded.class);
		assertThat(event.taskId()).isEqualTo(3051L);
		assertThat(event.elderId()).isEqualTo(101L);
		assertThat(event.visitScheduledStart()).isEqualTo(OffsetDateTime.of(NINE, SINGAPORE));
		assertThat(event.status()).isEqualTo("DONE");
		assertThat(event.caregiverNote()).isEqualTo("Enjoyed the bath");
		assertThat(event.recordedAt()).isEqualTo(OffsetDateTime.of(NINE.plusMinutes(40), SINGAPORE));
	}

	@Test
	void readingsGoOutAsTheDatabaseKeepsThem() {
		HealthRecord record = new HealthRecord(77L, 812L, HealthObservation.Flag.ATTENTION, "Slightly warm",
				NINE.plusMinutes(20).plusNanos(123_000), List.of(new HealthRecord.Reading("temperature",
						new BigDecimal("37.8"), "°C"), new HealthRecord.Reading("pulse", new BigDecimal("72.345"), "bpm")));
		outbox.vitalsRecorded(record, visit(Visit.Status.IN_PROGRESS, 7L, null, 2));

		VitalsRecorded event = only(VitalsRecorded.TYPE, VitalsRecorded.class);
		assertThat(event.healthRecordId()).isEqualTo(77L);
		assertThat(event.healthFlag()).isEqualTo("ATTENTION");
		assertThat(event.healthNote()).isEqualTo("Slightly warm");
		assertThat(event.recordedAt()).isEqualTo(OffsetDateTime.of(NINE.plusMinutes(20), SINGAPORE));
		assertThat(event.readings()).containsExactly(new VitalsRecorded.Reading("temperature", "37.80", "°C", false),
				new VitalsRecorded.Reading("pulse", "72.35", "bpm", false));
	}

	@Test
	void anElderConfirmationCarriesTheDisputesIncident() {
		ElderConfirmation disputed = new ElderConfirmation(5L, 812L, 101L, ElderConfirmation.ConfirmationStatus.DISPUTED,
				(byte) 2, "Left early", NINE.plusHours(10));
		outbox.elderConfirmationSubmitted(disputed, visit(Visit.Status.COMPLETED, 7L, null, 4), 602L);

		ElderConfirmationSubmitted event = only(ElderConfirmationSubmitted.TYPE, ElderConfirmationSubmitted.class);
		assertThat(event.status()).isEqualTo("DISPUTED");
		assertThat(event.rating()).isEqualTo(2);
		assertThat(event.comment()).isEqualTo("Left early");
		assertThat(event.confirmedAt()).isEqualTo(OffsetDateTime.of(NINE.plusHours(10), SINGAPORE));
		assertThat(event.incidentId()).isEqualTo(602L);
	}

	private <T> T only(String type, Class<T> payload) {
		assertThat(published).singleElement().satisfies(entry -> assertThat(entry[0]).isEqualTo(type));
		return payload.cast(published.getFirst()[1]);
	}

	private static Visit visit(Visit.Status status, Long caregiverId, Long absenceId, int version) {
		return visit(status, caregiverId, absenceId, version, null, null);
	}

	private static Visit visit(Visit.Status status, Long caregiverId, Long absenceId, int version, LocalDateTime in,
			LocalDateTime out) {
		return new Visit(812L, 101L, caregiverId, 40L, absenceId, "BATHING", NINE, NINE.plusHours(1), in, out, status,
				null, 21L, version, null, null);
	}
}
