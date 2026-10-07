package sg.nus.carelink.incident.domain.repository;

import java.util.Optional;

import sg.nus.carelink.incident.domain.model.IncidentAcknowledgement;

/**
 * Port for incident_acknowledgement: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.IncidentAcknowledgementRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface IncidentAcknowledgementRepository {

	Optional<IncidentAcknowledgement> findById(Long id);

	IncidentAcknowledgement save(IncidentAcknowledgement incidentAcknowledgement);
}
