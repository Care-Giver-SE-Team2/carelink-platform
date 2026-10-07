package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import sg.nus.carelink.incident.domain.model.IncidentLog;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentLogJpaEntity;

/**
 * JPA entity <-> domain model for incident_log, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by IncidentLogMapperTest.
 */
final class IncidentLogMapper {

	private IncidentLogMapper() {
	}

	static IncidentLog toDomain(IncidentLogJpaEntity e) {
		return new IncidentLog(
				e.getId(),
				e.getIncidentId(),
				e.getActor(),
				e.getAction(),
				e.getDetail(),
				e.getOccurredAt());
	}

	static IncidentLogJpaEntity toEntity(IncidentLog d) {
		IncidentLogJpaEntity e = new IncidentLogJpaEntity();
		e.setId(d.id());
		e.setIncidentId(d.incidentId());
		e.setActor(d.actor());
		e.setAction(d.action());
		e.setDetail(d.detail());
		e.setOccurredAt(d.occurredAt());
		return e;
	}
}
