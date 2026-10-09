package sg.nus.carelink.profile.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import sg.nus.carelink.profile.domain.model.CredentialType;
import sg.nus.carelink.profile.infrastructure.persistence.entity.CredentialTypeJpaEntity;

/** Every column survives the trip entity -> domain -> entity; a swapped or dropped field fails here. */
class CredentialTypeMapperTest {

	@Test
	void mapsEveryColumnInBothDirections() {
		CredentialTypeJpaEntity entity = new CredentialTypeJpaEntity();
		entity.setId(1L);
		entity.setName("v2");

		CredentialType domain = CredentialTypeMapper.toDomain(entity);
		assertThat(domain.id()).isEqualTo(entity.getId());
		assertThat(domain.name()).isEqualTo(entity.getName());

		CredentialTypeJpaEntity back = CredentialTypeMapper.toEntity(domain);
		assertThat(back.getId()).isEqualTo(entity.getId());
		assertThat(back.getName()).isEqualTo(entity.getName());
	}
}
