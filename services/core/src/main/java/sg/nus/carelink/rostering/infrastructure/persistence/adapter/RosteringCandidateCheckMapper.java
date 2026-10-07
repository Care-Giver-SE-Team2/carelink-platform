package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateCheckJpaEntity;

/**
 * JPA entity <-> domain model for rostering_candidate_check, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by RosteringCandidateCheckMapperTest.
 */
final class RosteringCandidateCheckMapper {

	private RosteringCandidateCheckMapper() {
	}

	static RosteringCandidateCheck toDomain(RosteringCandidateCheckJpaEntity e) {
		return new RosteringCandidateCheck(
				e.getId(),
				e.getRosteringCandidateId(),
				e.getRosteringConstraintId(),
				e.getResult() == null ? null : RosteringCandidateCheck.Result.valueOf(e.getResult().name()),
				e.getDetail());
	}

	static RosteringCandidateCheckJpaEntity toEntity(RosteringCandidateCheck d) {
		RosteringCandidateCheckJpaEntity e = new RosteringCandidateCheckJpaEntity();
		e.setId(d.id());
		e.setRosteringCandidateId(d.rosteringCandidateId());
		e.setRosteringConstraintId(d.rosteringConstraintId());
		e.setResult(d.result() == null ? null : RosteringCandidateCheckJpaEntity.Result.valueOf(d.result().name()));
		e.setDetail(d.detail());
		return e;
	}
}
