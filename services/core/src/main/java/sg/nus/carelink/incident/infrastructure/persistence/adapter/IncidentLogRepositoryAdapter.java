package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import sg.nus.carelink.events.Events;
import sg.nus.carelink.eventtypes.IncidentUpdated;
import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.domain.repository.IncidentLogRepository;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentJpaRepository;
import sg.nus.carelink.incident.infrastructure.persistence.repository.IncidentLogJpaRepository;

/**
 * Implements the domain port with Spring Data. The dependency points infrastructure ->
 * domain, never the other way round (dependency inversion, as in identity).
 *
 * <p>Every timeline entry is published as {@code IncidentUpdated} in the transaction that saves it,
 * with the incident's state as it stands then. The services save the incident before the entry
 * that records the change, so the last entry of a change carries the incident's new state.
 */
@Repository
class IncidentLogRepositoryAdapter implements IncidentLogRepository {

	private final IncidentLogJpaRepository jpa;

	private final IncidentJpaRepository incidents;

	private final Events events;

	IncidentLogRepositoryAdapter(IncidentLogJpaRepository jpa, IncidentJpaRepository incidents, Events events) {
		this.jpa = jpa;
		this.incidents = incidents;
		this.events = events;
	}

	@Override
	public Optional<IncidentLog> findById(Long id) {
		return jpa.findById(id).map(IncidentLogMapper::toDomain);
	}

	@Override
	@Transactional
	public IncidentLog save(IncidentLog incidentLog) {
		IncidentLog saved = IncidentLogMapper.toDomain(jpa.save(IncidentLogMapper.toEntity(incidentLog)));
		Incident incident = incidents.findById(saved.incidentId()).map(IncidentMapper::toDomain)
				.orElseThrow(() -> new IllegalStateException("Incident " + saved.incidentId() + " of a timeline entry"));
		events.publish(IncidentUpdated.TYPE, IncidentEventMapper.updated(saved, incident));
		return saved;
	}

	@Override
	public List<IncidentLog> findTimeline(Long incidentId) {
		return jpa.findByIncidentIdOrderByOccurredAtAscIdAsc(incidentId).stream()
				.map(IncidentLogMapper::toDomain)
				.toList();
	}
}
