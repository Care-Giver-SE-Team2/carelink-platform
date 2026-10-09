package sg.nus.carelink.incident.domain.repository;

import java.util.List;
import java.util.Optional;

import sg.nus.carelink.incident.domain.model.IncidentLog;

/**
 * Port for incident_log: what the application layer may ask of storage, in domain terms.
 * Implemented by infrastructure.persistence.adapter.IncidentLogRepositoryAdapter. Add finders as
 * the use cases need them; identity.domain.repository.AppUserRepository is the template.
 */
public interface IncidentLogRepository {

	Optional<IncidentLog> findById(Long id);

	IncidentLog save(IncidentLog incidentLog);

	/** The whole timeline of one incident, oldest first. Never filtered: rejected attempts count. */
	List<IncidentLog> findTimeline(Long incidentId);
}
