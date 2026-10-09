package sg.nus.carelink.rostering.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.rostering.domain.model.RosteringConstraint;
import sg.nus.carelink.rostering.infrastructure.persistence.entity.RosteringConstraintJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class RosteringConstraintMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		RosteringConstraintJpaEntity entity = new RosteringConstraintJpaEntity();
		entity.setId(1L);
		entity.setCode("v2");
		entity.setName("v3");
		entity.setKind(RosteringConstraintJpaEntity.Kind.HARD);
		entity.setParameterValue("v5");
		entity.setEnabled(true);

		RosteringConstraint domain = RosteringConstraintMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.code()).isEqualTo(entity.getCode());
		assertThat(domain.name()).isEqualTo(entity.getName());
		assertThat(domain.kind().name()).isEqualTo(entity.getKind().name());
		assertThat(domain.parameterValue()).isEqualTo(entity.getParameterValue());
		assertThat(domain.enabled()).isEqualTo(entity.isEnabled());

		RosteringConstraintJpaEntity back = RosteringConstraintMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getCode()).isEqualTo(entity.getCode());
		assertThat(back.getName()).isEqualTo(entity.getName());
		assertThat(back.getKind()).isEqualTo(entity.getKind());
		assertThat(back.getParameterValue()).isEqualTo(entity.getParameterValue());
		assertThat(back.isEnabled()).isEqualTo(entity.isEnabled());
	}
}
