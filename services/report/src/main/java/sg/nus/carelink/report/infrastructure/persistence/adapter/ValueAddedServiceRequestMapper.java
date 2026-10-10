package sg.nus.carelink.report.infrastructure.persistence.adapter;

import sg.nus.carelink.report.domain.model.ValueAddedServiceRequest;
import sg.nus.carelink.report.infrastructure.persistence.entity.ValueAddedServiceRequestJpaEntity;

/**
 * JPA entity <-> domain model for value_added_service_request, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by ValueAddedServiceRequestMapperTest.
 */
final class ValueAddedServiceRequestMapper {

	private ValueAddedServiceRequestMapper() {
	}

	static ValueAddedServiceRequest toDomain(ValueAddedServiceRequestJpaEntity e) {
		return new ValueAddedServiceRequest(
				e.getId(),
				e.getElderId(),
				e.getValueAddedServiceId(),
				e.getRequestedByFamilyMemberId(),
				e.getApprovingFamilyMemberId(),
				e.getVisitId(),
				e.getRequestedSchedule(),
				e.getSpecialInstructions(),
				e.getStatus() == null ? null : ValueAddedServiceRequest.Status.valueOf(e.getStatus().name()),
				e.getDecidedAt(),
				e.getCreatedAt(),
				e.getUpdatedAt());
	}

	static ValueAddedServiceRequestJpaEntity toEntity(ValueAddedServiceRequest d) {
		ValueAddedServiceRequestJpaEntity e = new ValueAddedServiceRequestJpaEntity();
		e.setId(d.id());
		e.setElderId(d.elderId());
		e.setValueAddedServiceId(d.valueAddedServiceId());
		e.setRequestedByFamilyMemberId(d.requestedByFamilyMemberId());
		e.setApprovingFamilyMemberId(d.approvingFamilyMemberId());
		e.setVisitId(d.visitId());
		e.setRequestedSchedule(d.requestedSchedule());
		e.setSpecialInstructions(d.specialInstructions());
		e.setStatus(d.status() == null ? null : ValueAddedServiceRequestJpaEntity.Status.valueOf(d.status().name()));
		e.setDecidedAt(d.decidedAt());
		return e;
	}
}
