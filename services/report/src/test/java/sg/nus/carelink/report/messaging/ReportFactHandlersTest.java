package sg.nus.carelink.report.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.events.EventHandler;
import sg.nus.carelink.events.EventMetadata;
import sg.nus.carelink.eventtypes.ElderConfirmationSubmitted;
import sg.nus.carelink.eventtypes.IncidentRaised;
import sg.nus.carelink.eventtypes.IncidentUpdated;
import sg.nus.carelink.eventtypes.RosterChangeUpdated;
import sg.nus.carelink.eventtypes.SpotCheckUpdated;
import sg.nus.carelink.eventtypes.VisitCancelled;
import sg.nus.carelink.eventtypes.VisitCaregiverChanged;
import sg.nus.carelink.eventtypes.VisitCheckedIn;
import sg.nus.carelink.eventtypes.VisitClosed;
import sg.nus.carelink.eventtypes.VisitCompleted;
import sg.nus.carelink.eventtypes.VisitExceptionRaised;
import sg.nus.carelink.eventtypes.VisitScheduled;
import sg.nus.carelink.eventtypes.VisitTaskRecorded;
import sg.nus.carelink.eventtypes.VitalsRecorded;
import sg.nus.carelink.report.domain.repository.KeptFacts;

/** One handler per event report keeps, each handing its event, and its sequence where it is ordered by one, to KeptFacts. */
class ReportFactHandlersTest {

	private static final OffsetDateTime NINE = OffsetDateTime.parse("2026-10-12T09:00:00+08:00");

	private final KeptFacts facts = mock(KeptFacts.class);
	private final ReportFactHandlers config = new ReportFactHandlers();

	private final Map<String, EventHandler<?>> handlers = List.<EventHandler<?>>of(
			config.visitScheduledHandler(facts), config.visitCaregiverChangedHandler(facts),
			config.visitCancelledHandler(facts), config.visitCheckedInHandler(facts),
			config.visitCompletedHandler(facts), config.visitExceptionRaisedHandler(facts), config.visitClosedHandler(facts),
			config.visitTaskRecordedHandler(facts), config.vitalsRecordedHandler(facts),
			config.elderConfirmationSubmittedHandler(facts), config.incidentRaisedHandler(facts),
			config.incidentUpdatedHandler(facts), config.spotCheckUpdatedHandler(facts),
			config.rosterChangeUpdatedHandler(facts))
			.stream().collect(Collectors.toMap(EventHandler::type, Function.identity()));

	@Test
	void everyEventTheCatalogueRoutesToReportHasItsHandler() {
		assertThat(handlers.keySet()).containsExactlyInAnyOrder("VisitScheduled", "VisitCaregiverChanged",
				"VisitCancelled", "VisitCheckedIn", "VisitCompleted", "VisitExceptionRaised", "VisitClosed", "VisitTaskRecorded",
				"VitalsRecorded", "ElderConfirmationSubmitted", "IncidentRaised", "IncidentUpdated",
				"SpotCheckUpdated", "RosterChangeUpdated");
		assertThat(handlers.get("VitalsRecorded").payloadType()).isEqualTo(VitalsRecorded.class);
	}

	@Test
	void eachVisitEventIsKeptAsItCame() {
		var scheduled = new VisitScheduled(812L, 101L, 7L, 21L, 40L, "BATHING", NINE, NINE.plusHours(1), "PLAN", null, 0);
		var changed = new VisitCaregiverChanged(812L, 101L, NINE, 9L, 7L, 33L, 1, NINE);
		var cancelled = new VisitCancelled(812L, 101L, NINE, 9L, "ABSENCE", 33L, "skipped", 2, NINE);
		var checkedIn = new VisitCheckedIn(812L, 101L, NINE, 9L, NINE, false, 3);
		var completed = new VisitCompleted(812L, 101L, NINE, 9L, NINE, NINE.plusHours(1), 4);
		var exception = new VisitExceptionRaised(812L, 101L, NINE, 9L, "CAREGIVER_REPORT", null, 601L, true, 5);
		var vitals = new VitalsRecorded(77L, 812L, 101L, NINE, NINE, "ATTENTION", null, List.of());
		var answer = new ElderConfirmationSubmitted(812L, 101L, NINE, "CONFIRMED", 5, null, NINE, null);
		var closed = new VisitClosed(812L, 101L, NINE, 9L, "VERIFIED", 6, NINE);

		for (Object event : List.of(scheduled, changed, cancelled, checkedIn, completed, exception, vitals, answer, closed)) {
			handle(event, 900L);
		}

		verify(facts).keep(scheduled);
		verify(facts).keep(changed);
		verify(facts).keep(cancelled);
		verify(facts).keep(checkedIn);
		verify(facts).keep(completed);
		verify(facts).keep(exception);
		verify(facts).keep(closed);
		verify(facts).keep(vitals);
		verify(facts).keep(answer);
	}

	@Test
	void eventsOrderedBySequenceAreKeptWithIt() {
		var task = new VisitTaskRecorded(3051L, 812L, 101L, NINE, "Bathing", "DONE", null, null, NINE);
		var spotCheck = new SpotCheckUpdated(41L, 101L, 812L, 9L, NINE, "APPROVED", null, null, null, null, null, NINE);
		var rosterChange = new RosterChangeUpdated(55L, 812L, 101L, 33L, NINE, 7L, "RESOLVED", "REPLACED", "FAMILY", 9L,
				NINE);

		handle(task, 901L);
		handle(spotCheck, 902L);
		handle(rosterChange, null);

		verify(facts).keep(task, 901L);
		verify(facts).keep(spotCheck, 902L);
		// A message from no outbox counts as older than anything kept
		verify(facts).keep(rosterChange, 0L);
	}

	@Test
	void incidentsAreKeptByTheirOwnOrder() {
		var raised = new IncidentRaised(601L, 101L, 812L, "CAREGIVER", "FALL", "HIGH", "OPEN", "Slipped", NINE);
		var updated = new IncidentUpdated(601L, 101L, 9001L, "CLAIMED", "lee", null, NINE, "IN_PROGRESS", "HIGH", null);

		handle(raised, 903L);
		handle(updated, 904L);

		verify(facts).keep(raised);
		verify(facts).keep(updated);
	}

	@SuppressWarnings("unchecked")
	private void handle(Object event, Long sequence) {
		var handler = (EventHandler<Object>) handlers.get(typeOf(event));
		handler.handle(event, new EventMetadata("id-" + sequence, handler.type(), "visit", Instant.EPOCH, sequence));
	}

	private static String typeOf(Object event) {
		return event.getClass().getSimpleName();
	}

}
