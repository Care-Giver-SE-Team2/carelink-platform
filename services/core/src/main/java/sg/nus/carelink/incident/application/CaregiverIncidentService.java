package sg.nus.carelink.incident.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;
import sg.nus.carelink.shared.error.ResourceNotFound;

@Service
@Transactional
class CaregiverIncidentService implements CaregiverIncidentGateway {
    private final IncidentService service;
    private final IncidentRepository incidents;
    CaregiverIncidentService(IncidentService service, IncidentRepository incidents) { this.service = service; this.incidents = incidents; }
    public Report report(Long elder, Long visit, Long actor, String category, String severity, String description) {
        return view(service.reportByCaregiver(elder, visit, actor, Incident.Category.valueOf(category), Incident.Severity.valueOf(severity), description));
    }
    @Transactional(readOnly = true)
    public Report own(Long actor, Long id) {
        return incidents.findById(id).filter(i -> i.source() == Incident.Source.CAREGIVER && actor.equals(i.reportedByUserId()))
                .map(CaregiverIncidentService::view).orElseThrow(() -> new ResourceNotFound("Report", id));
    }
    @Transactional(readOnly = true)
    public Reports list(Long actor, Long visit, int page, int size) {
        var rows = incidents.findCaregiverReports(actor, visit, page, size);
        return new Reports(rows.items().stream().map(CaregiverIncidentService::view).toList(), page, size, rows.totalElements());
    }
    private static Report view(Incident i) {
        return new Report(i.id(), i.visitId(), i.category().name(), i.severity().name(), i.description(), i.status().name(), i.reportedAt(), i.respondBy(), i.resolvedAt());
    }
}
