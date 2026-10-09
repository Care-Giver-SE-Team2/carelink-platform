package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import sg.nus.carelink.incident.domain.model.IncidentAcknowledgement;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentAcknowledgementJpaEntity;

/**
 * JPA entity <-> domain model for incident_acknowledgement, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by IncidentAcknowledgementMapperTest.
 */
final class IncidentAcknowledgementMapper {

	private IncidentAcknowledgementMapper() {
	}

	static IncidentAcknowledgement toDomain(IncidentAcknowledgementJpaEntity e) {
		return new IncidentAcknowledgement(
				e.getId(),
				e.getIncidentId(),
				e.getFamilyMemberId(),
				e.getViewedAt(),
				e.getAcknowledgedAt(),
				e.getResponseNote(),
				e.getCreatedAt());
	}

	static IncidentAcknowledgementJpaEntity toEntity(IncidentAcknowledgement d) {
		IncidentAcknowledgementJpaEntity e = new IncidentAcknowledgementJpaEntity();
		e.setId(d.id());
		e.setIncidentId(d.incidentId());
		e.setFamilyMemberId(d.familyMemberId());
		e.setViewedAt(d.viewedAt());
		e.setAcknowledgedAt(d.acknowledgedAt());
		e.setResponseNote(d.responseNote());
		return e;
	}
}
