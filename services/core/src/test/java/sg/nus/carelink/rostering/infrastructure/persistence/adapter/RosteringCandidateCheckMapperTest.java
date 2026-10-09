package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringCandidateCheck;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringCandidateCheckJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class RosteringCandidateCheckMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		RosteringCandidateCheckJpaEntity entity = new RosteringCandidateCheckJpaEntity();
		entity.setId(1L);
		entity.setRosteringCandidateId(2L);
		entity.setRosteringConstraintId(3L);
		entity.setResult(RosteringCandidateCheckJpaEntity.Result.PASS);
		entity.setDetail("v5");

		RosteringCandidateCheck domain = RosteringCandidateCheckMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.rosteringCandidateId()).isEqualTo(entity.getRosteringCandidateId());
		assertThat(domain.rosteringConstraintId()).isEqualTo(entity.getRosteringConstraintId());
		assertThat(domain.result().name()).isEqualTo(entity.getResult().name());
		assertThat(domain.detail()).isEqualTo(entity.getDetail());

		RosteringCandidateCheckJpaEntity back = RosteringCandidateCheckMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getRosteringCandidateId()).isEqualTo(entity.getRosteringCandidateId());
		assertThat(back.getRosteringConstraintId()).isEqualTo(entity.getRosteringConstraintId());
		assertThat(back.getResult()).isEqualTo(entity.getResult());
		assertThat(back.getDetail()).isEqualTo(entity.getDetail());
	}
}
