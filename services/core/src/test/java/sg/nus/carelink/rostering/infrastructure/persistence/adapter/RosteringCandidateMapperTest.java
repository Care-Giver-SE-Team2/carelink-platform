package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringCandidate;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class RosteringCandidateMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		RosteringCandidateJpaEntity entity = new RosteringCandidateJpaEntity();
		entity.setId(1L);
		entity.setRosteringRunId(2L);
		entity.setVisitId(3L);
		entity.setCaregiverId(4L);
		entity.setOptionRank(5);
		entity.setScore(new BigDecimal("6.5"));
		entity.setOutcome(RosteringCandidateJpaEntity.Outcome.SELECTED);
		entity.setExcludedByCode("v8");
		entity.setMatchReason("v9");

		RosteringCandidate domain = RosteringCandidateMapper.toDomain(entity);
		assertThat(domain.matchReason()).isEqualTo(entity.getMatchReason());
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.rosteringRunId()).isEqualTo(entity.getRosteringRunId());
		assertThat(domain.visitId()).isEqualTo(entity.getVisitId());
		assertThat(domain.caregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(domain.optionRank()).isEqualTo(entity.getOptionRank());
		assertThat(domain.score()).isEqualTo(entity.getScore());
		assertThat(domain.outcome().name()).isEqualTo(entity.getOutcome().name());
		assertThat(domain.excludedByCode()).isEqualTo(entity.getExcludedByCode());

		RosteringCandidateJpaEntity back = RosteringCandidateMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getRosteringRunId()).isEqualTo(entity.getRosteringRunId());
		assertThat(back.getVisitId()).isEqualTo(entity.getVisitId());
		assertThat(back.getCaregiverId()).isEqualTo(entity.getCaregiverId());
		assertThat(back.getOptionRank()).isEqualTo(entity.getOptionRank());
		assertThat(back.getScore()).isEqualTo(entity.getScore());
		assertThat(back.getOutcome()).isEqualTo(entity.getOutcome());
		assertThat(back.getExcludedByCode()).isEqualTo(entity.getExcludedByCode());
		assertThat(back.getMatchReason()).isEqualTo(entity.getMatchReason());
	}
}
