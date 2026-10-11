package sg.nus.carelink.visit.infrastructure.persistence.adapter;

import sg.nus.carelink.visit.domain.model.VisitStateTransition;
import sg.nus.carelink.visit.infrastructure.persistence.entity.VisitStateTransitionJpaEntity;

/**
 * JPA entity <-> domain model for visit_state_transition, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by VisitStateTransitionMapperTest.
 */
final class VisitStateTransitionMapper {

	private VisitStateTransitionMapper() {
	}

	static VisitStateTransition toDomain(VisitStateTransitionJpaEntity e) {
		return new VisitStateTransition(
				e.getId(),
				e.getVisitId(),
				e.getFromState(),
				e.getToState(),
				e.getActorUserId(),
				e.getResult() == null ? null : VisitStateTransition.Result.valueOf(e.getResult().name()),
				e.getRejectionReason(),
				e.getOccurredAt());
	}

	static VisitStateTransitionJpaEntity toEntity(VisitStateTransition d) {
		VisitStateTransitionJpaEntity e = new VisitStateTransitionJpaEntity();
		e.setId(d.id());
		e.setVisitId(d.visitId());
		e.setFromState(d.fromState());
		e.setToState(d.toState());
		e.setActorUserId(d.actorUserId());
		e.setResult(d.result() == null ? null : VisitStateTransitionJpaEntity.Result.valueOf(d.result().name()));
		e.setRejectionReason(d.rejectionReason());
		e.setOccurredAt(d.occurredAt());
		return e;
	}
}
