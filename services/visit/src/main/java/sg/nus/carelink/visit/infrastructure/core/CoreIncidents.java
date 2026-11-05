package sg.nus.carelink.visit.infrastructure.core;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.coreapi.CoreNotFound;
import sg.nus.carelink.coreapi.CoreRuleViolation;
import sg.nus.carelink.shared.error.BusinessRuleViolation;
import sg.nus.carelink.shared.error.ResourceNotFound;
import sg.nus.carelink.visit.application.CaregiverIncidentGateway;
import sg.nus.carelink.visit.application.CaregiverSpotChecks;
import sg.nus.carelink.visit.application.ElderServiceDisputes;
import sg.nus.carelink.visit.application.MissedCheckInIncidentGateway;
import sg.nus.carelink.visit.application.MissedCheckInPauseEvidence;

/**
 * Everything visit asks of core's incident module, through core's internal API: a caregiver's
 * reports, the missed check-in incident and whether it alone holds a visit, an elder's
 * dispute, and the spot checks on a caregiver's own visit. core's 404 and 409 become the errors the incident module threw in process.
 *
 * <p>Each call runs in visit's transaction but commits in core on its own. When core refuses, the
 * visit's change is rolled back with it; when visit's transaction fails after core has answered,
 * the incident stays, as an incident a manager can see and close.
 */
@Component
class CoreIncidents implements CaregiverIncidentGateway, MissedCheckInIncidentGateway, MissedCheckInPauseEvidence,
		ElderServiceDisputes, CaregiverSpotChecks {

	private final CoreApi core;

	CoreIncidents(CoreApi core) {
		this.core = core;
	}

	/** The caregiver's own request id keys the report, so their retry is the same report. */
	@Override
	public Report report(Long elderId, Long visitId, Long actor, String category, String severity, String description,
			UUID clientRequestId) {
		try {
			return report(core.reportIncident(new CoreApi.CaregiverIncidentRequest(elderId, visitId, actor, category,
					severity, description, "caregiver-report:" + actor + ":" + clientRequestId)));
		}
		catch (CoreRuleViolation refused) {
			throw new BusinessRuleViolation(refused.code(), refused.getMessage());
		}
	}

	@Override
	public Report own(Long actor, Long incidentId) {
		try {
			return report(core.incident(incidentId, actor));
		}
		catch (CoreNotFound missing) {
			throw new ResourceNotFound("Report", incidentId);
		}
	}

	@Override
	public Reports list(Long actor, Long visitId, int page, int size) {
		CoreApi.IncidentReports reports = core.incidents(actor, visitId, page, size);
		return new Reports(reports.items().stream().map(CoreIncidents::report).toList(), reports.page(), reports.size(),
				reports.totalElements());
	}

	/** One missed check-in per visit and due time: a scan that tries again gets the incident already raised. */
	@Override
	public Long raise(Long elderId, Long visitId, LocalDateTime dueAt, LocalDateTime observedAt) {
		return core.raiseMissedCheckIn(new CoreApi.MissedCheckInRequest(elderId, visitId, dueAt, observedAt,
				"missed-check-in:" + visitId + ":" + dueAt)).incidentId();
	}

	@Override
	public boolean isSoleIncident(Long elderId, Long visitId, Long incidentId) {
		try {
			return core.soleMissedCheckInIncident(incidentId, elderId, visitId).sole();
		}
		catch (CoreNotFound missing) {
			return false;
		}
	}

	/** An elder answers for a visit once, so one dispute per visit. */
	@Override
	public Long createElderServiceDispute(Long elderId, Long visitId, Long reportedByUserId, String description) {
		return core.raiseServiceDispute(new CoreApi.ServiceDisputeRequest(elderId, visitId, reportedByUserId, description,
				"service-dispute:" + visitId)).incidentId();
	}

	@Override
	public List<Conclusion> forVisit(String username, Long visitId) {
		return core.caregiverSpotChecks(username, visitId).stream()
				.map(check -> new Conclusion(check.id(), check.visitId(), check.result(), check.checkedAt(),
						check.caregiverResponse()))
				.toList();
	}

	private static Report report(CoreApi.IncidentReport report) {
		return new Report(report.id(), report.visitId(), report.category(), report.severity(), report.description(),
				report.status(), report.reportedAt(), report.respondBy(), report.resolvedAt());
	}

}
