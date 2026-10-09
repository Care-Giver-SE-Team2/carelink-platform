package sg.nus.carelink.incident.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.service.EscalationPolicy;
import sg.nus.carelink.incident.support.FakeManagerDirectory;
import sg.nus.carelink.incident.support.InMemoryIncidentLogRepository;
import sg.nus.carelink.incident.support.InMemoryIncidentRepository;
import sg.nus.carelink.incident.support.IncidentFixtures;
import sg.nus.carelink.incident.support.RecordingAlert;
import sg.nus.carelink.incident.support.RecordingIncidentFamilyEvents;

/**
 * A visit on a caregiver's approved leave, due soon and never re-rostered, becomes an incident
 * with a responder and a countdown before it starts, and its timeline says why.
 */
class LeaveVisitIncidentServiceTest {

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
	void aVisitStillWithACaregiverOnLeaveIsRaisedBySystemLoggedAndRouted() {
		Incident incident = service.raiseForUnrosteredLeaveVisit(7L, 30L,
				"Personal care visit at 8 Oct 09:00 is still with Aisha, who is on approved leave");

		assertThat(incident.elderId()).isEqualTo(7L);
		assertThat(incident.visitId()).isEqualTo(30L);
		assertThat(incident.reportedByUserId()).isNull();
		assertThat(incident.source()).isEqualTo(Incident.Source.SYSTEM_MISSED_CHECKIN);
		assertThat(incident.responderUserId()).isEqualTo(IncidentFixtures.ALICE.userId());
		assertThat(incident.respondBy()).isNotNull();
		assertThat(timeline.actionsFor(incident.id())).containsSubsequence("REPORTED", "ASSIGNED");
		assertThat(timeline.findTimeline(incident.id())).extracting(IncidentLog::detail)
				.contains("visit due soon is still with a caregiver on leave");
	}
}
