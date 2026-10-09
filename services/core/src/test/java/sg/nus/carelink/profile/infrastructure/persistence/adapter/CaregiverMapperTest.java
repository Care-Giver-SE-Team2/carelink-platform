package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.Caregiver;
import sg.nus.carelink.profile.infrastructure.persistence.entity.CaregiverJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class CaregiverMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		CaregiverJpaEntity entity = new CaregiverJpaEntity();
		entity.setId(1L);
		entity.setUserId(2L);
		entity.setFullName("v3");
		entity.setPhone("v4");
		entity.setSector("v5");
		entity.setDialects("v6");
		entity.setStatus(CaregiverJpaEntity.Status.ONBOARDING);

		Caregiver domain = CaregiverMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.userId()).isEqualTo(entity.getUserId());
		assertThat(domain.fullName()).isEqualTo(entity.getFullName());
		assertThat(domain.phone()).isEqualTo(entity.getPhone());
		assertThat(domain.sector()).isEqualTo(entity.getSector());
		assertThat(domain.dialects()).isEqualTo(entity.getDialects());
		assertThat(domain.status().name()).isEqualTo(entity.getStatus().name());

		CaregiverJpaEntity back = CaregiverMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getUserId()).isEqualTo(entity.getUserId());
		assertThat(back.getFullName()).isEqualTo(entity.getFullName());
		assertThat(back.getPhone()).isEqualTo(entity.getPhone());
		assertThat(back.getSector()).isEqualTo(entity.getSector());
		assertThat(back.getDialects()).isEqualTo(entity.getDialects());
		assertThat(back.getStatus()).isEqualTo(entity.getStatus());
	}
}
