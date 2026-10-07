package sg.nus.carelink.incident.infrastructure.persistence.adapter;

import sg.nus.carelink.incident.domain.model.Incident;
import sg.nus.carelink.incident.infrastructure.persistence.entity.IncidentJpaEntity;

/**
 * JPA entity <-> domain model for incident, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by IncidentMapperTest.
 */
final class IncidentMapper {

	private IncidentMapper() {
	}

	static Incident toDomain(IncidentJpaEntity e) {
		return new Incident(
				e.getId(),
				e.getElderId(),
				e.getVisitId(),
				e.getReportedByUserId(),
				e.getResponderUserId(),
				e.getSource() == null ? null : Incident.Source.valueOf(e.getSource().name()),
				e.getCategory() == null ? null : Incident.Category.valueOf(e.getCategory().name()),
				e.getSeverity() == null ? null : Incident.Severity.valueOf(e.getSeverity().name()),
				e.getStatus() == null ? null : Incident.Status.valueOf(e.getStatus().name()),
				e.getLatitude(),
				e.getLongitude(),
				e.getLocationText(),
				e.getDescription(),
				e.getRespondBy(),
				e.getReportedAt(),
				e.getResolvedAt());
	}

	static IncidentJpaEntity toEntity(Incident d) {
		IncidentJpaEntity e = new IncidentJpaEntity();
		e.setId(d.id());
		e.setElderId(d.elderId());
		e.setVisitId(d.visitId());
		e.setReportedByUserId(d.reportedByUserId());
		e.setResponderUserId(d.responderUserId());
		e.setSource(d.source() == null ? null : IncidentJpaEntity.Source.valueOf(d.source().name()));
		e.setCategory(d.category() == null ? null : IncidentJpaEntity.Category.valueOf(d.category().name()));
		e.setSeverity(d.severity() == null ? null : IncidentJpaEntity.Severity.valueOf(d.severity().name()));
		e.setStatus(d.status() == null ? null : IncidentJpaEntity.Status.valueOf(d.status().name()));
		e.setLatitude(d.latitude());
		e.setLongitude(d.longitude());
		e.setLocationText(d.locationText());
		e.setDescription(d.description());
		e.setRespondBy(d.respondBy());
		e.setReportedAt(d.reportedAt());
		e.setResolvedAt(d.resolvedAt());
		return e;
	}
}
