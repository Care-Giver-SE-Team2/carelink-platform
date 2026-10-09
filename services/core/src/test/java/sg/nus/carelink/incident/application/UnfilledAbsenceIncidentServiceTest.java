package sg.nus.carelink.incident.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;
import sg.nus.carelink.incident.support.FakeManagerDirectory;
import sg.nus.carelink.incident.support.InMemoryIncidentLogRepository;
import sg.nus.carelink.incident.support.InMemoryIncidentRepository;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.incident.support.RecordingAlert;
import sg.nus.carelink.incident.support.RecordingIncidentFamilyEvents;

/**
 * UC-MG04 exception 3a: a visit an absence vacated that nobody can take becomes an incident with
 * a responder and a countdown, so the gap is somebody's job rather than a line in a list.
 */
class UnfilledAbsenceIncidentServiceTest {

	private final InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
	private final InMemoryIncidentLogRepository timeline = new InMemoryIncidentLogRepository();
	private final Clock clock = IncidentFixtures.clockAt(IncidentFixtures.RAISED_AT);

	private IncidentService service;

	@BeforeEach
	void setUp() {
		EscalationService escalation = new EscalationService(incidents, timeline,
				FakeManagerDirectory.with(IncidentFixtures.ALICE, IncidentFixtures.BEN), new RecordingAlert(), new RecordingIncidentFamilyEvents(),
				EscalationPolicy.defaults(), clock);
		service = new IncidentService(incidents, timeline, escalation, clock);
	}

	@Test
	void anUnfilledVisitIsRaisedBySystemLoggedAndRouted() {
		Incident incident = service.raiseForUnfilledAbsence(7L, 30L,
				"Personal care visit at 8 Oct 09:00: the caregiver is absent and nobody is free to replace them");

		assertThat(incident.elderId()).isEqualTo(7L);
		assertThat(incident.visitId()).isEqualTo(30L);
		assertThat(incident.reportedByUserId()).isNull();
		assertThat(incident.source()).isEqualTo(Incident.Source.SYSTEM_MISSED_CHECKIN);
		assertThat(incident.category()).isEqualTo(Incident.Category.SERVICE);
		assertThat(incident.responderUserId()).isEqualTo(IncidentFixtures.ALICE.userId());
		assertThat(incident.respondBy()).isNotNull();
		assertThat(timeline.actionsFor(incident.id())).containsSubsequence("REPORTED", "ASSIGNED");
	}
}
