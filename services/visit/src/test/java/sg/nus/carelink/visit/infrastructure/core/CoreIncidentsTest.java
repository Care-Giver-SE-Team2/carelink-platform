package sg.nus.carelink.visit.infrastructure.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.coreapi.CoreRuleViolation;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * What visit sends core for each incident it raises, and the key that makes a retry the same
 * request: the caregiver's request id, the visit and its due time, the visit.
 */
class CoreIncidentsTest {

	private static final LocalDateTime DUE = LocalDateTime.of(2026, 10, 12, 9, 15);
	private static final CoreApi.IncidentReport REPORTED = new CoreApi.IncidentReport(601L, 812L, "FALL", "HIGH",
			"Slipped in the bathroom", "OPEN", DUE, DUE.plusMinutes(30), null);

	private final CoreApi core = mock(CoreApi.class);
	private final CoreIncidents incidents = new CoreIncidents(core);

	@Test
	void aCaregiversReportIsKeyedByTheirOwnRequestId() {
		UUID request = UUID.fromString("7d1f4c2a-0000-4000-8000-000000000001");
		when(core.reportIncident(any())).thenReturn(REPORTED);

		var report = incidents.report(101L, 812L, 70L, "FALL", "HIGH", "Slipped in the bathroom", request);

		verify(core).reportIncident(new CoreApi.CaregiverIncidentRequest(101L, 812L, 70L, "FALL", "HIGH",
				"Slipped in the bathroom", "caregiver-report:70:" + request));
		assertThat(report.id()).isEqualTo(601L);
		assertThat(report.respondBy()).isEqualTo(DUE.plusMinutes(30));
	}

	@Test
	void coreRefusingAReportIsABusinessRuleHere() {
		when(core.reportIncident(any())).thenThrow(new CoreRuleViolation("VISIT_NOT_REPORTABLE", "Not now"));
		UUID request = UUID.randomUUID();

		assertThatThrownBy(() -> incidents.report(101L, 812L, 70L, "FALL", "HIGH", "x", request))
				.isInstanceOf(BusinessRuleViolation.class)
				.extracting("code").isEqualTo("VISIT_NOT_REPORTABLE");
	}

	@Test
	void aMissedCheckInIsKeyedByItsVisitAndDueTime() {
		when(core.raiseMissedCheckIn(any())).thenReturn(new CoreApi.IncidentRef(602L));

		assertThat(incidents.raise(101L, 812L, DUE, DUE.plusMinutes(5))).isEqualTo(602L);
		verify(core).raiseMissedCheckIn(new CoreApi.MissedCheckInRequest(101L, 812L, DUE, DUE.plusMinutes(5),
				"missed-check-in:812:2026-10-12T09:15"));
	}

	@Test
	void anEldersDisputeIsKeyedByItsVisit() {
		when(core.raiseServiceDispute(any())).thenReturn(new CoreApi.IncidentRef(603L));

		assertThat(incidents.createElderServiceDispute(101L, 812L, 15L, "Left early")).isEqualTo(603L);
		verify(core).raiseServiceDispute(new CoreApi.ServiceDisputeRequest(101L, 812L, 15L, "Left early",
				"service-dispute:812"));
	}

	@Test
	void readsAnswerWhatCoreKeeps() {
		when(core.incident(601L, 70L)).thenReturn(REPORTED);
		when(core.incident(404L, 70L)).thenThrow(new CoreNotFound("Incident [404] does not exist"));
		when(core.incidents(70L, 812L, 0, 20)).thenReturn(new CoreApi.IncidentReports(List.of(REPORTED), 0, 20, 1));
		when(core.soleMissedCheckInIncident(602L, 101L, 812L)).thenReturn(new CoreApi.SoleIncident(true));
		when(core.soleMissedCheckInIncident(604L, 101L, 812L)).thenThrow(new CoreNotFound("gone"));

		assertThat(incidents.own(70L, 601L).description()).isEqualTo("Slipped in the bathroom");
		assertThatThrownBy(() -> incidents.own(70L, 404L)).isInstanceOf(ResourceNotFound.class);
		assertThat(incidents.list(70L, 812L, 0, 20).items()).singleElement()
				.extracting(report -> report.id()).isEqualTo(601L);
		assertThat(incidents.isSoleIncident(101L, 812L, 602L)).isTrue();
		assertThat(incidents.isSoleIncident(101L, 812L, 604L)).isFalse();
	}

}
