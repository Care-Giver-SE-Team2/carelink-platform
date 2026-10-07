package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateJpaEntity;

/**
 * JPA entity <-> domain model for rostering_candidate, both directions, column by column. Database-managed
 * columns (created_at, updated_at) are read but never written back. Covered by RosteringCandidateMapperTest.
 */
final class RosteringCandidateMapper {

	private RosteringCandidateMapper() {
	}

	static RosteringCandidate toDomain(RosteringCandidateJpaEntity e) {
		return new RosteringCandidate(
				e.getId(),
				e.getRosteringRunId(),
				e.getVisitId(),
				e.getCaregiverId(),
				e.getOptionRank(),
				e.getScore(),
				e.getOutcome() == null ? null : RosteringCandidate.Outcome.valueOf(e.getOutcome().name()),
				e.getExcludedByCode(),
				e.getMatchReason());
	}

	static RosteringCandidateJpaEntity toEntity(RosteringCandidate d) {
		RosteringCandidateJpaEntity e = new RosteringCandidateJpaEntity();
		e.setId(d.id());
		e.setRosteringRunId(d.rosteringRunId());
		e.setVisitId(d.visitId());
		e.setCaregiverId(d.caregiverId());
		e.setOptionRank(d.optionRank());
		e.setScore(d.score());
		e.setOutcome(d.outcome() == null ? null : RosteringCandidateJpaEntity.Outcome.valueOf(d.outcome().name()));
		e.setExcludedByCode(d.excludedByCode());
		e.setMatchReason(d.matchReason());
		return e;
	}
}
