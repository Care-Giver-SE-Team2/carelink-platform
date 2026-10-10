package sg.nus.carelink.incident.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sg.nus.carelink.coreapi.CoreApi;
import sg.nus.carelink.incident.application.CaregiverIncidentGateway;
import sg.nus.carelink.incident.application.IncidentService;
import sg.nus.carelink.incident.application.MissedCheckInIncidentGateway;

/**
 * incident's part of core's internal API ({@link CoreApi}): the incidents a visit raises, either
 * reported by the caregiver, found by the missed check-in scan, or disputed by the elder.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalIncidentController {

	private final CaregiverIncidentGateway caregiverIncidents;

	private final MissedCheckInIncidentGateway missedCheckIns;

	private final IncidentService incidents;

	InternalIncidentController(CaregiverIncidentGateway caregiverIncidents,
			MissedCheckInIncidentGateway missedCheckIns, IncidentService incidents) {
		this.caregiverIncidents = caregiverIncidents;
		this.missedCheckIns = missedCheckIns;
		this.incidents = incidents;
	}

	@PostMapping("/incidents/caregiver-reports")
	public CoreApi.IncidentReport reportIncident(@RequestBody CoreApi.CaregiverIncidentRequest request) {
		return report(caregiverIncidents.report(request.elderId(), request.visitId(), request.actor(),
				request.category(), request.severity(), request.description()));
	}

	@GetMapping("/incidents/{incidentId}")
	public CoreApi.IncidentReport incident(@PathVariable Long incidentId, @RequestParam Long actor) {
		return report(caregiverIncidents.own(actor, incidentId));
	}

	@GetMapping("/incidents")
	public CoreApi.IncidentReports incidents(@RequestParam Long actor, @RequestParam Long visitId,
			@RequestParam int page, @RequestParam int size) {
		CaregiverIncidentGateway.Reports reports = caregiverIncidents.list(actor, visitId, page, size);
		return new CoreApi.IncidentReports(reports.items().stream().map(InternalIncidentController::report).toList(),
				reports.page(), reports.size(), reports.totalElements());
	}

	@PostMapping("/incidents/missed-check-ins")
	public CoreApi.IncidentRef raiseMissedCheckIn(@RequestBody CoreApi.MissedCheckInRequest request) {
		return new CoreApi.IncidentRef(
				missedCheckIns.raise(request.elderId(), request.visitId(), request.dueAt(), request.observedAt()));
	}

	@PostMapping("/incidents/service-disputes")
	public CoreApi.IncidentRef raiseServiceDispute(@RequestBody CoreApi.ServiceDisputeRequest request) {
		return new CoreApi.IncidentRef(incidents.createElderServiceDispute(
				request.elderId(), request.visitId(), request.reportedByUserId(), request.description()).id());
	}

	private static CoreApi.IncidentReport report(CaregiverIncidentGateway.Report report) {
		return new CoreApi.IncidentReport(report.id(), report.visitId(), report.category(), report.severity(),
				report.description(), report.status(), report.reportedAt(), report.respondBy(), report.resolvedAt());
	}

}
