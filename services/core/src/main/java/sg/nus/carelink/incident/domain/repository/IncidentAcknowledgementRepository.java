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

	Optional<IncidentAcknowledgement> findByIncidentIdAndFamilyMemberId(Long incidentId, Long familyMemberId);

	/** Creates the pair if absent and locks its current row until the caller's transaction ends. */
	IncidentAcknowledgement findOrCreateForUpdate(Long incidentId, Long familyMemberId);

	IncidentAcknowledgement save(IncidentAcknowledgement incidentAcknowledgement);
}
