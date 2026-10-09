package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.repository.IncidentLogRepository;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentLogJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 */
@Repository
class IncidentLogRepositoryAdapter implements IncidentLogRepository {

	private final IncidentLogJpaRepository jpa;

	IncidentLogRepositoryAdapter(IncidentLogJpaRepository jpa) {
		this.jpa = jpa;
	}

	@Override
	public Optional<IncidentLog> findById(Long id) {
		return jpa.findById(id).map(IncidentLogMapper::toDomain);
	}

	@Override
	public IncidentLog save(IncidentLog incidentLog) {
		return IncidentLogMapper.toDomain(jpa.save(IncidentLogMapper.toEntity(incidentLog)));
	}

	@Override
	public List<IncidentLog> findTimeline(Long incidentId) {
		return jpa.findByIncidentIdOrderByOccurredAtAscIdAsc(incidentId).stream()
				.map(IncidentLogMapper::toDomain)
				.toList();
	}
}
