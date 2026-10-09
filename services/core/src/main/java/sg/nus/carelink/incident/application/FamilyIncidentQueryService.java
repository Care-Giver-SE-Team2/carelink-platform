package sg.nus.carelink.incident.application;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentAcknowledgement;
import sg.nus.carelink.incident.domain.repository.IncidentAcknowledgementRepository;
import sg.nus.carelink.incident.domain.repository.IncidentRepository;
import sg.nus.carelink.incident.domain.repository.FamilyAlertDeliveryStore;
import sg.nus.carelink.profile.application.FamilyAccessQuery;
import sg.nus.carelink.profile.application.FamilyIdentityQuery;
import sg.nus.carelink.profile.application.FamilyReadAudit;
import sg.nus.carelink.shared.error.ResourceNotFound;

/**
 * Reads an incident and only the current family's receipt under current binding access.
 *
 * @author Wang Zhili
 */
@Service
public class FamilyIncidentQueryService {

	private final IncidentRepository incidents;
	private final IncidentAcknowledgementRepository acknowledgements;
	private final FamilyIdentityQuery identity;
	private final FamilyAccessQuery access;
	private final FamilyReadAudit audit;
	private final FamilyAlertDeliveryStore deliveries;

	public FamilyIncidentQueryService(IncidentRepository incidents, IncidentAcknowledgementRepository acknowledgements,
			FamilyIdentityQuery identity, FamilyAccessQuery access, FamilyReadAudit audit, FamilyAlertDeliveryStore deliveries) {
		this.incidents = incidents;
		this.acknowledgements = acknowledgements;
		this.identity = identity;
		this.access = access;
		this.audit = audit;
		this.deliveries = deliveries;
	}

	public Detail findDetail(String username, Long incidentId) {
		return audit.read(username, FamilyReadAudit.Resource.INCIDENT_DETAIL, incidentId, "", () -> {
			Long familyMemberId = identity.requireFamilyMemberId(username);
			Incident incident = incidents.findById(incidentId)
					.orElseThrow(() -> new ResourceNotFound("Incident", incidentId));
			access.requireReadableElder(username, incident.elderId());
			IncidentAcknowledgement acknowledgement = acknowledgements
					.findByIncidentIdAndFamilyMemberId(incidentId, familyMemberId)
					.orElseGet(() -> new IncidentAcknowledgement(null, incidentId, familyMemberId, null, null, null, null));
			return new Detail(incident, acknowledgement, deliveries.acknowledgeBy(incidentId, familyMemberId).orElse(null));
		});
	}

	public record Detail(Incident incident, IncidentAcknowledgement acknowledgement, LocalDateTime acknowledgeBy) { }
}
