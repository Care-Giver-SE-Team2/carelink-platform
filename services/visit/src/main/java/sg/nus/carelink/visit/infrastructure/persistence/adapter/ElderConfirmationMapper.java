package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import sg.nus.carelink.visit.domain.model.ElderConfirmation;
import sg.nus.carelink.visit.infrastructure.persistence.entity.ElderConfirmationJpaEntity;

/**
 * JPA entity <-> domain model for elder_confirmation, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by ElderConfirmationMapperTest.
 */
final class ElderConfirmationMapper {

	private ElderConfirmationMapper() {
	}

	static ElderConfirmation toDomain(ElderConfirmationJpaEntity e) {
		return new ElderConfirmation(
				e.getId(),
				e.getVisitId(),
				e.getElderId(),
				e.getConfirmationStatus() == null ? null : ElderConfirmation.ConfirmationStatus.valueOf(e.getConfirmationStatus().name()),
				e.getRating(),
				e.getComment(),
				e.getConfirmedAt());
	}

	static ElderConfirmationJpaEntity toEntity(ElderConfirmation d) {
		ElderConfirmationJpaEntity e = new ElderConfirmationJpaEntity();
		e.setId(d.id());
		e.setVisitId(d.visitId());
		e.setElderId(d.elderId());
		e.setConfirmationStatus(d.confirmationStatus() == null ? null : ElderConfirmationJpaEntity.ConfirmationStatus.valueOf(d.confirmationStatus().name()));
		e.setRating(d.rating());
		e.setComment(d.comment());
		e.setConfirmedAt(d.confirmedAt());
		return e;
	}
}
